package com.catsmoker.app.features.gamingtools.tools.cleaner

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the recurring junk sweep's WorkManager enrollment, mirroring [DexoptSweepScheduler]:
 * [ExistingPeriodicWorkPolicy.UPDATE] so re-applying the schedule retunes in place instead of
 * restarting the countdown, and [Constraints] requiring battery-not-low — a storage walk plus
 * deletions is real I/O, not something to run on the last percent.
 *
 * WorkManager persists the periodic work across reboots itself, so no boot receiver is needed.
 */
@Singleton
class CleanerSweepScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Applies the store's current enabled/interval — enable, disable, or retune. */
    fun apply(store: CleanerScheduleStore) {
        val workManager = WorkManager.getInstance(context)
        if (!store.isEnabled()) {
            workManager.cancelUniqueWork(CleanerSweepWorker.UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<CleanerSweepWorker>(
            store.getIntervalHours().toLong(), TimeUnit.HOURS
        )
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            CleanerSweepWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** When WorkManager currently expects the next run, or null when nothing is scheduled. */
    suspend fun nextScheduledRunAt(): Long? = withContext(Dispatchers.IO) {
        try {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(CleanerSweepWorker.UNIQUE_WORK_NAME)
                .get()
                .asSequence()
                .mapNotNull { if (!it.state.isFinished) it.nextScheduleTimeMillis else null }
                .minOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
