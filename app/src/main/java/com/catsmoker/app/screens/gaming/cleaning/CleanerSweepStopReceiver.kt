package com.catsmoker.app.screens.gaming.cleaning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import java.util.concurrent.ExecutionException
import javax.inject.Inject

/**
 * Stops the current cleaner run without disabling its recurring schedule.
 *
 * Cancelling a periodic WorkManager request also cancels future runs, so after the current
 * execution is cancelled the enabled schedule is enrolled again using its current settings.
 */
@AndroidEntryPoint
class CleanerSweepStopReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduleStore: CleanerScheduleStore

    @Inject
    lateinit var scheduler: CleanerSweepScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return

        val workIdText = intent.getStringExtra(EXTRA_WORK_ID)
        if (workIdText == null) {
            Log.w(TAG, "Ignoring cleaner stop action without a work ID")
            return
        }
        val workId = try {
            UUID.fromString(workIdText)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Ignoring cleaner stop action with an invalid work ID", error)
            return
        }

        val pendingResult = goAsync()
        val cancellation = WorkManager.getInstance(context).cancelWorkById(workId)
        cancellation.result.addListener(
            {
                try {
                    cancellation.result.get()
                    if (scheduleStore.isEnabled()) {
                        scheduler.apply(scheduleStore)
                    }
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    Log.e(TAG, "Could not stop the cleaner sweep", error)
                } catch (error: ExecutionException) {
                    Log.e(TAG, "Could not stop the cleaner sweep", error.cause ?: error)
                } finally {
                    pendingResult.finish()
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    companion object {
        const val ACTION_STOP = "com.catsmoker.app.action.STOP_CLEANER_SWEEP"
        const val EXTRA_WORK_ID = "com.catsmoker.app.extra.CLEANER_WORK_ID"

        private const val TAG = "CleanerSweepStop"
    }
}
