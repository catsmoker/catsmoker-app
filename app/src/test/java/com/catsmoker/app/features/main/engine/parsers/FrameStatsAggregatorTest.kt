package com.catsmoker.app.features.main.engine.parsers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the frame-aggregation pattern against
 * `reference/debug-overlay/.../FrameStatsProcessor.kt` (read before this test was written):
 * a bounded, throttled window turning per-second samples into totals and ratios.
 *
 * Ported pattern, not channel: the reference aggregates per-frame callbacks with durations,
 * overruns and state breakdowns the app cannot see from its poll loop — what IS portable is
 * the windowed aggregation of the signals the app does have (per-second fps + the timestats
 * missed count from the same dump), producing the average and the missed-frame share.
 */
class FrameStatsAggregatorTest {

    @Test
    fun emptyWindowHasNoReadings() {
        val agg = FrameStatsAggregator().snapshot()
        assertEquals(0, agg.samples)
        assertNull(agg.avgFps)
        assertNull(agg.jankPercent)
    }

    @Test
    fun averagesFpsOverSamples() {
        val agg = FrameStatsAggregator()
        agg.push(60, 0)
        agg.push(30, 0)
        val snap = agg.snapshot()
        assertEquals(2, snap.samples)
        assertEquals(45f, snap.avgFps)
        assertEquals(0f, snap.jankPercent)
    }

    @Test
    fun jankPercentIsMissedOverPresentedPlusMissed() {
        val agg = FrameStatsAggregator()
        agg.push(60, 6)
        agg.push(60, 0)
        val snap = agg.snapshot()
        // 6 missed of (120 presented + 6 missed).
        assertEquals(6f / 126f * 100f, snap.jankPercent!!, 1e-4f)
    }

    @Test
    fun samplesWithoutMissedCountsDoNotInventJank() {
        // The Choreographer fallback has no missed signal: its frames join the average but
        // never the jank ratio — a 0 there would assert something unmeasured.
        val agg = FrameStatsAggregator()
        agg.push(60, null)
        val snap = agg.snapshot()
        assertEquals(60f, snap.avgFps)
        assertNull(snap.jankPercent)
    }

    @Test
    fun windowIsBounded() {
        val agg = FrameStatsAggregator(maxSamples = 3)
        agg.push(60, 0)
        agg.push(60, 0)
        agg.push(30, 0)
        agg.push(30, 0)
        val snap = agg.snapshot()
        assertEquals(3, snap.samples)
        assertEquals(40f, snap.avgFps)
    }
}
