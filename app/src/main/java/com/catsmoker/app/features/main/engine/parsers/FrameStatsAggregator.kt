package com.catsmoker.app.features.main.engine.parsers

/**
 * Windowed aggregation of per-second FPS samples, after the reference's `FrameStatsProcessor`
 * (`reference/debug-overlay/.../FrameStatsProcessor.kt`, read before this file was written).
 *
 * Ported pattern, not channel: per-frame durations, overruns and state breakdowns need
 * per-frame callbacks the poll loop cannot see. What the loop does have — per-second fps
 * plus the timestats missed count from the same dump — aggregates here into the average
 * and the missed-frame share over a bounded window, which is also what smooths the
 * sparkline series without inventing samples.
 *
 * A sample without a missed count (the Choreographer fallback has no such signal) joins
 * the frame average but never the jank ratio: a 0 there would assert something unmeasured.
 */
data class FrameAggregation(
    val samples: Int,
    val totalFrames: Long,
    val missedFrames: Long,
    val avgFps: Float?,
    /** Missed / (presented + missed) over the samples that carried a missed count, or null. */
    val jankPercent: Float?,
)

class FrameStatsAggregator(private val maxSamples: Int = 60) {

    private val samples = ArrayDeque<Pair<Int, Int?>>()

    fun push(fps: Int, missed: Int?): FrameAggregation {
        samples.addLast(fps to missed)
        while (samples.size > maxSamples) samples.removeFirst()
        return snapshot()
    }

    fun snapshot(): FrameAggregation {
        if (samples.isEmpty()) {
            return FrameAggregation(0, 0L, 0L, null, null)
        }
        var total = 0L
        var missedTotal = 0L
        var jankBase = 0L
        for ((fps, missed) in samples) {
            total += fps
            if (missed != null) {
                missedTotal += missed
                jankBase += fps
            }
        }
        val jankPercent = if (jankBase > 0 && missedTotal >= 0 && (jankBase + missedTotal) > 0) {
            missedTotal.toFloat() / (jankBase + missedTotal).toFloat() * 100f
        } else {
            null
        }
        return FrameAggregation(
            samples = samples.size,
            totalFrames = total,
            missedFrames = missedTotal,
            avgFps = total.toFloat() / samples.size.toFloat(),
            jankPercent = jankPercent,
        )
    }
}
