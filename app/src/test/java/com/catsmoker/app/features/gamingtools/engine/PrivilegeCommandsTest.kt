package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for centralized privileged command construction (Phase 1 / C5).
 *
 * Centralises command *construction* only — sequencing, snapshots, verification
 * and recovery stay with each caller in GamingEngine.
 */
class PrivilegeCommandsTest {

    @Test
    fun fixedPerformanceBuildsCmdPower() {
        assertEquals(
            listOf("cmd", "power", "set-fixed-performance-mode-enabled", "true"),
            PrivilegeCommands.fixedPerformanceArgs(true).toList()
        )
        assertEquals(
            listOf("cmd", "power", "set-fixed-performance-mode-enabled", "false"),
            PrivilegeCommands.fixedPerformanceArgs(false).toList()
        )
    }

    @Test
    fun memoryReclaimSequenceCoversTrimCompactKill() {
        val cmds = PrivilegeCommands.memoryReclaimCommands()
        assertTrue(cmds.any { it.contains("trim-caches") })
        assertTrue(cmds.any { it.contains("compact") })
        assertTrue(cmds.any { it == "am kill-all" })
    }

    @Test
    fun forceStopTargetsSinglePackage() {
        assertEquals(
            listOf("am", "force-stop", "com.example"),
            PrivilegeCommands.forceStopArgs("com.example").toList()
        )
    }

    @Test
    fun suspendTargetsCurrentUser() {
        assertEquals(
            listOf("pm", "suspend", "--user", "0", "com.example"),
            PrivilegeCommands.suspendArgs("com.example").toList()
        )
        assertEquals(
            listOf("pm", "unsuspend", "--user", "0", "com.example"),
            PrivilegeCommands.unsuspendArgs("com.example").toList()
        )
    }
}
