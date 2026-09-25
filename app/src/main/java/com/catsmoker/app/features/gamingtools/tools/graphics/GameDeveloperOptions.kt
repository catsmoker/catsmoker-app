package com.catsmoker.app.features.gamingtools.tools.graphics

import android.content.Context
import android.os.Build
import com.catsmoker.app.R
import com.catsmoker.app.features.gamingtools.engine.RefreshRateController
import com.catsmoker.app.system.shell.ShellRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two gaming switches Android's own Developer Options screen exposes that this
 * app can actually drive.
 *
 * Each one goes through the mechanism the platform screen itself uses — no app-local imitation, and
 * no key invented to make a switch look real. Every write is confirmed by reading the value back
 * from the system, and a control the device does not support reports *why* rather than moving and
 * doing nothing.
 *
 * A third platform switch, "Show refresh rate" (SurfaceFlinger's debug transaction `1034`),
 * is deliberately NOT offered here: from Android 14 it is gated on the signature permission
 * `ACCESS_SURFACE_FLINGER`, which the `shell` caller Shizuku uses does not hold and cannot be
 * granted — so on every supported no-root configuration the switch could never move anything.
 * Offering it would be pretending at a control we do not have. Users who want the overlay turn
 * it on in Android's own Developer options screen (linked below the switches), which runs as
 * `system` and is always accepted.
 *
 * - **Force peak refresh rate** — writes `min_refresh_rate` as a float, set to the panel's peak
 *   rate, as `ForcePeakRefreshRatePreferenceController` does. Raising the *minimum* is what forces
 *   the panel up: `DisplayModeDirector` votes the ceiling as `max(min, peak)`, so the minimum wins.
 * - **Disable default frame rate for games** — Android holds this in the system property
 *   `debug.graphics.game_default_frame_rate.disabled`, and the cap it lifts comes from the
 *   read-only `ro.surface_flinger.game_default_frame_rate_override`.
 */
@Singleton
class GameDeveloperOptions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRunner: ShellRunner,
    private val refreshController: RefreshRateController
) {
    /**
     * One switch's real condition.
     *
     * @param enabled what the system reports, or null when it would not answer at all. Null must be
     *   shown as unknown — never as "off", which would be a reading the device did not give.
     * @param available whether the control can do anything on this device.
     * @param unavailableReason the concrete reason when [available] is false.
     * @param detail a device fact worth showing next to the switch (the peak rate, the frame cap).
     * @param openDeveloperOptions true when the switch cannot work here but the user can set the same
     *   thing themselves in Android's own Developer options screen. The row then offers a button that
     *   goes there instead of only saying no.
     */
    data class ToggleState(
        val enabled: Boolean? = null,
        val available: Boolean = false,
        val unavailableReason: String? = null,
        val detail: String? = null,
        val openDeveloperOptions: Boolean = false
    )

    /** Both switches, as last read. */
    data class State(
        val forcePeakRefreshRate: ToggleState = ToggleState(),
        val gameDefaultFrameRateDisabled: ToggleState = ToggleState()
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun refresh() {
        _state.value = State(
            forcePeakRefreshRate = readForcePeakRefreshRate(),
            gameDefaultFrameRateDisabled = readGameDefaultFrameRate()
        )
    }

    // ---------------------------------------------------------- Force peak refresh rate

    private fun readForcePeakRefreshRate(): ToggleState {
        // Single source of truth (F3): the shared RefreshRateController owns min_refresh_rate.
        // This row only reads through it — Gaming Mode's lock and this switch share one backend,
        // so the two can no longer overwrite each other silently.
        val peak = refreshController.peakHz()
        if (peak <= DEFAULT_REFRESH_RATE) {
            return ToggleState(
                available = false,
                unavailableReason = context.getString(R.string.gt_gdo_single_speed),
                detail = context.getString(R.string.gt_gdo_runs_at, formatHz(peak))
            )
        }
        // An unset key means "no minimum": Settings.System.getFloat(cr, key, 0f) — which is how the
        // platform controller reads it — returns 0 both when the key is absent and when it holds
        // something unparseable, so falling back to 0 here is the device's own answer, not a guess.
        val current = refreshController.readMinHz() ?: NO_CONFIG
        val canWrite = refreshController.canWrite()
        return ToggleState(
            // AOSP treats "min is at the peak" as on. Compared with a tolerance because a provider
            // may hold 120 for a written 120.0, and both mean the same rate.
            enabled = RefreshRateController.isAtPeak(current, peak),
            available = canWrite,
            unavailableReason = if (canWrite) {
                null
            } else {
                context.getString(R.string.gt_gdo_need_sys)
            },
            detail = if (current > 0f && current < peak - HZ_TOLERANCE) {
                context.getString(R.string.gt_gdo_peak_full, formatHz(peak), formatHz(current))
            } else {
                context.getString(R.string.gt_gdo_peak_upto, formatHz(peak))
            }
        )
    }

    /**
     * Raises or clears the minimum refresh rate via the shared controller.
     *
     * The stash lives in the controller (with legacy migration), so turning it off
     * puts the user's own value back rather than blanket-writing the platform's 0.
     * Always targets the panel peak — maximum refresh, never a cap.
     *
     * @return the state read back afterwards.
     */
    suspend fun setForcePeakRefreshRate(enabled: Boolean): ToggleState {
        refreshController.setForcePeak(enabled)
        val result = readForcePeakRefreshRate()
        _state.value = _state.value.copy(forcePeakRefreshRate = result)
        return result
    }

    // ------------------------------------------- Disable default frame rate for games

    /**
     * Reads the game default frame rate switch.
     *
     * The property is the state Android itself keeps; there is no `Settings` key for this. The cap
     * being lifted is a read-only build property, so it is shown as a fact rather than a control.
     */
    private suspend fun readGameDefaultFrameRate(): ToggleState = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return@withContext ToggleState(
                available = false,
                unavailableReason = context.getString(R.string.gt_gdo_old15, Build.VERSION.RELEASE)
            )
        }
        val cap = getProp(PROP_GAME_FRAME_RATE_OVERRIDE)?.toIntOrNull()
        val raw = getProp(PROP_GAME_FRAME_RATE_DISABLED)
        ToggleState(
            // SystemProperties.getBoolean, which the Developer Options controller uses to read this,
            // counts exactly these tokens as true and everything else — including a property that is
            // not set — as false. Null here means the read itself failed, which is not a reading.
            enabled = raw?.let { it in TRUTHY_PROPERTY_VALUES },
            available = shellRunner.hasPrivilege(),
            unavailableReason = if (shellRunner.hasPrivilege()) {
                null
            } else {
                context.getString(R.string.gt_needs_root_shizuku_change)
            },
            detail = if (cap != null) {
                context.getString(R.string.gt_gdo_cap, cap)
            } else {
                context.getString(R.string.gt_gdo_cap_unknown)
            }
        )
    }

    /**
     * Sets the game default frame rate property.
     *
     * Developer Options routes this through `IGameManagerService.toggleGameDefaultFrameRate`, which
     * writes this same property and then notifies SurfaceFlinger in the same step. That binder
     * interface is not reachable from an app, so this writes the property directly and verifies it;
     * the notification part is what the caller is told about in the UI.
     *
     * @return the state read back afterwards.
     */
    suspend fun setGameDefaultFrameRateDisabled(disabled: Boolean): ToggleState {
        withContext(Dispatchers.IO) {
            shellRunner.execSafeResult(
                "setprop", PROP_GAME_FRAME_RATE_DISABLED, if (disabled) "true" else "false"
            )
        }
        val result = readGameDefaultFrameRate()
        _state.value = _state.value.copy(gameDefaultFrameRateDisabled = result)
        return result
    }

    // ------------------------------------------------------------------------ plumbing

    /**
     * @return the property's value, "" when it is not set, or null when it could not be read at all.
     *
     * An unset property makes `getprop` print an empty line and still succeed, so blank is a real
     * answer — "not set" — and must not be confused with a read that failed.
     */
    private suspend fun getProp(name: String): String? {
        val result = shellRunner.execSafeResult("getprop", name)
        return if (result.isSuccess) result.stdout.trim() else null
    }

    companion object {
        private const val PROP_GAME_FRAME_RATE_DISABLED =
            "debug.graphics.game_default_frame_rate.disabled"
        private const val PROP_GAME_FRAME_RATE_OVERRIDE =
            "ro.surface_flinger.game_default_frame_rate_override"

        /** Below this there is nothing to force; the platform uses the same threshold. */
        private const val DEFAULT_REFRESH_RATE = 60f

        /** The platform's "no minimum" value for `min_refresh_rate`. */
        private const val NO_CONFIG = 0f

        /** Rates differing by less than this are the same rate, stored differently. */
        private const val HZ_TOLERANCE = 0.5f

        /** The values `SystemProperties.getBoolean` reads as true. */
        private val TRUTHY_PROPERTY_VALUES = setOf("1", "y", "yes", "on", "true")

        private fun formatHz(hz: Float): String =
            if (hz % 1f == 0f) "${hz.toInt()} Hz" else String.format(Locale.US, "%.1f Hz", hz)
    }
}
