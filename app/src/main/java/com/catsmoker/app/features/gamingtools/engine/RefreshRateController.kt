package com.catsmoker.app.features.gamingtools.engine

import android.content.Context
import android.provider.Settings
import com.catsmoker.app.system.shell.ShellRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for refresh-rate control (consolidation F3).
 *
 * Gaming Mode's refresh lock and Developer Options' "Always fastest" switch both
 * wrote `system:min_refresh_rate` independently — different formats (int vs float),
 * different stashes, different revert paths — so enabling both overwrote each other
 * and a snapshot could capture the forced value as the "original".
 *
 * Both now delegate here. The objective is always the panel's measured maximum:
 * this controller never lowers the ceiling; it only pins the minimum to the peak
 * (which forces the panel up) or restores a previous value.
 */
@Singleton
class RefreshRateController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRunner: ShellRunner,
    private val refreshRates: DisplayRefreshRateProvider
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The panel's measured peak in Hz — the only target this controller ever writes. */
    fun peakHz(): Float = refreshRates.getMaxHardwareRefreshRate()

    fun peakHzInt(): Int = peakHz().toInt()

    /** Current `min_refresh_rate` via the provider (no privilege needed), or null when unset. */
    fun readMinHz(): Float? = try {
        Settings.System.getString(context.contentResolver, KEY_MIN_REFRESH_RATE)?.toFloatOrNull()
    } catch (_: Exception) {
        null
    }

    /** Whether the panel is currently pinned to its peak (within storage tolerance). */
    fun isForcePeakActive(): Boolean {
        val peak = peakHz()
        if (peak <= SINGLE_SPEED_HZ) return false
        return isAtPeak(readMinHz() ?: NO_CONFIG, peak)
    }

    /** Whether this device can write system settings (privilege or WRITE_SETTINGS grant). */
    fun canWrite(): Boolean =
        shellRunner.hasPrivilege() || Settings.System.canWrite(context)

    /**
     * Pins peak + min to the panel maximum (the Gaming Mode lock).
     *
     * @return which keys verified after write-back.
     */
    suspend fun lockToPeak(): LockResult {
        val maxHz = peakHzInt()
        val peakOk = putVerified(KEY_PEAK_REFRESH_RATE, maxHz.toString())
        val minOk = putVerified(KEY_MIN_REFRESH_RATE, maxHz.toString())
        return LockResult(peakOk = peakOk, minOk = minOk, lockedHz = maxHz)
    }

    /**
     * Raises the minimum to the peak (the "Always fastest" switch).
     *
     * Stashes the previous value first (unless already forced, which would record
     * our own value), mirroring the platform controller's own semantics.
     */
    suspend fun setForcePeak(enabled: Boolean): Boolean {
        val peak = peakHz()
        if (enabled) {
            if (isForcePeakActive()) return true
            val previous = readMinHz()
            prefs.edit().putString(PREF_PREVIOUS_MIN_HZ, previous?.toString() ?: "").apply()
            return writeFloat(KEY_MIN_REFRESH_RATE, peak)
        } else {
            val stashed = prefs.getString(PREF_PREVIOUS_MIN_HZ, null)
                ?: legacyStash()
            val previous = stashed?.toFloatOrNull()
            val ok = when {
                previous != null -> writeFloat(KEY_MIN_REFRESH_RATE, previous)
                stashed != null -> deleteMin()
                else -> writeFloat(KEY_MIN_REFRESH_RATE, NO_CONFIG)
            }
            prefs.edit().remove(PREF_PREVIOUS_MIN_HZ).apply()
            return ok
        }
    }

    data class LockResult(val peakOk: Boolean, val minOk: Boolean, val lockedHz: Int) {
        val locked: Boolean get() = peakOk || minOk
    }

    // ------------------------------------------------------------------ plumbing

    private suspend fun putVerified(key: String, value: String): Boolean {
        shellRunner.execSafeResult("settings", "put", "system", key, value)
        val readBack = readRaw(key) ?: return false
        val actual = readBack.toFloatOrNull()
        val wanted = value.toFloatOrNull()
        return if (actual != null && wanted != null) actual == wanted else readBack == value
    }

    private suspend fun writeFloat(key: String, value: Float): Boolean = withContext(Dispatchers.IO) {
        val formatted = String.format(Locale.US, "%.2f", value)
        if (shellRunner.hasPrivilege()) {
            shellRunner.execSafeResult("settings", "put", "system", key, formatted)
        }
        val landed = readMinHz()?.let { abs(it - value) < HZ_TOLERANCE } == true
        if (!landed && Settings.System.canWrite(context)) {
            runCatching { Settings.System.putFloat(context.contentResolver, key, value) }
        }
        readMinHz()?.let { abs(it - value) < HZ_TOLERANCE } == true
    }

    private suspend fun deleteMin(): Boolean = withContext(Dispatchers.IO) {
        shellRunner.execSafeResult("settings", "delete", "system", KEY_MIN_REFRESH_RATE)
        // Deleted reads back as unset (null) — the platform's own "no minimum".
        readRaw(KEY_MIN_REFRESH_RATE) == null
    }

    private fun readRaw(key: String): String? {
        try {
            Settings.System.getString(context.contentResolver, key)?.let { return it }
        } catch (_: Exception) { }
        val result = runCatching {
            // Blocking read inside IO callers only; kept synchronous to preserve
            // the verified-write contract without restructuring callers.
            kotlinx.coroutines.runBlocking {
                shellRunner.execSafeResult("settings", "get", "system", key)
            }
        }.getOrNull() ?: return null
        if (!result.isSuccess) return null
        val text = result.stdout.trim()
        return text.ifEmpty { null }
    }

    /**
     * Picks up a stash written by GameDeveloperOptions before the migration, so
     * users who forced peak on the old build restore their own value, not 0.
     */
    private fun legacyStash(): String? = try {
        val legacy = context.getSharedPreferences(
            "game_developer_options", Context.MODE_PRIVATE
        ).getString("previous_min_refresh_rate", null)
        if (legacy != null) {
            context.getSharedPreferences("game_developer_options", Context.MODE_PRIVATE)
                .edit().remove("previous_min_refresh_rate").apply()
        }
        legacy
    } catch (_: Exception) {
        null
    }

    companion object {
        const val KEY_MIN_REFRESH_RATE = "min_refresh_rate"
        const val KEY_PEAK_REFRESH_RATE = "peak_refresh_rate"

        /** Below this there is nothing to force; matches the platform threshold. */
        const val SINGLE_SPEED_HZ = 60f

        /** The platform's "no minimum" value. */
        const val NO_CONFIG = 0f

        /** Rates differing by less than this are the same rate, stored differently. */
        const val HZ_TOLERANCE = 0.5f

        private const val PREFS_NAME = "refresh_rate_controller"
        private const val PREF_PREVIOUS_MIN_HZ = "previous_min_hz"

        /** Pure predicate — the unit-testable seam. */
        fun isAtPeak(currentHz: Float, peakHz: Float): Boolean =
            currentHz >= peakHz - HZ_TOLERANCE
    }
}
