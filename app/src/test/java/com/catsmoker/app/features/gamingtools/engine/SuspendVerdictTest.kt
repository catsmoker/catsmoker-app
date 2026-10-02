package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the `pm suspend` / `pm unsuspend` verdict rules plus the pre-suspended skip parser.
 *
 * The revert path decides from the verdicts whether a package is actually awake again, so a
 * refusal the shell reports as text must read as a failure even when the exit code is 0 —
 * otherwise the package is dropped from the record while still frozen.
 *
 * The parser half pins `parseSuspendedPackages` against
 * `reference/gamingtools/booster/.../shizuku/ShizukuManager.kt#getSuspendedPackages`: one
 * `cmd package list packages -s` call, `package:` prefix stripped. Suspending an app someone
 * else already suspended would record it as ours, and deactivation would then unsuspend an
 * app this session never froze.
 */
class SuspendVerdictTest {

    @Test
    fun parsesSuspendedPackageList() {
        val output = """
            package:com.frozen.one
            package:com.frozen.two
        """.trimIndent()
        assertEquals(setOf("com.frozen.one", "com.frozen.two"), SuspendVerdict.parseSuspendedPackages(output))
    }

    @Test
    fun blankOutputMeansNothingPreSuspended() {
        assertTrue(SuspendVerdict.parseSuspendedPackages("").isEmpty())
        assertTrue(SuspendVerdict.parseSuspendedPackages("  \n ").isEmpty())
    }

    @Test
    fun ignoresNonPackageLines() {
        val output = "package:com.frozen\nFailure: something\npackage:com.other  "
        assertEquals(setOf("com.frozen", "com.other"), SuspendVerdict.parseSuspendedPackages(output))
    }

    @Test
    fun suspendListWithTheAndroidPackageIsUntrustworthy() {
        // Device evidence (Lenovo TB-8505X, Android 10): `pm suspend android` is refused
        // (`new suspended state: false`) yet `package:android` still appears in
        // `list packages -s` — a flag that lists the unsuspendable lists nothing honestly.
        assertFalse(SuspendVerdict.isSuspendListTrustworthy(setOf("android", "com.frozen")))
        assertFalse(SuspendVerdict.isSuspendListTrustworthy(setOf("android")))
    }

    @Test
    fun saneSuspendListIsTrustworthy() {
        assertTrue(SuspendVerdict.isSuspendListTrustworthy(setOf("com.frozen.one", "com.frozen.two")))
        assertTrue(SuspendVerdict.isSuspendListTrustworthy(emptySet()))
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
