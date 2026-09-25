package com.catsmoker.app.features.main.engine.parsers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behaviour lock for 1% / 0.1% low FPS.
 *
 * An average hides stutter: a game at 60 FPS with regular hitches still averages 60. The lows
 * average the worst 1% (and worst 0.1%) of per-second samples, so hitches move the number.
 * Each percentile averages at least one sample (rounding up), and an empty history yields no
 * reading at all — never a 0 no device reported.
 */
class FrameLowsTest {

    @Test
    fun emptyHistoryHasNoLows() {
        assertNull(FrameLows.lows(emptyList()))
    }

    @Test
    fun singleSampleIsBothLows() {
        val lows = FrameLows.lows(listOf(60))!!
        assertEquals(60, lows.low1)
        assertEquals(60, lows.low01)
    }

    @Test
    fun worstSamplesDragTheLowsBelowTheAverage() {
        // 99 good seconds and one hitch: the average rounds to 60, the lows do not.
        val history = List(99) { 60 } + 20
        val lows = FrameLows.lows(history)!!
        assertEquals(20, lows.low1)
        assertEquals(20, lows.low01)
    }

    @Test
    fun percentilesAverageTheirWholeWindow() {
        // 100 samples 1..100: worst 1% is {1}, worst 0.1% rounds up to {1} too.
        val history = (1..100).toList()
        val lows = FrameLows.lows(history)!!
        assertEquals(1, lows.low1)
        assertEquals(1, lows.low01)
    }

    @Test
    fun tenthPercentWidensPastOneSampleOnLongHistories() {
        // 2000 samples: worst 1% is {1..20}, avg 10; worst 0.1% is {1,2}, avg 1.
        val history = (1..2000).toList()
        val lows = FrameLows.lows(history)!!
        assertEquals(10, lows.low1)
        assertEquals(1, lows.low01)
    }

    @Test
    fun ignoresNonPositiveSamples() {
        // A 0 is a missed poll, not a second of zero frames — averaging it in would invent a
        // freeze the device never reported.
        val lows = FrameLows.lows(listOf(60, 60, 0, -1))!!
        assertEquals(60, lows.low1)
        assertEquals(60, lows.low01)
    }

    @Test
    fun allNonPositiveMeansNoReading() {
        assertNull(FrameLows.lows(listOf(0, 0)))
    }
}
