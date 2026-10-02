package com.catsmoker.app.features.editgamefiles.wuwa

/** One line of an INI diff: unchanged context, added, or removed. */
enum class WuwaDiffKind {
    CONTEXT,
    ADDED,
    REMOVED,
}

/** One diff line with its old/new line numbers (1-based; null on the absent side). */
data class WuwaDiffLine(
    val kind: WuwaDiffKind,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val text: String,
)

/** Added/removed/unchanged tallies for one diff. */
data class WuwaDiffSummary(
    val added: Int,
    val removed: Int,
    val unchanged: Int,
)

/** The full line diff of two INI texts. */
data class WuwaDiffResult(
    val lines: List<WuwaDiffLine>,
    val summary: WuwaDiffSummary,
)

/**
 * Line diff for INI texts (LCS backtrack), ported from
 * `reference/gamingtools/WuWa-Config-Android-main/.../util/LineDiff.kt`, read in full before
 * this file was written: same split, same table, same backtrack with the same tie-break
 * (addition preferred on equal scores), same summary.
 *
 * The reference renders this in its review screen against a freshly read device file;
 * here it pairs generated text with the newest local backup ([WuwaBackupDiff] handles
 * matching and BOM-aware decode) — the pack gate's own strip report deliberately keeps
 * its key-count instead (a line diff would be thrown off by the trailing newline the strip
 * normalizes; see `WuwaCommunityPack.stripVariant`).
 */
object WuwaLineDiff {

    fun compute(oldText: String, newText: String): WuwaDiffResult {
        val oldLines = oldText.split('\n')
        val newLines = newText.split('\n')
        val lcs = lcsTable(oldLines.toTypedArray(), newLines.toTypedArray())
        val out = ArrayList<WuwaDiffLine>(oldLines.size + newLines.size)
        var i = oldLines.size
        var j = newLines.size
        var added = 0
        var removed = 0
        var unchanged = 0
        while (i > 0 && j > 0) {
            if (oldLines[i - 1] == newLines[j - 1]) {
                out.add(WuwaDiffLine(WuwaDiffKind.CONTEXT, i, j, oldLines[i - 1]))
                unchanged++
                i--
                j--
            } else if (lcs[i][j - 1] >= lcs[i - 1][j]) {
                out.add(WuwaDiffLine(WuwaDiffKind.ADDED, null, j, newLines[j - 1]))
                added++
                j--
            } else {
                out.add(WuwaDiffLine(WuwaDiffKind.REMOVED, i, null, oldLines[i - 1]))
                removed++
                i--
            }
        }
        while (i > 0) {
            out.add(WuwaDiffLine(WuwaDiffKind.REMOVED, i, null, oldLines[i - 1]))
            removed++
            i--
        }
        while (j > 0) {
            out.add(WuwaDiffLine(WuwaDiffKind.ADDED, null, j, newLines[j - 1]))
            added++
            j--
        }
        out.reverse()
        return WuwaDiffResult(out, WuwaDiffSummary(added, removed, unchanged))
    }

    private fun lcsTable(a: Array<String>, b: Array<String>): Array<IntArray> {
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 1..a.size) {
            for (j in 1..b.size) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1] + 1 else maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
        return dp
    }
}
