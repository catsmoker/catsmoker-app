package com.catsmoker.app.shared.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colour a log line is drawn in, chosen from the line's own text.
 *
 * Logcat's severity tags are matched first, then the words this app's own shell output uses, so a
 * device log and our progress lines colour by the same rules. Every branch is full-opacity and
 * bright enough for the near-black terminal box the lines are drawn on — a dimmed default turned
 * out grey-on-grey and unreadable, so an unclaimed line is drawn near-white instead of being
 * given a severity it never claimed.
 */
/**
 * The solid near-black behind every log terminal box. It used to be translucent black, so in the
 * light theme the surface showed through and the pale log colours washed out against it; a solid
 * fill keeps the contrast the palette above was picked for in both themes.
 */
val LogTerminalBackground = Color(0xFF15181C)

fun logLineColor(line: String): Color {
    val upperLine = line.uppercase()
    return when {
        // Logcat tags
        line.contains(" E/") || upperLine.contains("ERROR") || upperLine.contains("FAILED") -> Color(0xFFFF6B6B)
        line.contains(" W/") || upperLine.contains("WARN") || upperLine.contains("WARNING") -> Color(0xFFFFD740)
        line.contains(" I/") || upperLine.contains("INFO") -> Color(0xFF40C4FF)
        line.contains(" D/") || upperLine.contains("DEBUG") -> Color(0xFFCFD8DC)
        upperLine.contains("SUCCESS") || upperLine.contains("COMPLETE") -> Color(0xFF4ADE80)
        upperLine.contains("STARTING") -> Color(0xFFE1F5FE)
        else -> Color(0xFFF5F5F5)
    }
}
