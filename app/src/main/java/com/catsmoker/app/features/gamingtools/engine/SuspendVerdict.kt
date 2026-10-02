package com.catsmoker.app.features.gamingtools.engine

/**
 * What `pm suspend` / `pm unsuspend` actually reported, read from the exit code and the shell's
 * own words rather than from the fact that a command was issued.
 *
 * `pm` prints the verdict on one line — `Package <pkg> new suspended state: true/false` — and
 * some builds still exit 0 after refusing, so both signals are read, the same way
 * `classifyCompileOutput` reads both for `cmd package compile`. A silent exit 0 is taken at
 * face value; inventing a failure there would be as wrong as inventing a success.
 *
 * Pure so the rule is pinnable by a JVM unit test: the revert path depends on it to decide
 * which packages are actually awake again, and an unverified unsuspend is how apps end up
 * frozen after Gaming Mode is off.
 */
object SuspendVerdict {

    /**
     * Whether `pm suspend` actually froze the package: exit 0 without the shell reporting the
     * package is still unsuspended (`state: false`).
     */
    fun isSuspendConfirmed(exitCode: Int, stdout: String): Boolean =
        exitCode == 0 && !stdout.contains("state: false", ignoreCase = true)

    /**
     * Whether `pm unsuspend` actually woke the package back up: exit 0 without the shell
     * reporting the package is still suspended (`state: true`).
     */
    fun isUnsuspendConfirmed(exitCode: Int, stdout: String): Boolean =
        exitCode == 0 && !stdout.contains("state: true", ignoreCase = true)

    /**
     * Whether a `list packages -s` answer is safe to exclude from.
     *
     * Device evidence (Lenovo TB-8505X, Android 10 MTK: 167 of 176 packages listed, including
     * running system apps): the `-s` flag on some builds lists nearly everything, in which case
     * excluding its contents would gut the sweep. The `android` package (the system server) can
     * never be suspended — `pm suspend` refuses it — so its presence proves flag misuse and the
     * whole answer is discarded (fail-open to suspending normally) rather than trusted.
     */
    fun isSuspendListTrustworthy(suspended: Set<String>): Boolean =
        SUSPEND_CANARY_PACKAGES.none { it in suspended }

    /** Packages the platform cannot suspend; listing one proves a broken `-s` flag. */
    private val SUSPEND_CANARY_PACKAGES = setOf("android")

    /**
     * The packages `cmd package list packages -s` reports as already suspended, one
     * `package:<name>` per line. Read before the sweep so externally suspended apps are
     * skipped — but only when [isSuspendListTrustworthy] passes; an untrustworthy answer is
     * discarded whole rather than trusted. Non-package lines (failures, blanks) are
     * ignored — an unreadable answer means "nothing known suspended", never a guess.
     */
    fun parseSuspendedPackages(output: String): Set<String> {
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
