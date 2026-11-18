package com.catsmoker.app.screens.gaming.mode

import com.catsmoker.app.R

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.catsmoker.app.platform.notifications.CatsmokerNotifications
import com.catsmoker.app.system.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.catsmoker.app.screens.gaming.automation.AutomationProtocol
import com.catsmoker.app.screens.gaming.automation.GameAutomation

@AndroidEntryPoint
class GamingModeService : Service() {

    @Inject
    lateinit var engineProvider: dagger.Lazy<GamingEngine>
    private val gamingEngine: GamingEngine get() = engineProvider.get()
    @Inject lateinit var automation: dagger.Lazy<GameAutomation>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var requests = 0
    private var latestStartId = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = buildNotification()

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: IllegalStateException) {
            automation.get().record("foreground_required")
            stopSelf()
            return
        } catch (_: SecurityException) {
            automation.get().record("foreground_required")
            stopSelf()
            return
        }
        CatsmokerNotifications.attachOwner(this, "GamingMode")
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) return START_NOT_STICKY
        latestStartId = startId
        if (intent?.action == AutomationProtocol.ACTION) {
            // Non-exported service; nevertheless re-check consent/token after queued delivery.
            if (automation.get().parse(intent) == null) {
                if (requests == 0 && !gamingEngine.sessionOpen.value) stopSelfResult(startId)
            } else {
                requests++
                serviceScope.launch {
                    try { automation.get().execute(intent).await() } finally {
                        requests--
                        if (requests == 0 && !gamingEngine.sessionOpen.value) stopSelfResult(latestStartId)
                    }
                }
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PREPARE) {
            val pkg = intent.getStringExtra("game")
            if (pkg != null && !automation.get().gameAvailable(pkg)) {
                if (requests == 0 && !gamingEngine.sessionOpen.value) stopSelfResult(startId)
            } else {
                requests++
                serviceScope.launch {
                    try { automation.get().prepareFromUi(pkg).await() } finally {
                        requests--
                        if (requests == 0 && !gamingEngine.sessionOpen.value) stopSelfResult(latestStartId)
                    }
                }
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            // Reverts the system settings before releasing the service, exactly as the in-app switch
            // (deactivateGamingMode) and onTaskRemoved do. This path used to call stopSelf() alone,
            // which ended the service while leaving animation scales, DND, and the rest of the
            // snapshot applied — a stop that did not undo anything it was stopping.
            serviceScope.launch {
                automation.get().restoreFromUi().await()
                if (!gamingEngine.sessionOpen.value) stopSelf()
            }
            return START_NOT_STICKY
        }
        // The foreground notification covers preparation as well as the active session.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        CatsmokerNotifications.detachOwner(this, "GamingMode")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Ensure system settings are restored if the app is swiped from recents
        serviceScope.launch {
            automation.get().restoreFromUi().await()
            if (!gamingEngine.sessionOpen.value) stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        CatsmokerNotifications.ensureGroup(nm)
        val channel = CatsmokerNotifications.channel(CHANNEL_ID, getString(R.string.gt_svc_gm_channel), NotificationManager.IMPORTANCE_LOW)
        channel.description = getString(R.string.gt_svc_gm_channel_desc)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // The same ACTION_STOP the service already handled, now reachable from the shade. It restores
        // the snapshot before stopping, so this button is equivalent to turning the switch off in-app.
        // getForegroundService, not getService: from API 26 a service started from a notification
        // action must call startForeground, which onCreate does on entry.
        val stop = PendingIntent.getForegroundService(
            this,
            REQUEST_STOP,
            Intent(this, GamingModeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.gt_svc_gm_title))
            .setContentText(getString(R.string.gt_svc_gm_text))
            .setSmallIcon(R.drawable.ic_stat_name)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setGroup(CatsmokerNotifications.GROUP_KEY)
            .setContentIntent(pi)
            .addAction(R.drawable.ic_action_name, getString(R.string.notification_stop), stop)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "gaming_mode_channel"
        const val NOTIFICATION_ID = 102
        const val ACTION_STOP = "com.catsmoker.app.ACTION_STOP_GAMING_MODE"
        const val ACTION_PREPARE = "com.catsmoker.app.PREPARE_SELECTED_GAME"
        @Volatile var isRunning: Boolean = false
            private set
        private const val REQUEST_STOP = 0
    }
}
