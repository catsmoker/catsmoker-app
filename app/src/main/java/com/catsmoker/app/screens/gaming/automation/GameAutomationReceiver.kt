package com.catsmoker.app.screens.gaming.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.catsmoker.app.R
import com.catsmoker.app.screens.gaming.mode.GamingModeService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Android, not a guessed exemption, decides whether this request may start a foreground service. */
@AndroidEntryPoint
class GameAutomationReceiver : BroadcastReceiver() {
    @Inject lateinit var automation: GameAutomation
    override fun onReceive(context: Context, intent: Intent) {
        val request = automation.parse(intent)
        val result = when {
            request == null -> GameAutomation.response("denied")
            request.isQuery -> automation.query(request.requestId)
            else -> automation.rejectBroadcast(request)?.let { code ->
                AutomationStatus.withRequest(GameAutomation.response(code), request)
            } ?: handle(context, intent, request)
        }
        if (isOrderedBroadcast) {
            resultCode = if (request == null) 0 else -1
            resultData = result
        }
    }

    private fun handle(context: Context, intent: Intent, request: AutomationProtocol.Request): String {
        if (request.operation == AutomationProtocol.Operation.SELECT_GAME) return automation.select(request)
        if (request.game != null && !automation.gameAvailable(request.game)) {
            return automation.recordDelivery(request, "game_unavailable")
        }
        val token = intent.getStringExtra("token") ?: return GameAutomation.response("denied")
        val result = when (request.operation) {
            AutomationProtocol.Operation.LAUNCH_GAME,
            AutomationProtocol.Operation.APPLY_AND_LAUNCH -> requestForeground(context, request, token)
            else -> {
                // Rebuild a new explicit intent; don't forward caller flags, categories or metadata.
                val service = Intent(context, GamingModeService::class.java).fillRequest(request, token)
                try {
                    ContextCompat.startForegroundService(context, service)
                    if (request.isRestore) cancelContinuation(context)
                    "accepted"
                } catch (_: IllegalStateException) {
                    requestForeground(context, request, token)
                } catch (_: SecurityException) {
                    requestForeground(context, request, token)
                }
            }
        }
        return automation.recordDelivery(request, result)
    }

    private fun requestForeground(context: Context, request: AutomationProtocol.Request, token: String): String {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return "foreground_required"
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.gt_automation), NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE)
            return "foreground_required"
        val launch = Intent(context, GameAutomationActivity::class.java).fillRequest(request, token)
        val pending = PendingIntent.getActivity(context, CONTINUATION_ID, launch,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
        return try {
            notifications.notify(CONTINUATION_ID, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(context.getString(R.string.gt_automation))
                .setContentText(context.getString(R.string.gt_automation_continue))
                .setContentIntent(pending).setAutoCancel(true).build())
            "awaiting_user"
        } catch (_: SecurityException) {
            pending.cancel()
            "foreground_required"
        }
    }

    private fun Intent.fillRequest(request: AutomationProtocol.Request, token: String): Intent = apply {
        action = AutomationProtocol.ACTION
        putExtra("token", token)
        putExtra("operation", request.operation.name)
        request.game?.let { putExtra("game", it) }
        request.requestId?.let { putExtra("request_id", it) }
    }

    companion object {
        private const val CHANNEL = "game_automation"
        private const val CONTINUATION_ID = 7431

        internal fun cancelContinuation(context: Context) {
            val continuation = Intent(context, GameAutomationActivity::class.java).setAction(AutomationProtocol.ACTION)
            PendingIntent.getActivity(context, CONTINUATION_ID, continuation,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)?.cancel()
            context.getSystemService(NotificationManager::class.java).cancel(CONTINUATION_ID)
        }
    }
}
