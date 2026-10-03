package com.catsmoker.app.shared.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The module-loaded heartbeat the Xposed module records in our own process.
 *
 * The app cannot ask LSPosed whether the module is active — and "module not
 * detected" reports (e.g. #2, LSPosed 1.10.1) are otherwise indistinguishable
 * from a scope the user never ticked. So the module writes
 * [LSPosedConfig.KEY_MODULE_HEARTBEAT_ELAPSED] on every load into our own
 * process, and the status card reads it back. `elapsedRealtime` (not wall
 * time) is the clock because it resets on every reboot: a stored value
 * greater than now can only predate the last boot, which is exactly when a
 * heartbeat goes stale.
 */
class ModuleHeartbeatTest {

    @Test
    fun absentHeartbeatIsNotActive() {
        assertFalse(LSPosedConfig.isHeartbeatFresh(0L, 123456L))
    }

    @Test
    fun negativeHeartbeatIsNotActive() {
        assertFalse(LSPosedConfig.isHeartbeatFresh(-5L, 123456L))
    }

    @Test
    fun heartbeatFromAPreviousBootIsNotActive() {
        assertFalse(LSPosedConfig.isHeartbeatFresh(999999L, 123456L))
    }

    @Test
    fun heartbeatFromThisBootIsActive() {
        assertTrue(LSPosedConfig.isHeartbeatFresh(123000L, 123456L))
        assertTrue(LSPosedConfig.isHeartbeatFresh(123456L, 123456L))
    }

    @Test
    fun weekOldHeartbeatIsStale() {
        // Device evidence: a 7-day-old heartbeat read Active while the module could not
        // load at all (stale install path). Age-bounded now: reopening the app refreshes
        // a working module's heartbeat on the spot, so only dead modules stay stale.
        val now = 7L * 24L * 60L * 60L * 1000L
        assertFalse(LSPosedConfig.isHeartbeatFresh(11_564_730L, now))
        assertFalse(LSPosedConfig.isHeartbeatFresh(now - LSPosedConfig.MAX_HEARTBEAT_AGE_MS - 1L, now))
        assertTrue(LSPosedConfig.isHeartbeatFresh(now - LSPosedConfig.MAX_HEARTBEAT_AGE_MS + 1L, now))
    }
}
