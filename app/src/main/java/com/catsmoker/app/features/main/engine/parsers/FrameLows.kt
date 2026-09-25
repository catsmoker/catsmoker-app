package com.catsmoker.app.features.main.engine.parsers

import kotlin.math.ceil

/**
 * 1% and 0.1% low FPS from per-second frame samples.
 *
 * Computed over whatever history the caller holds (the engine keeps 60 samples): the samples
 * sort ascending and each percentile averages its whole window, rounding the window size up so
 * it always covers at least one sample. Non-positive samples are missed polls, not frozen
 * seconds, and are excluded — when nothing positive remains there is no reading, not a 0.
 */
object FrameLows {

    /** Average FPS of the worst 1% and worst 0.1% of samples. */
    data class Lows(val low1: Int, val low01: Int)

    fun lows(fpsHistory: List<Int>): Lows? {
        val samples = fpsHistory.filter { it > 0 }.sorted()
        if (samples.isEmpty()) return null
        return Lows(
            low1 = averageOfWorst(samples, 0.01),
            low01 = averageOfWorst(samples, 0.001)
        )
    }

    private fun averageOfWorst(sorted: List<Int>, fraction: Double): Int {
        val window = ceil(sorted.size * fraction).toInt().coerceAtLeast(1).coerceAtMost(sorted.size)
        return sorted.take(window).average().toInt()
    }
}
