package com.catsmoker.app.shared.util

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.catsmoker.app.R
import com.catsmoker.app.system.MainActivity

/**
 * The one notification group every Catsmoker notification belongs to.
 *
 * Eight foreground services can each hold a notification at once (gaming mode, overlay,
 * crosshair, session watch, force-stop, VPN, booster, dexopt sweep), and without a group
 * the shade shows them as a scattered unrelated list. Each service still keeps its own
 * notification — a foreground service must own its `startForeground` notification for its
 * own lifetime, so merging them into one would break that contract — but all of them sit
 * inside this group, which is what makes the shade read as one Catsmoker block.
 *
 * The group only bundles visually because of the summary row below: Android shows
 * same-group notifications individually until a `setGroupSummary(true)` notification for
 * the key exists. The summary is owned collectively — every service attaches on start
 * and detaches on destroy, and the row is cancelled only when the owner set empties, so
 * it can neither outlive the group nor vanish while members remain. All of this runs in
 * the app's main process (every service and the sweep worker live there), so a plain
 * synchronized set is sufficient; a process death clears the shade and the set together.
 *
 * The title is the brand and stays English, like `app_name`. Channels created before this
 * group existed pick it up the next time their service creates them again (creating an
 * existing channel ID updates it, including its group), so no migration step is needed —
 * but each feature must actually run once after the update for its channel to migrate.
 */
object CatsmokerNotifications {

    const val GROUP_KEY = "com.catsmoker.app.GROUP"

    const val GROUP_TITLE = "Catsmoker"

    /** Idempotent: safe to call from every service's channel setup. */
    fun ensureGroup(nm: NotificationManager) {
        nm.createNotificationChannelGroup(NotificationChannelGroup(GROUP_KEY, GROUP_TITLE))
    }

    /** A channel pre-assigned to the group. Apply `description` etc. on the result as before. */
    fun channel(id: String, name: String, importance: Int): NotificationChannel =
        NotificationChannel(id, name, importance).apply { group = GROUP_KEY }

    // ------------------------------------------------------------ group summary

    /** The summary row's notification ID — free (101–107, 4201, 4301 are the members). */
    private const val SUMMARY_ID = 100

    private const val SUMMARY_CHANNEL_ID = "catsmoker_summary_channel"

    /** Services (and the sweep worker) currently holding the group open. Main process only. */
    private val activeOwners = mutableSetOf<String>()

    /**
     * Registers [owner] (a service's class simple name) and (re-)posts the summary row.
     * Idempotent: repeated starts with the same owner post once.
     */
    fun attachOwner(context: Context, owner: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        synchronized(activeOwners) {
            activeOwners += owner
            ensureGroup(nm)
            nm.createNotificationChannel(
                channel(SUMMARY_CHANNEL_ID, GROUP_TITLE, NotificationManager.IMPORTANCE_LOW)
            )
            nm.notify(SUMMARY_ID, summary(context))
        }
    }

    /**
     * Unregisters [owner], cancelling the summary only when nobody remains — so stopping
     * one service while three others run never unbundles the shade, and the last stop
     * leaves no stale row behind.
     */
    fun detachOwner(context: Context, owner: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        synchronized(activeOwners) {
            activeOwners -= owner
            if (activeOwners.isEmpty()) {
                nm.cancel(SUMMARY_ID)
            }
        }
    }

    /**
     * The bundle header: brand title, tap-to-open, silent and ongoing so it holds the
     * bundle together instead of being swiped away from under it.
     */
    private fun summary(context: Context): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, SUMMARY_CHANNEL_ID)
            .setContentTitle(GROUP_TITLE)
            .setContentText(context.getString(R.string.notif_summary_sub))
            .setSmallIcon(R.drawable.ic_stat_name)
            .setOngoing(true)
            .setSilent(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setContentIntent(open)
            .build()
    }
}
