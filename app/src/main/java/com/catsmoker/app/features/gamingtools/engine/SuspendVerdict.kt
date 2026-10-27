package com.catsmoker.app.features.gamingtools.engine

/**
 * What `pm suspend` / `pm unsuspend` actually reported, plus the two parsers that read suspension
 * state back from the package manager.
 *
 * `pm` prints the verdict on one line — `Package <pkg> new suspended state: true/false` — and
 * some builds still exit 0 after refusing, so both signals are read, the same way
 * `classifyCompileOutput` reads both for `cmd package compile`. A silent exit 0 is taken at
 * face value; inventing a failure there would be as wrong as inventing a success.
 *
 * Pure so the rules are pinnable by JVM unit tests: the revert path depends on them to decide
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
     * `Package [<name>] (<hash>):` — one header per package in `dumpsys package`.
     *
     * Some builds print the header without the hash, so both shapes are accepted. Anchored to the
     * whole line so component lines (which also contain the package name) never start a block.
     */
    private val PACKAGE_HEADER =
        Regex("""^\s*Package \[([^\]]+)]\s*(?:\([^)]*\))?:\s*$""")

    /**
     * `User 0: ... suspended=true ...` — the per-user state line inside a package block.
     *
     * Only user 0 is read because every `pm suspend`/`pm unsuspend` this app issues passes
     * `--user 0`. Treating another profile's suspension as ours would keep the recovery prompt
     * alive forever while issuing commands that cannot clear it.
     */
    private val USER_ZERO_SUSPENDED = Regex("""^\s*User 0:.*\bsuspended=true\b""")

    /**
     * Reads the suspended packages out of one `dumpsys package` dump.
     *
     * Why this source: `pm list packages` has no `--suspended` option on current Android
     * (verified on Android 15: `Error: Unknown option: --suspended`), and `-s` means *system*
     * packages, which is what the previous parser was accidentally reading — 250 system apps on
     * a device with none suspended. `dumpsys package` exposes the real per-user state as
     * `suspended=true`, next to `suspendingPackage`, in one call for the whole device.
     *
     * @return the suspended package set, or null when the text is not a package dump at all
     *   (command failure, truncated output, permission refusal). Null means "unknown", and every
     *   caller must treat it that way rather than as "nothing is suspended".
     */
    fun parseDumpsysSuspended(output: String): Set<String>? {
        var sawPackage = false
        var current: String? = null
        val suspended = mutableSetOf<String>()
        for (line in output.lineSequence()) {
            val header = PACKAGE_HEADER.matchEntire(line)
            if (header != null) {
                sawPackage = true
                current = header.groupValues[1].trim()
                continue
            }
            val pkg = current ?: continue
            if (USER_ZERO_SUSPENDED.containsMatchIn(line)) suspended += pkg
        }
        return if (sawPackage) suspended else null
    }
}
