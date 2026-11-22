package com.catsmoker.app.screens.gaming.apps

/** A missing answer, including a silent exit 0, is not evidence that a package woke up. */
object SuspendVerdict {

    private val commandState =
        Regex("""Package (\S+) new suspended state: (true|false)""", RegexOption.IGNORE_CASE)

    fun reportedState(pkg: String, exitCode: Int, stdout: String): Boolean? {
        if (exitCode != 0) return null
        return stdout.lineSequence().mapNotNull { commandState.matchEntire(it.trim()) }
            .firstOrNull { it.groupValues[1] == pkg }?.groupValues?.get(2)?.equals("true", ignoreCase = true)
    }

    /** AOSP's explicit answer to `pm unsuspend` for a package that has been uninstalled. */
    fun isMissingPackage(pkg: String, exitCode: Int, output: String): Boolean =
        exitCode > 0 && output.lineSequence().any {
            it.trim() == "java.lang.IllegalArgumentException: Unknown target package: $pkg"
        }

    /**
     * `Package [<name>] (<hash>):` — one header per package in `dumpsys package`.
     *
     * Some builds print the header without the hash, so both shapes are accepted. Anchored to the
     * whole line so component lines (which also contain the package name) never start a block.
     */
    private val PACKAGE_HEADER =
        Regex("""^\s*Package \[([^\]]+)]\s*(?:\([^)]*\))?:\s*$""")

    /**
     * Only user 0 counts: treating a work profile's suspension as ours would leave the record
     * pending forever while issuing commands that cannot clear it.
     */
    private val userHeader = Regex("""^\s*User (\d+):""")
    private val suspendedFlag = Regex("""\bsuspended=(true|false)\b""")
    private val installedFlag = Regex("""\binstalled=true\b""")
    private val quarantineFlag = Regex("""\bquarantined=true\b""")
    // Android 11+ prints the owner as `<userId>package`, e.g. `<0>com.android.shell`.
    // The user prefix is part of the suspension key, so it cannot be dropped: another
    // profile's root cannot be restored by this profile's root command.
    private val ownerField = Regex("""(?:^|\s)suspendingPackage=(?:<(\d+)>)?(\S+)""")

    internal data class PackageState(
        val suspended: Boolean,
        val installed: Boolean,
        val owners: Set<String> = emptySet(),
        val quarantined: Boolean = false
    )

    fun parseDumpsysStates(output: String): Map<String, Boolean> =
        parseDumpsysPackages(output).mapValues { it.value.suspended }

    /**
     * Record only explicit states from `dumpsys package packages`. Missing user-state lines
     * stay unknown, and the stale factory copy of an updated system app isn't live evidence.
     * Android 9–10 report the suspending package inline; newer releases list owners below the
     * user line. Other user headers must be retained to delimit those entries.
     */
    internal fun parseDumpsysPackages(output: String): Map<String, PackageState> = buildMap {
        var current: String? = null
        var userZero = false
        for (line in output.lineSequence()) {
            if (line.trim() == "Hidden system packages:") break
            val header = PACKAGE_HEADER.matchEntire(line)
            if (header != null) {
                current = header.groupValues[1]
                userZero = false
                continue
            }
            val pkg = current ?: continue
            val user = userHeader.find(line)
            if (user != null) {
                userZero = user.groupValues[1] == "0"
                if (!userZero) continue
                val flags = line.substringBefore(" dialogInfo=").substringBefore(" dialogMessage=")
                val suspended = suspendedFlag.find(flags)?.groupValues?.get(1) ?: continue
                val owner = ownerOf(ownerField.find(flags))
                put(
                    pkg,
                    PackageState(
                        suspended = suspended == "true",
                        installed = installedFlag.containsMatchIn(flags),
                        owners = setOfNotNull(owner),
                        quarantined = quarantineFlag.containsMatchIn(flags)
                    )
                )
            } else if (userZero && line.trimStart().startsWith("suspendingPackage=")) {
                val state = get(pkg)?.takeIf { it.suspended } ?: continue
                val owner = ownerOf(ownerField.find(line)) ?: continue
                put(pkg, state.copy(owners = state.owners + owner, quarantined = state.quarantined ||
                    quarantineFlag.containsMatchIn(line)))
            }
        }
    }

    /**
     * Keeps the profile prefix for owners outside user 0, so they can never be mistaken for
     * this profile's root or Shell. Older releases print the package name without a prefix.
     */
    private fun ownerOf(match: MatchResult?): String? {
        val found = match ?: return null
        val user = found.groupValues[1]
        val packageName = found.groupValues[2]
        return if (user.isEmpty() || user == "0") packageName else "<$user>$packageName"
    }
}
