package com.catsmoker.app.features.gamingtools.engine

/**
 * Whole-Hz summary of a panel's refresh rates for display.
 *
 * `Display.Mode` repeats rates across resolutions and carries near-duplicates from frame-timing
 * math (59.94 vs 60.0), so the raw list is rounded, de-duplicated and sorted highest-first.
 * Non-finite and non-positive entries are dropped; an empty or garbage input yields an empty
 * list, which callers report as unreadable — never a 60 Hz the panel never claimed.
 */
object PanelRefreshSummary {

    fun summarizeRates(rates: List<Float>): List<Int> =
        rates.filter { it.isFinite() && it > 0f }
            .map { Math.round(it) }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
}
