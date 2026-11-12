package com.catsmoker.app.features.gamingtools.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.catsmoker.app.screens.gaming.mode.BootRecoveryPolicy
import com.catsmoker.app.screens.gaming.mode.GamingEngine
import com.catsmoker.app.screens.gaming.mode.GamingModeState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class BootRecoveryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val gamingEngine: GamingEngine
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!hasRecoveryRecords(prefs)) return Result.success()

        gamingEngine.state.first { it != GamingModeState.Checking }

        // Startup reconciliation may have confirmed that nothing is left to restore.
        if (!hasRecoveryRecords(prefs)) return Result.success()

        val result = gamingEngine.restoreSuspendedApps()
        if (result.refusedWhileActive || !result.isComplete) {
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
        return Result.success()
    }

    private fun hasRecoveryRecords(prefs: SharedPreferences): Boolean =
        BootRecoveryPolicy.hasRecoveryRecords(
            prefs.getStringSet("affected_pkgs", emptySet()),
            prefs.getStringSet("appops_prior", emptySet())
        )

    companion object {
        const val UNIQUE_WORK_NAME = "gaming_mode_boot_recovery"
        private const val PREFS_NAME = "gaming_engine_prefs"
        private const val MAX_ATTEMPTS = 5
    }
}
