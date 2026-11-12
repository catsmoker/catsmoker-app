package com.catsmoker.app.features.gamingtools.tools.booster

import com.catsmoker.app.R

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.catsmoker.app.platform.notifications.CatsmokerNotifications
import com.catsmoker.app.screens.gaming.compilation.AppBoosterService
import com.catsmoker.app.screens.gaming.compilation.BoosterOutcome
import com.catsmoker.app.screens.gaming.compilation.BoosterState
import com.catsmoker.app.screens.gaming.compilation.DexoptScheduleStore
import com.catsmoker.app.screens.gaming.compilation.DexoptSweepStopReceiver
import com.catsmoker.app.screens.gaming.mode.GamingEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The recurring ART dexopt sweep: the same engine run the App Booster button starts
 * ([GamingEngine.runArtOptimization]), driven on a schedule instead of a tap.
 *
 * Scheduled maintenance is deferrable ordinary WorkManager work, never a specialUse
 * foreground service. A normal progress notification retains the Stop action when
 * notification permission allows it. WorkManager may defer or cancel a run; the engine
 * handles coroutine cancellation and destroys its compile process. No uninterrupted
 * execution or exact interval is promised. An unavailable privileged shell is a skipped
 * run, reported separately because the engine creates no history for a run that never began.
 */
@HiltWorker
class DexoptSweepWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val gamingEngine: GamingEngine,
    private val scheduleStore: DexoptScheduleStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // The user switched the schedule off while this run was already queued. Running anyway
        // would be the opposite of listening, and it is not a failure of anything.
        if (!scheduleStore.isEnabled()) return Result.success()

        // A manual sweep (or an earlier scheduled one) already holds the engine. Starting would
        // only log "already running" and leave the other sweep's state untouched — the engine's
        // own guard, checked here so the notification never claims a second sweep.
        if (gamingEngine.boosterState.value.isRunning) return Result.success()

        ensureChannel()
        CatsmokerNotifications.attachOwner(applicationContext, "DexoptSweep")

        // The reference's own pattern: the notification follows the engine's real counts, which
        // only advance when a command actually ran and answered. A child of doWork's scope
        // (not a standalone CoroutineScope): a WorkManager-initiated stop cancels doWork,
        // which now takes the progress collection down with it.
        return coroutineScope {
            val progressJob = launch {
                gamingEngine.boosterState.collect { state ->
                    runCatching {
                        applicationContext.getSystemService(NotificationManager::class.java)
                            ?.notify(NOTIFICATION_ID, buildNotification(state))
                    }
                }
            }

            try {
                // Never forced: the schedule exists to catch apps the phone has not compiled yet,
                // which `cmd package compile` skips on its own when they are already done.
                gamingEngine.runArtOptimization(mode = DexoptScheduleStore.SCHEDULED_MODE, force = false)

                val outcome = gamingEngine.boosterState.value.outcome
                if (outcome is BoosterOutcome.Unavailable) {
                    // Unavailable means the sweep never began (no privilege at wake time, no
                    // eligible apps) and the engine records no history entry for it by design — so
                    // without this notification a skipped run would leave no trace at all.
                    postSkippedNotification(outcome.reason)
                }
                // Completed / Cancelled / Failed are all recorded by the engine in its own history,
                // which the App Booster card shows; the worker has nothing to add on top.
                Result.success()
            } finally {
                progressJob.cancel()
                applicationContext.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
                CatsmokerNotifications.detachOwner(applicationContext, "DexoptSweep")
            }
        }
    }

    /**
     * No onStopped override, unlike [AppBoosterService.onDestroy]: CoroutineWorker makes it final
     * here. A WorkManager-initiated stop cancels this coroutine instead, and the engine's own
     * CancellationException handling already covers that — it marks the run cancelled and its
     * finally destroys the in-flight compile process. The user-facing Stop action never goes
     * through WorkManager at all: it routes through [DexoptSweepStopReceiver] to
     * [GamingEngine.cancelArtOptimization], so the recurring schedule survives the stop.
     */

    /** Mirrors [AppBoosterService]'s notification, with the scheduled sweep's own title. */
    private fun buildNotification(state: BoosterState): Notification {
        val stop = PendingIntent.getBroadcast(
            applicationContext,
            REQUEST_STOP,
            Intent(applicationContext, DexoptSweepStopReceiver::class.java).apply {
                action = DexoptSweepStopReceiver.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(applicationContext, AppBoosterService.CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.booster_scheduled_title))
            .setContentText(contentText(state))
            .setSmallIcon(R.drawable.ic_stat_name)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setGroup(CatsmokerNotifications.GROUP_KEY)
            .addAction(R.drawable.ic_action_name, applicationContext.getString(R.string.booster_notification_stop), stop)

        val progress = state.progress
        if (progress == null) {
            // The package list has not been queried yet, so there is no percentage to show.
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, (progress * 100).roundToInt().coerceIn(0, 100), false)
        }
        return builder.build()
    }

    private fun contentText(state: BoosterState): String = when {
        state.currentPackage != null -> applicationContext.getString(
            R.string.booster_notification_compiling,
            state.processedCount + 1,
            state.totalCount,
            state.currentPackage
        )
        state.totalCount > 0 -> applicationContext.getString(
            R.string.booster_notification_progress, state.processedCount, state.totalCount
        )
        else -> applicationContext.getString(R.string.booster_notification_preparing)
    }

    /**
     * The one surface a skipped run gets. Auto-cancel, no Stop action — there is nothing running
     * to stop, and the text is the engine's own reason for standing aside.
     */
    private fun postSkippedNotification(reason: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        val text = applicationContext.getString(R.string.booster_scheduled_skipped, reason)
        val notification = NotificationCompat.Builder(applicationContext, AppBoosterService.CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.booster_scheduled_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_name)
            .setAutoCancel(true)
            .setGroup(CatsmokerNotifications.GROUP_KEY)
            .build()
        manager.notify(SKIPPED_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        // The service creates this channel too; createNotificationChannel is idempotent, so both
        // paths are safe whichever runs first.
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        CatsmokerNotifications.ensureGroup(manager)
        manager.createNotificationChannel(
            CatsmokerNotifications.channel(AppBoosterService.CHANNEL_ID, applicationContext.getString(R.string.gt_svc_booster_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        /** Addresses the schedule as one unit — cancelling by tag would leave it half-alive. */
        const val UNIQUE_WORK_NAME = "dexopt_scheduled_sweep"

        // Distinct from the manual sweep's 101, so both notifications can be up at once. The
        // skipped notice must also avoid the overlay's 104: IDs are per-app, and a collision
        // would let a skipped-sweep notice replace the overlay's ongoing notification.
        private const val NOTIFICATION_ID = 103
        private const val SKIPPED_NOTIFICATION_ID = 105
        private const val REQUEST_STOP = 1
    }
}
