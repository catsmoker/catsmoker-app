package com.catsmoker.app.features.gamingtools.engine

import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.catsmoker.app.R
import com.catsmoker.app.shared.util.CatsmokerNotifications
import com.catsmoker.app.shared.util.ForegroundWatcher
import com.catsmoker.app.system.MainActivity
import com.catsmoker.app.system.shell.ShellRunner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject

/**
 * Watches the foreground app and starts Gaming Mode when a library game comes up, stopping
 * it when the game is left — the auto session.
 *
 * The shape mirrors [com.catsmoker.app.features.gamingtools.tools.forcestop.AutoForceStopService]:
 * same usage-events poll, same per-cycle privilege and usage-access checks named in the
 * notification, same single stop path. Two deliberate differences: the poll runs every 3 s
 * instead of 2 (game switches are slower than app switches, and the cheaper cadence is free
 * battery), and this service never touches other apps — it only drives the engine.
 *
 * Ownership rule: this service starts Gaming Mode only when the engine is Idle, and stops it
 * only while the package it recorded is the one the engine is running. A manual activation —
 * the engine busy with no recorded session — is left alone entirely, so leaving the game later
 * cannot revert the user's own mode. Gaming Mode also runs its own foreground service while
 * active, so a manual session shows two notifications by design (this one watching, that one
 * holding settings); an auto session shows this one, and the in-app engine keeps it alive
 * through the same process.
 */
@AndroidEntryPoint
class GameSessionService : Service() {

    @Inject
    lateinit var shellRunner: ShellRunner

    @Inject
    lateinit var gamingEngine: GamingEngine

    @Inject
    lateinit var sessionManager: GameSessionManager

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private var pollingJob: Job? = null

    /** Last text pushed to the notification, so an unchanged status is not re-posted. */
    private var lastStatus: String? = null

    companion object {
        private const val CHANNEL_ID = "game_session_channel"
        private const val NOTIF_ID = 106
        private const val POLL_INTERVAL_MS = 3000L
        private const val REQUEST_STOP = 0

        /** Delivered by the notification's Stop button and by [stop]; stopSelf() answers it. */
        const val ACTION_STOP = "com.catsmoker.app.action.STOP_GAME_SESSION"

        /** Sent when the service ends from any path, so the switch reflects the service. */
        const val ACTION_SERVICE_STOPPED = "com.catsmoker.app.action.GAME_SESSION_STOPPED"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GameSessionService::class.java))
        }

        fun stop(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GameSessionService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(getString(R.string.gt_svc_starting)))
        CatsmokerNotifications.attachOwner(this, "GameSession")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (pollingJob?.isActive != true) {
            pollingJob = scope.launch { pollLoop() }
        }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        var lastEventTime = System.currentTimeMillis() - POLL_INTERVAL_MS

        while (true) {
            delay(POLL_INTERVAL_MS.milliseconds)

            if (!sessionManager.isEnabled()) {
                postStatus(getString(R.string.gt_svc_session_off))
                continue
            }
            if (!hasUsageAccess()) {
                postStatus(getString(R.string.gt_svc_session_no_usage))
                continue
            }
            if (!shellRunner.hasPrivilege()) {
                postStatus(getString(R.string.gt_svc_session_no_priv))
                continue
            }

            val now = System.currentTimeMillis()
            val current = queryLatestForegroundPackage(usageStatsManager, lastEventTime, now)
            lastEventTime = now

            // Whose session the engine is running decides what this tick may do. A session this
            // monitor started keeps the engine busy on purpose; the old "any non-idle engine is a
            // manual one" rule released the monitor's own record on the very next tick, so the
            // game it had just started for was re-labelled manual, logged as "watch released", and
            // never stopped when the game was left. Ownership is the recorded package, not the
            // engine being busy.
            when (
                GameSessionDecider.ownership(
                    engineBusy = gamingEngine.state.value !is GamingModeState.Idle,
                    recordedPkg = sessionManager.sessionActivePkg.value
                )
            ) {
                GameSessionDecider.Ownership.Manual -> {
                    postStatus(getString(R.string.gt_svc_session_manual))
                    continue
                }
                // The user turned the session off by hand; the record outlived the engine. Left
                // in place, the next manual activation would look like the monitor's own and be
                // reverted when its game is left.
                GameSessionDecider.Ownership.Stale -> sessionManager.releaseByUser()
                GameSessionDecider.Ownership.Self -> Unit
            }

            // With the stale record gone, "library game in the foreground, nothing recorded" is
            // the state that starts a session — so the game the user just switched the mode off
            // for would be switched back on every 3 s. The veto holds until they leave it.
            if (sessionManager.isUserStopped(current)) {
                postStatus(getString(R.string.gt_svc_session_watching))
                continue
            }

            when (val action = sessionManager.onForeground(current)) {
                is GameSessionDecider.Action.Start -> {
                    gamingEngine.toggleGamingMode(true, action.pkg)
                    postStatus(getString(R.string.gt_svc_session_active, action.pkg))
                }
                is GameSessionDecider.Action.Stop -> {
                    gamingEngine.toggleGamingMode(false)
                    postStatus(getString(R.string.gt_svc_session_watching))
                }
                is GameSessionDecider.Action.Switch -> {
                    gamingEngine.toggleGamingMode(false)
                    gamingEngine.toggleGamingMode(true, action.to)
                    postStatus(getString(R.string.gt_svc_session_active, action.to))
                }
                GameSessionDecider.Action.Noop -> {
                    val active = sessionManager.sessionActivePkg.value
                    postStatus(
                        if (active != null) getString(R.string.gt_svc_session_active, active)
                        else getString(R.string.gt_svc_session_watching)
                    )
                }
            }
        }
    }

    private fun hasUsageAccess(): Boolean = ForegroundWatcher.hasUsageAccess(this)

    private fun queryLatestForegroundPackage(
        usm: UsageStatsManager,
        begin: Long,
        end: Long
    ): String? = ForegroundWatcher.queryLatestForegroundPackage(usm, begin, end)

    private fun postStatus(text: String) {
        if (text == lastStatus) return
        lastStatus = text
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        CatsmokerNotifications.ensureGroup(nm)
        nm.createNotificationChannel(
            CatsmokerNotifications.channel(CHANNEL_ID, getString(R.string.gt_svc_session_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(text: String): android.app.Notification {
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val stop = android.app.PendingIntent.getForegroundService(
            this,
            REQUEST_STOP,
            Intent(this, GameSessionService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.gt_svc_session_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_name)
            .setOngoing(true)
            .setGroup(CatsmokerNotifications.GROUP_KEY)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_action_name, getString(R.string.notification_stop), stop)
            .build()
    }

    override fun onDestroy() {
        CatsmokerNotifications.detachOwner(this, "GameSession")
        // Ending the watch must not strand a session: whatever this service started is
        // reverted through the engine, like switching the card off by hand. A fresh scope —
        // the revert must outlive the job cancelled below, or the cancel wins the race and
        // the settings stay held with nothing watching them.
        CoroutineScope(Dispatchers.IO).launch {
            if (sessionManager.sessionActivePkg.value != null) {
                gamingEngine.toggleGamingMode(false)
                sessionManager.release()
            }
        }
        pollingJob?.cancel()
        job.cancel()
        sendBroadcast(Intent(ACTION_SERVICE_STOPPED).setPackage(packageName))
        super.onDestroy()
    }
}
