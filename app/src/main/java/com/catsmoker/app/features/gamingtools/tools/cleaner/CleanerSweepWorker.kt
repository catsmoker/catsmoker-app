package com.catsmoker.app.features.gamingtools.tools.cleaner

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.catsmoker.app.R
import com.catsmoker.app.shared.util.CatsmokerNotifications
import com.catsmoker.app.shared.util.formatBytes
import com.catsmoker.app.system.shell.ShellRunner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The unattended junk sweep: scan, then delete only the non-aggressive buckets.
 *
 * Aggressive buckets (error reports, corpses of removed apps) are NEVER touched here — each
 * has a visible cost the user accepted per-tap in the manual flow (a log wanted for a bug
 * report, data belonging to something installed under an unresolvable name), and an
 * unattended run cannot ask. They stay manual-only by construction, not by flag.
 *
 * Foreground with a progress notification while running (same specialUse shape as the dexopt
 * worker), then a result notification with the measured figures — or a skipped notice when
 * the run never began (schedule off, nothing readable), so a silent schedule leaves a trace.
 * On Android 12+ a refused `setForeground` degrades to ordinary background work rather than
 * crashing, mirroring the dexopt worker.
 */
@HiltWorker
class CleanerSweepWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val shellRunner: ShellRunner,
    private val patternStore: CleanerPatternStore,
    private val scheduleStore: CleanerScheduleStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // The user switched the schedule off while this run was already queued. Running anyway
        // would be the opposite of listening, and it is not a failure of anything.
        if (!scheduleStore.isEnabled()) return Result.success()

        ensureChannel()
        val foreground = try {
            setForeground(foregroundInfo(null))
            CatsmokerNotifications.attachOwner(applicationContext, "CleanerSweep")
            true
        } catch (_: Exception) {
            false
        }

        try {
            val report = CleaningFeature.scan(
                applicationContext,
                shellRunner,
                patternStore.getKeepEntries(),
                patternStore.getCleanPatterns()
            )
            if (!report.scannedAnything) {
                postResultNotification(
                    applicationContext.getString(R.string.cleaner_scheduled_title),
                    applicationContext.getString(R.string.cleaner_scheduled_unreadable)
                )
                return Result.success()
            }
            // Non-aggressive buckets only (see the class KDoc): logs and corpses stay manual.
            val safe = report.results.filter { !it.category.isAggressive }
            if (safe.isEmpty()) {
                postResultNotification(
                    applicationContext.getString(R.string.cleaner_scheduled_title),
                    applicationContext.getString(R.string.cleaner_scheduled_nothing_safe)
                )
                return Result.success()
            }
            if (foreground) runCatching { setForeground(foregroundInfo(safe.sumOf { it.sizeBytes })) }
            val result = CleaningFeature.clean(shellRunner, safe)
            postResultNotification(
                applicationContext.getString(R.string.cleaner_scheduled_title),
                applicationContext.getString(
                    R.string.cleaner_scheduled_done,
                    formatBytes(result.freedBytes),
                    result.deletedItems
                )
            )
            CatsmokerNotifications.detachOwner(applicationContext, "CleanerSweep")
            return Result.success()
        } finally {
            if (foreground) runCatching { CatsmokerNotifications.detachOwner(applicationContext, "CleanerSweep") }
        }
    }

    private fun foregroundInfo(foundBytes: Long?): ForegroundInfo {
        val text = if (foundBytes == null) {
            applicationContext.getString(R.string.cleaner_scheduled_scanning)
        } else {
            applicationContext.getString(R.string.cleaner_scheduled_cleaning, formatBytes(foundBytes))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.cleaner_scheduled_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setGroup(CatsmokerNotifications.GROUP_KEY)
            .build()
        return foregroundInfo(notification)
    }

    private fun foregroundInfo(notification: Notification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /** The one surface a finished or skipped run gets. Auto-cancel — nothing is running anymore. */
    private fun postResultNotification(title: String, text: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_name)
            .setAutoCancel(true)
            .setGroup(CatsmokerNotifications.GROUP_KEY)
            .build()
        manager.notify(RESULT_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        CatsmokerNotifications.ensureGroup(manager)
        manager.createNotificationChannel(
            CatsmokerNotifications.channel(
                CHANNEL_ID,
                applicationContext.getString(R.string.cleaner_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "cleaner_scheduled_sweep"
        const val CHANNEL_ID = "catsmoker_cleaner_channel"

        // 108/109: distinct from the manual/dexopt/overlay IDs so no notice replaces another.
        private const val NOTIFICATION_ID = 108
        private const val RESULT_NOTIFICATION_ID = 109
    }
}
