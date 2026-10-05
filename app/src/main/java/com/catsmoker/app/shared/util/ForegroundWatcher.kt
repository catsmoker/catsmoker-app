package com.catsmoker.app.shared.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Shared foreground-app monitoring (consolidation C7).
 *
 * GameSessionService (start Gaming Mode) and AutoForceStopService (kill leavers)
 * polled the same `UsageStatsManager` query at different cadences with duplicated
 * access checks. Both keep their own poll loop + decider — this only shares the
 * access check and the latest-foreground query so duplicate polling logic cannot drift.
 */
object ForegroundWatcher {

    /** Whether usage access is granted (an appop, not a runtime permission). */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        // checkOpNoThrow is the non-deprecated spelling on the compile SDK and has
        // existed since API 19, so no version branch is needed.
        val mode = try {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } catch (_: Exception) {
            return false
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Latest package that moved to foreground in [begin, end], or null when the
     * query failed / nothing moved. A null foreground is never acted on.
     */
    fun queryLatestForegroundPackage(
        usm: UsageStatsManager,
        begin: Long,
        end: Long
    ): String? {
        val events = try {
            usm.queryEvents(begin, end)
        } catch (_: Exception) {
            return null
        } ?: return null
        var latest: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latest = event.packageName
            }
        }
        return latest
    }
}
