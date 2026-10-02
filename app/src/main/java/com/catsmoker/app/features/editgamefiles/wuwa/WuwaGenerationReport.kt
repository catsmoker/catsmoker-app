package com.catsmoker.app.features.editgamefiles.wuwa

/**
 * Generation report for Engine.ini: accepted / rejected / render-pipeline-flagged
 * cvars (M100 verify surfacing + M105 RT flags).
 *
 * The report compares the Engine text immediately before and after the
 * forbidden-cvar strip — the only transform between the two snapshots — so a
 * rejected key is always a key the strip actually removed, never an invented
 * count, and an accepted key is always one the final file carries. Key parsing
 * mirrors [WuWaConfigGenerator.deduplicateIniText] exactly (sections, comments,
 * blanks and `+CVars=`/`-CVars=` prefixes skipped, case-insensitive), so the
 * report can never disagree with what the deploy channel pushes.
 *
 * Render-pipeline families (`RT_KEY_PREFIXES`, the M105 desktop-class set:
 * ray tracing + Lumen) are flagged informationally from the FINAL text — never
 * auto-removed, never a verdict on any particular value. Unknown-cvar axes stay
 * out deliberately: without the CVar database loaded, an "unknown" count would
 * be a guess, and a printed guess is worse than an absent field.
 */
object WuwaGenerationReport {

    /** Render-pipeline families flagged informationally (M105 scope, keys only). */
    val RT_KEY_PREFIXES = listOf("r.raytracing.", "r.lumen.")

    data class FileReport(
        /** Distinct cvar keys the final file carries. */
        val accepted: Int = 0,
        /** Keys present before the strip but absent after it, sorted. */
        val rejected: List<String> = emptyList(),
        /** Final-file keys in [RT_KEY_PREFIXES], sorted. Informational only. */
        val rtFlagged: List<String> = emptyList()
    )

    fun report(preStripEngine: String, finalEngine: String): FileReport {
        val before = cvarKeys(preStripEngine)
        val after = cvarKeys(finalEngine)
        return FileReport(
            accepted = after.size,
            rejected = (before - after).sorted(),
            rtFlagged = after.filter { key ->
                RT_KEY_PREFIXES.any { key.startsWith(it) }
            }.sorted()
        )
    }

    private fun cvarKeys(text: String): Set<String> {
        val keys = mutableSetOf<String>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#") ||
                trimmed.startsWith("//") || trimmed.startsWith("[")
            ) continue
            val cvarLine = trimmed.removePrefix("+CVars=").removePrefix("-CVars=").trim()
            if (cvarLine.isEmpty() || cvarLine.startsWith(";") || cvarLine.startsWith("#") ||
                cvarLine.startsWith("//") || cvarLine.startsWith("[")
            ) continue
            val eq = cvarLine.indexOf('=')
            if (eq <= 0) continue
            keys.add(cvarLine.substring(0, eq).trim().lowercase())
        }
        return keys
    }
}
