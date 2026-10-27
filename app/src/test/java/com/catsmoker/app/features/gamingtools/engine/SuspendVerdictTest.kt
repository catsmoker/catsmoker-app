package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the `pm suspend` / `pm unsuspend` verdict rules plus the `dumpsys package` suspension parser.
 *
 * The revert path decides from the verdicts whether a package is actually awake again, so a
 * refusal the shell reports as text must read as a failure even when the exit code is 0 —
 * otherwise the package is dropped from the record while still frozen.
 *
 * The parser half is pinned against real device output shape (Android 15): each package block
 * starts with `Package [pkg] (hash):` and its per-user line carries `suspended=true`. The previous
 * source was `cmd package list packages -s`, where `-s` means *system* packages — on the test
 * device it returned 250 running system apps and no suspension information at all.
 */
class SuspendVerdictTest {

    // ------------------------------------------------- dumpsys package parser

    @Test
    fun parsesSuspendedPackagesFromDumpsysBlocks() {
        val output = """
            Package [com.frozen.one] (abc123):
              userId=10123
              User 0: ceDataInode=1 installed=true hidden=false suspended=true
              User 10: ceDataInode=2 installed=true hidden=false suspended=true
            Package [com.awake.two] (def456):
              userId=10124
              User 0: ceDataInode=3 installed=true hidden=false suspended=false
            Package [com.frozen.three] (ghi789):
              User 0: installed=true hidden=false suspended=true suspendingPackage=com.android.shell
        """.trimIndent()
        assertEquals(
            setOf("com.frozen.one", "com.frozen.three"),
            SuspendVerdict.parseDumpsysSuspended(output)
        )
    }

    @Test
    fun onlyUserZeroCountsBecauseThatIsTheUserTheEngineSuspends() {
        val output = """
            Package [com.work.profile.app] (abc):
              User 0: installed=true suspended=false
              User 10: installed=true suspended=true
        """.trimIndent()
        assertEquals(emptySet<String>(), SuspendVerdict.parseDumpsysSuspended(output))
    }

    @Test
    fun packageHeaderWithoutHashIsAccepted() {
        val output = """
            Package [com.example.game]:
              User 0: installed=true suspended=true
        """.trimIndent()
        assertEquals(setOf("com.example.game"), SuspendVerdict.parseDumpsysSuspended(output))
    }

    @Test
    fun componentLinesDoNotStartANewPackageBlock() {
        val output = """
            Package [com.example.game] (abc):
              User 0: installed=true suspended=true
              com.example.game/com.example.game.MainActivity:
                suspended=false
        """.trimIndent()
        assertEquals(setOf("com.example.game"), SuspendVerdict.parseDumpsysSuspended(output))
    }

    @Test
    fun unreadableOutputIsUnknownNotEmpty() {
        // An empty answer is "the command failed", not "nothing is suspended". Collapsing the two
        // is how a failed query would make the recovery prompt disappear while apps stay frozen.
        assertNull(SuspendVerdict.parseDumpsysSuspended(""))
        assertNull(SuspendVerdict.parseDumpsysSuspended("Error: package manager unavailable"))
        assertNull(SuspendVerdict.parseDumpsysSuspended("Permission denial: can't dump"))
    }

    @Test
    fun aDumpWithNoSuspensionsIsAnEmptySetNotUnknown() {
        val output = """
            Package [com.example.game] (abc):
              User 0: installed=true suspended=false
        """.trimIndent()
        assertEquals(emptySet<String>(), SuspendVerdict.parseDumpsysSuspended(output))
    }

    @Test
    fun suspendedInsideAnotherWordDoesNotCount() {
        val output = """
            Package [com.example.game] (abc):
              User 0: installed=true suspended=false lastSuspendedTime=123
        """.trimIndent()
        assertEquals(emptySet<String>(), SuspendVerdict.parseDumpsysSuspended(output))
    }

    // --------------------------------------------------------------- suspend

    @Test
    fun suspendConfirmedWhenSilentExitZero() {
        assertTrue(SuspendVerdict.isSuspendConfirmed(0, ""))
    }

    @Test
    fun suspendConfirmedWhenShellReportsSuspended() {
        assertTrue(
            SuspendVerdict.isSuspendConfirmed(
                0,
                "Package com.example.app new suspended state: true"
            )
        )
    }

    @Test
    fun suspendRefusedWhenShellReportsStillUnsuspended() {
        // The exact refusal shape the activation path already relied on.
        assertFalse(
            SuspendVerdict.isSuspendConfirmed(
                0,
                "Package com.example.app new suspended state: false"
            )
        )
    }

    @Test
    fun suspendRefusedWhenExitCodeIsNonZero() {
        assertFalse(SuspendVerdict.isSuspendConfirmed(1, ""))
        assertFalse(
            SuspendVerdict.isSuspendConfirmed(
                1,
                "Package com.example.app new suspended state: true"
            )
        )
    }

    // --------------------------------------------------------------- unsuspend

    @Test
    fun unsuspendConfirmedWhenSilentExitZero() {
        assertTrue(SuspendVerdict.isUnsuspendConfirmed(0, ""))
    }

    @Test
    fun unsuspendConfirmedWhenShellReportsAwake() {
        assertTrue(
            SuspendVerdict.isUnsuspendConfirmed(
                0,
                "Package com.example.app new suspended state: false"
            )
        )
    }

    @Test
    fun unsuspendRefusedWhenShellReportsStillSuspended() {
        assertFalse(
            SuspendVerdict.isUnsuspendConfirmed(
                0,
                "Package com.example.app new suspended state: true"
            )
        )
    }

    @Test
    fun unsuspendRefusedWhenExitCodeIsNonZero() {
        assertFalse(SuspendVerdict.isUnsuspendConfirmed(1, ""))
        assertFalse(
            SuspendVerdict.isUnsuspendConfirmed(
                1,
                "Package com.example.app new suspended state: false"
            )
        )
    }

    @Test
    fun verdictMatchingIsCaseInsensitive() {
        assertFalse(SuspendVerdict.isSuspendConfirmed(0, "New Suspended State: False"))
        assertFalse(SuspendVerdict.isUnsuspendConfirmed(0, "New Suspended State: True"))
    }
}
