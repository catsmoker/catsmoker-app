package com.catsmoker.app.features.gamingtools.engine

/**
 * The rule that decides what a persisted Gaming Mode record means after the process that wrote it
 * is gone.
 *
 * The old behaviour was to trust `is_active`: the app re-applied every lever, set the switch back
 * to Active, and called that recovery. That is a claim, not evidence — a reboot or a refused write
 * can leave any of those levers at stock while frozen apps stay frozen, and a crash before the
 * flag was written produced the opposite lie (apps suspended, UI idle, flag false).
 *
 * This reconciliation starts from what the device can actually be asked:
 *  - the package manager's per-package suspension list (suspendedNow, null when unreadable);
 *  - the persisted record of what this app suspended;
 *  - the boot count, which tells whether the settings levers could even have survived.
 *
 * Pure so the decision table is pinned by a JVM test rather than only by device runs.
 */
object GamingModeSession {

    /**
     * @param keepSession true when the persisted session must stay on record: its settings may
     *   still be applied, or something it wrote has not been verified back.
     * @param rebootedSinceStart true when the session began before the current boot, so the
     *   settings it applied were reset by the reboot.
     * @param pendingPackages recorded packages that are still suspended, or every recorded package
     *   when the device could not answer.
     * @param pendingAppOps packages whose `RUN_IN_BACKGROUND` app-op is still recorded as changed.
     * @param verified false when suspendedNow was unreadable; the pending list is then the
     *   record alone, not live evidence.
     */
    data class Verdict(
        val keepSession: Boolean,
        val rebootedSinceStart: Boolean,
        val pendingPackages: List<String>,
        val pendingAppOps: List<String>,
        val verified: Boolean
    ) {
        /** Whether a restore action has anything to do. Settings-only leftovers need no app restore. */
        val needsRecovery: Boolean get() = pendingPackages.isNotEmpty() || pendingAppOps.isNotEmpty()
    }

    /**
     * @param sessionOpen the persisted `is_active` flag — "a session was started and not closed".
     * @param bootCountAtStart boot count recorded at activation, or 0 when unknown.
     * @param currentBootCount this boot's count, or 0 when the platform would not report it.
     * @param recorded packages the session recorded as suspended.
     * @param suspendedNow packages the package manager reports as suspended for this user, or
     *   null when the query could not be run or parsed.
     * @param appOpsRecorded packages whose background app-op the session changed.
     */
    fun reconcile(
        sessionOpen: Boolean,
        bootCountAtStart: Int,
        currentBootCount: Int,
        recorded: Set<String>,
        suspendedNow: Set<String>?,
        appOpsRecorded: Set<String>
    ): Verdict {
        // Unknown boot counts on either side are treated as "no reboot": reverting a snapshot on a
        // device that already reset itself is harmless, while skipping the revert on a live session
        // leaves system settings applied with no way back.
        val rebooted = sessionOpen &&
            bootCountAtStart > 0 &&
            currentBootCount > 0 &&
            bootCountAtStart != currentBootCount

        val pendingPackages = when {
            recorded.isEmpty() -> emptyList()
            suspendedNow != null -> recorded.filter { it in suspendedNow }.sorted()
            else -> recorded.sorted()
        }

        val settingsStillApplied = sessionOpen && !rebooted
        val pendingAppOps = appOpsRecorded.sorted()
        return Verdict(
            keepSession = settingsStillApplied || pendingPackages.isNotEmpty() || pendingAppOps.isNotEmpty(),
            rebootedSinceStart = rebooted,
            pendingPackages = pendingPackages,
            pendingAppOps = pendingAppOps,
            verified = suspendedNow != null
        )
    }
}
