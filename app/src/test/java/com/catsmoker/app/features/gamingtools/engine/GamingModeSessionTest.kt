package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the decision table that turns a persisted Gaming Mode record into an honest startup state.
 *
 * The failure this replaces was trusting `is_active` alone: after a reboot the settings are stock
 * but the flag says active, after a crash before the flag the settings may be applied while the UI
 * says idle, and package suspensions survive both. These cases keep the two lie directions closed.
 */
class GamingModeSessionTest {

    @Test
    fun closedSessionWithNoRecordsIsClean() {
        val verdict = GamingModeSession.reconcile(
            sessionOpen = false,
            bootCountAtStart = 0,
            currentBootCount = 12,
            recorded = emptySet(),
            suspendedNow = emptySet(),
            appOpsRecorded = emptySet()
        )
        assertFalse(verdict.keepSession)
        assertFalse(verdict.rebootedSinceStart)
        assertFalse(verdict.needsRecovery)
        assertTrue(verdict.pendingPackages.isEmpty())
    }

    @Test
    fun openSessionOnTheSameBootStaysOnRecord() {
        val verdict = GamingModeSession.reconcile(
            sessionOpen = true,
            bootCountAtStart = 12,
            currentBootCount = 12,
            recorded = emptySet(),
            suspendedNow = emptySet(),
            appOpsRecorded = emptySet()
        )
        // Settings may still be applied, so the power button must revert rather than start again.
        assertTrue(verdict.keepSession)
        assertFalse(verdict.rebootedSinceStart)
        assertFalse(verdict.needsRecovery)
    }

    @Test
    fun rebootedSessionDropsSettingsButKeepsFrozenApps() {
        val verdict = GamingModeSession.reconcile(
            sessionOpen = true,
            bootCountAtStart = 12,
            currentBootCount = 13,
            recorded = setOf("com.frozen"),
            suspendedNow = setOf("com.frozen"),
            appOpsRecorded = emptySet()
        )
        // The reboot reset the settings; it did not wake the suspended package.
        assertTrue(verdict.keepSession)
        assertTrue(verdict.rebootedSinceStart)
        assertEquals(listOf("com.frozen"), verdict.pendingPackages)
        assertTrue(verdict.needsRecovery)
        assertTrue(verdict.verified)
    }

    @Test
    fun rebootedSessionWithNoLeftoversClosesCompletely() {
        val verdict = GamingModeSession.reconcile(
            sessionOpen = true,
            bootCountAtStart = 12,
            currentBootCount = 13,
            recorded = setOf("com.awake"),
            suspendedNow = emptySet(),
            appOpsRecorded = emptySet()
        )
        assertFalse(verdict.keepSession)
        assertTrue(verdict.rebootedSinceStart)
        assertTrue(verdict.pendingPackages.isEmpty())
    }

    @Test
    fun unreadablePackageStateKeepsTheRecordUnverified() {
        val verdict = GamingModeSession.reconcile(
            sessionOpen = false,
            bootCountAtStart = 12,
            currentBootCount = 12,
            recorded = setOf("com.maybe.frozen"),
            suspendedNow = null,
            appOpsRecorded = emptySet()
        )
        // Unknown is not empty: the record stays as evidence, and the UI says it is unverified.
        assertTrue(verdict.keepSession)
        assertFalse(verdict.verified)
        assertEquals(listOf("com.maybe.frozen"), verdict.pendingPackages)
    }

    @Test
    fun onlyPackagesStillSuspendedStayPending() {
        val verdict = GamingModeSession.reconcile(
            sessionOpen = false,
            bootCountAtStart = 0,
            currentBootCount = 20,
            recorded = setOf("com.awake", "com.frozen"),
            suspendedNow = setOf("com.frozen"),
            appOpsRecorded = emptySet()
        )
        assertEquals(listOf("com.frozen"), verdict.pendingPackages)
        assertTrue(verdict.keepSession)
    }

    @Test
    fun appOpsAloneStillNeedRecovery() {
        val verdict = GamingModeSession.reconcile(
            sessionOpen = false,
            bootCountAtStart = 12,
            currentBootCount = 12,
            recorded = emptySet(),
            suspendedNow = emptySet(),
            appOpsRecorded = setOf("com.restricted")
        )
        assertTrue(verdict.keepSession)
        assertTrue(verdict.needsRecovery)
        assertEquals(listOf("com.restricted"), verdict.pendingAppOps)
    }

    @Test
    fun unknownBootCountsCountAsTheSameBoot() {
        // Reverting a snapshot the reboot already reset is harmless; skipping it on a live session
        // leaves settings applied with no way back. The safe direction is the same boot.
        val verdict = GamingModeSession.reconcile(
            sessionOpen = true,
            bootCountAtStart = 0,
            currentBootCount = 12,
            recorded = emptySet(),
            suspendedNow = emptySet(),
            appOpsRecorded = emptySet()
        )
        assertFalse(verdict.rebootedSinceStart)
        assertTrue(verdict.keepSession)
    }

    @Test
    fun pendingRecoveryIsSortedForStableUi() {
        val verdict = GamingModeSession.reconcile(
            sessionOpen = false,
            bootCountAtStart = 0,
            currentBootCount = 1,
            recorded = setOf("com.c", "com.a", "com.b"),
            suspendedNow = null,
            appOpsRecorded = setOf("com.z", "com.y")
        )
        assertEquals(listOf("com.a", "com.b", "com.c"), verdict.pendingPackages)
        assertEquals(listOf("com.y", "com.z"), verdict.pendingAppOps)
    }
}
