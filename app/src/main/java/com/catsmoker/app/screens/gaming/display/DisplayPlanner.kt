package com.catsmoker.app.screens.gaming.display

import com.catsmoker.app.platform.display.AttachedDisplay
import com.catsmoker.app.platform.display.DisplayMode
import com.catsmoker.app.platform.display.DisplayRateMath
import com.catsmoker.app.platform.display.FramePacing

/**
 * What the engine intends to do for one request, decided before anything is written.
 *
 * The plan is a value, not an action: [DisplayPlanner] answers "which mechanism, which mode,
 * and is that honest" without touching the device, so the same reasoning can be unit-tested
 * and shown to the user before a risky change is made.
 */
sealed interface DisplayPlan {

    /**
     * Change the rate through the composer's policy — the only route that can hold a display
     * on a mode it is not currently choosing.
     */
    data class Composer(
        val displayId: Long,
        val mode: DisplayMode,
        val pacing: FramePacing,
        /** True when this switches the display's mode, which can blank it. */
        val switchesMode: Boolean
    ) : DisplayPlan

    /** Change the rate through the AOSP settings rows. Cannot switch a mode. */
    data class Settings(val rateHz: Float, val pacing: FramePacing?) : DisplayPlan

    /**
     * Samsung DeX: lift the external-desktop timing filter, then pace applications on the
     * monitor. The two are one plan because lifting the filter alone changes nothing the user
     * can see — the composer still has to be told which display paces.
     */
    data class DeX(val monitorId: Long, val rateHz: Float?, val pacing: FramePacing?) : DisplayPlan

    /** The request cannot be honoured here, for [reason]. */
    data class Refused(val reason: String) : DisplayPlan
}

/**
 * Turns a requested rate into a plan, or refuses.
 *
 * The two refusals that matter: a rate the display does not enumerate is never approximated
 * silently, and an external display is never asked for a rate above the highest mode *it*
 * reports on the connection it is actually using — a monitor's EDID and the cable's bandwidth
 * decide that, not a rate the app once saw on another setup.
 */
object DisplayPlanner {

    /**
     * @param target the display to change.
     * @param rateHz the rate the user asked for.
     * @param probe what the privileged backend can do here.
     * @param canWriteSettings whether the settings rows are writable (shell or WRITE_SETTINGS).
     * @param isDeXCapable true when this is a Samsung device whose DeX switch is present.
     */
    fun plan(
        target: AttachedDisplay,
        rateHz: Float,
        probe: DisplayProbe,
        canWriteSettings: Boolean,
        isDeXCapable: Boolean
    ): DisplayPlan {
        if (rateHz <= 0f) return DisplayPlan.Refused("No rate was requested")

        val enumerated = DisplayRateMath.nearestRate(target.rates, rateHz)
            ?: return DisplayPlan.Refused(
                "This display does not offer ${formatHz(rateHz)} Hz on the current connection"
            )

        val panelHz = target.currentHz ?: enumerated
        val pacing = DisplayRateMath.pacing(panelHz, enumerated)

        if (target.isExternal) {
            val peak = DisplayRateMath.peakRateAtActiveResolution(target)
            if (peak != null && enumerated > peak + DisplayRateMath.HZ_TOLERANCE) {
                return DisplayPlan.Refused(
                    "The monitor reports ${formatHz(peak)} Hz as its fastest mode on this connection"
                )
            }
            if (isDeXCapable && probe.dexHighResolution) {
                return DisplayPlan.DeX(
                    monitorId = target.id.toLong(),
                    rateHz = enumerated,
                    pacing = pacing
                )
            }
        }

        if (probe.modeSpecs) {
            val active = target.activeMode
            val mode = DisplayRateMath.modeAtRate(
                modes = target.modes,
                rateHz = enumerated,
                atWidth = active?.width,
                atHeight = active?.height
            ) ?: return DisplayPlan.Refused("No mode on this display runs at ${formatHz(enumerated)} Hz")
            return DisplayPlan.Composer(
                displayId = target.id.toLong(),
                mode = mode,
                pacing = pacing,
                switchesMode = active == null || active.id != mode.id
            )
        }

        if (canWriteSettings) {
            return DisplayPlan.Settings(rateHz = enumerated, pacing = pacing)
        }

        return DisplayPlan.Refused("Root or Shizuku, or the write-settings grant, is required")
    }

    /**
     * Formats a rate without inventing precision.
     *
     * Panels report 59.94 and 119.88 as readily as 120, and printing `119.88` next to a `120`
     * the display also lists would read as two different rates that are not.
     */
    fun formatHz(rateHz: Float): String {
        val rounded = Math.round(rateHz * 100f) / 100f
        return if (Math.abs(rounded - Math.round(rounded)) < 0.05f) {
            Math.round(rounded).toString()
        } else {
            // Keep the real fraction a panel reports (119.88, 59.94); it is not noise.
            rounded.toString()
        }
    }
}
