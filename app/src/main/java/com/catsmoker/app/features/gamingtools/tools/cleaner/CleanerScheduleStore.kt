package com.catsmoker.app.features.gamingtools.tools.cleaner

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The recurring junk sweep's persisted settings: whether it is on, and how far apart runs are.
 *
 * Its own prefs file (`cleaner_schedule_prefs`) — the house rule is one file per feature, and
 * none of the cleaner stores has anything to do with scheduling. Off by default: an
 * unattended sweep deletes files, so it starts only on explicit opt-in. The worker re-reads
 * these on every run rather than receiving them in input data, so an interval change made
 * after a run was queued applies without re-enqueueing.
 */
@Singleton
class CleanerScheduleStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("cleaner_schedule_prefs", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    /** Hours between scheduled runs. Clamped to [INTERVAL_CHOICES] on write. */
    fun getIntervalHours(): Int = prefs.getInt(KEY_INTERVAL_HOURS, DEFAULT_INTERVAL_HOURS)

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun setIntervalHours(hours: Int) {
        prefs.edit { putInt(KEY_INTERVAL_HOURS, INTERVAL_CHOICES.firstOrNull { it >= hours } ?: INTERVAL_CHOICES.last()) }
    }

    companion object {
        const val DEFAULT_INTERVAL_HOURS = 72

        /**
         * What the UI offers. Junk accumulates slower than dexopt staleness, so the shortest
         * is a day and the default three — a daily unattended delete is a second dose of risk
         * for buckets that refill slowly.
         */
        val INTERVAL_CHOICES = listOf(24, 72, 168)

        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL_HOURS = "interval_hours"
    }
}
