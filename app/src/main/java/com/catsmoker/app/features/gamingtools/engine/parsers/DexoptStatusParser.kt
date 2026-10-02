package com.catsmoker.app.features.gamingtools.engine.parsers

/**
 * Parses ART dexopt state into normalized compiler-filter signals.
 *
 * The platform names the same filter three ways across builds — `status=X`,
 * `compiler-filter=X` / `compilerfilter=X`, `[status=X]` — so every line form is normalized
 * through [parseCompilerFilterFromLine]'s keyword map (`speed-profile` / `everything` /
 * `speed` / `quicken` / `verify` / `extract`), ported from the reference project's parser.
 * A package the dump names but never details is `unknown-present`: present, not optimal,
 * distinct from absent. A package the dump never names is null: unreadable, compiles.
 */
object DexoptStatusParser {
    fun parse(output: String): Map<String, String> {
        val statuses = HashMap<String, String>()
        var currentPkg: String? = null
        for (rawLine in output.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith("[") && line.endsWith("]") && !line.contains("=")) {
                currentPkg = line.substring(1, line.length - 1)
                continue
            }
            val pkg = currentPkg ?: continue
            // The platform's three line forms, first detailed line wins (as before). The
            // keyword scan is gated on filter-shaped lines so an APK path that merely
            // contains "speed" (e.g. a speedtest app) can never pose as a filter.
            val statusIdx = line.indexOf("status=")
            val filter = if (statusIdx >= 0) {
                line.substring(statusIdx + "status=".length)
                    .takeWhile { it.isLetterOrDigit() || it == '-' }
                    .lowercase()
                    .let { parseCompilerFilterFromLine(it) ?: it.takeIf { t -> t.isNotEmpty() } }
            } else {
                val lower = line.lowercase()
                if (lower.contains("filter") || lower.contains("compiler") || lower.startsWith("[")) {
                    parseCompilerFilterFromLine(lower)
                } else {
                    null
                }
            }
            if (!filter.isNullOrEmpty()) {
                statuses[pkg] = filter
                currentPkg = null
            }
        }
        return statuses
    }

    /**
     * The compiler-filter keyword one lowercased line carries, or null when it carries none.
     * Order matters: `speed-profile` contains `speed`, so it is tested first.
     */
    fun parseCompilerFilterFromLine(lowercasedLine: String): String? {
        return when {
            lowercasedLine.contains("speed-profile") -> "speed-profile"
            lowercasedLine.contains("everything") -> "everything"
            lowercasedLine.contains("[status=speed]") ||
                (lowercasedLine.contains("speed") && !lowercasedLine.contains("profile")) -> "speed"
            lowercasedLine.contains("quicken") -> "quicken"
            lowercasedLine.contains("verify") -> "verify"
            lowercasedLine.contains("run-from-apk") || lowercasedLine.contains("extract") -> "extract"
            else -> null
        }
    }

    /**
     * The filter for one package from a full dump: the keyword in its ~30-line section, or
     * `unknown-present` when the dump names the package but never details it, or null when
     * the dump never names it at all.
     *
     * The scan stops at the next package header: a filter belonging to another package must
     * never be attributed to this one (a case the reference's plain 30-line window gets
     * wrong when sections are short).
     */
    fun compilerFilterFor(packageName: String, dump: String): String? {
        val lines = dump.lineSequence().toList()
        val bracketed = "[$packageName]"
        val idx = lines.indexOfFirst { it.contains(bracketed) || it.contains(packageName) }
        if (idx < 0) return null
        var first = true
        for (line in lines.subList(idx, minOf(idx + 30, lines.size))) {
            val trimmed = line.trim()
            parseCompilerFilterFromLine(trimmed.lowercase())?.let { return it }
            // A new section starts: the filter below belongs to someone else. The header line
            // itself (first iteration) is skipped by the flag above.
            if (!first && trimmed.startsWith("[")) break
            first = false
        }
        return if (isPackagePresentInDexoptDump(packageName, dump)) "unknown-present" else null
    }

    /** Whether the package appears in the dump at all, in any bracketed form. */
    fun isPackagePresentInDexoptDump(packageName: String, dump: String): Boolean {
        if (dump.isBlank()) return false
        return dump.contains("[$packageName]")
    }

    /**
     * Interprets `cmd package compile --check <package>`: `true`/`false`, or prose containing
     * "compilation [not] needed" / "need[s] compile". Anything else is unknown (null), never
     * a fabricated verdict.
     */
    fun parseCompileCheckNeedsOptimization(output: String): Boolean? {
        if (output.isBlank()) return null
        val lower = output.trim().lowercase()
        if (lower == "true") return true
        if (lower == "false") return false
        if (lower.contains("compilation") && lower.contains("not") && lower.contains("needed")) return false
        if (lower.contains("compilation") && lower.contains("needed")) return true
        if (lower.contains("need") && lower.contains("compile")) {
            if (lower.contains("not") && lower.contains("needed")) return false
            if (lower.contains("needed")) return true
        }
        return null
    }
}
