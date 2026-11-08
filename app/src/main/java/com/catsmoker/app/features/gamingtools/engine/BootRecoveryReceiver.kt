package com.catsmoker.app.features.gamingtools.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootRecoveryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("gaming_engine_prefs", Context.MODE_PRIVATE)
        if (!BootRecoveryPolicy.hasRecoveryRecords(
                prefs.getStringSet("affected_pkgs", emptySet()),
                prefs.getStringSet("appops_prior", emptySet())
            )
        ) {
            return
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            BootRecoveryWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BootRecoveryWorker>().build()
        )
    }
}

internal object BootRecoveryPolicy {
    fun hasRecoveryRecords(affectedPackages: Set<String>?, recordedAppOps: Set<String>?): Boolean =
        !affectedPackages.isNullOrEmpty() || !recordedAppOps.isNullOrEmpty()
}
