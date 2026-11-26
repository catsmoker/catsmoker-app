package com.catsmoker.app.screens.gaming.components

import androidx.annotation.StringRes
import com.catsmoker.app.R

@StringRes
fun gamingOperationLabel(result: String): Int = when (result) {
    "accepted" -> R.string.gt_status_received
    "awaiting_user" -> R.string.gt_automation_status_awaiting
    "running" -> R.string.gt_status_running
    "applied", "already_applied" -> R.string.gt_status_prepared
    "restored", "nothing_to_restore" -> R.string.gt_status_restored
    "completed" -> R.string.gt_status_completed
    "byte_budget" -> R.string.gt_status_budget
    "time_budget" -> R.string.gt_status_time
    "memory_pressure" -> R.string.gt_status_pressure
    "cancelled" -> R.string.gt_status_cancelled
    "interrupted" -> R.string.gt_status_interrupted
    "partial" -> R.string.gt_status_partial
    "busy" -> R.string.gt_status_busy
    "restore_required" -> R.string.gt_status_restore
    "foreground_required" -> R.string.gt_status_foreground
    "game_unavailable" -> R.string.gt_automation_status_game_unavailable
    "failed", "denied", "no_progress", "launch_failed" -> R.string.gt_status_failed
    "disabled" -> R.string.gt_status_disabled
    "selected" -> R.string.gt_status_selected
    "launched", "launch_ready" -> R.string.gt_status_launch
    else -> R.string.gt_status_idle
}
