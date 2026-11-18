package com.catsmoker.app.screens.gaming.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.catsmoker.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.core.content.ContextCompat
import com.catsmoker.app.screens.gaming.mode.GamingModeService

/** Android, not a guessed exemption, decides whether this request may start a foreground service. */
@AndroidEntryPoint
class GameAutomationReceiver : BroadcastReceiver() {
    @Inject lateinit var automation: GameAutomation
    override fun onReceive(context: Context, intent: Intent) {
        val request = automation.parse(intent)
        val result = if (request == null) GameAutomation.response("denied") else when (request.operation) {
            AutomationProtocol.Operation.QUERY_SELECTION,
            AutomationProtocol.Operation.QUERY_STATUS -> automation.query()
            AutomationProtocol.Operation.SELECT_GAME -> automation.select(request)
            AutomationProtocol.Operation.LAUNCH_GAME,
            AutomationProtocol.Operation.APPLY_AND_LAUNCH -> requestForeground(context, intent)
            else -> {
                try {
                    ContextCompat.startForegroundService(context,
                        Intent(intent).setClass(context, GamingModeService::class.java))
                    GameAutomation.response("accepted")
                } catch (_: IllegalStateException) {
                    requestForeground(context, intent)
                } catch (_: SecurityException) {
                    requestForeground(context, intent)
                }
            }
        }
        if (isOrderedBroadcast) {
            resultCode = if (request == null) 0 else -1
            resultData = result
        }
    }

    private fun requestForeground(context: Context, request: Intent): String {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return automation.record("foreground_required")
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.gt_automation), NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE)
            return automation.record("foreground_required")
        val launch = Intent(context, GameAutomationActivity::class.java).apply {
            action = AutomationProtocol.ACTION
            AutomationProtocol.keys.forEach { key -> request.getStringExtra(key)?.let { putExtra(key, it) } }
        }
        val pending = PendingIntent.getActivity(context, 7431, launch,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return try {
            notifications.notify(7431, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(context.getString(R.string.gt_automation))
                .setContentText(context.getString(R.string.gt_automation_continue))
                .setContentIntent(pending).setAutoCancel(true).build())
            automation.record("awaiting_user")
        } catch (_: SecurityException) {
            automation.record("foreground_required")
        }
    }

    companion object { private const val CHANNEL = "game_automation" }
}
