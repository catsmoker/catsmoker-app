package com.catsmoker.app.features.main.engine.parsers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the vsync-fallback spike guard against
 * `reference/debug-overlay/.../FpsCalculator.kt` (read before this test was written): the
 * fallback counts wall-clock frames, so a burst of callbacks can report a rate no panel can
 * display — the count is coerced into `[0, maxFps]`, where maxFps is the panel's measured
 * maximum, never a hardcoded ceiling.
 */
class FpsFallbackTest {

    @Test
    fun normalCountsPassThrough() {
        assertEquals(60, clampFpsSample(60, 120f))
        assertEquals(120, clampFpsSample(120, 120f))
    }

    @Test
    fun spikesClampToThePanelMax() {
        assertEquals(120, clampFpsSample(1000, 120f))
        assertEquals(60, clampFpsSample(61, 60f))
    }

    @Test
    fun negativesClampToZero() {
        assertEquals(0, clampFpsSample(-3, 120f))
    }

    @Test
    fun unknownPanelMaxLeavesTheCountAlone() {
        // No panel facts, no clamp: inventing a ceiling would be a cap by another name.
        assertEquals(1000, clampFpsSample(1000, null))
        assertEquals(1000, clampFpsSample(1000, 0f))
        assertEquals(0, clampFpsSample(-3, null))
    }
}
