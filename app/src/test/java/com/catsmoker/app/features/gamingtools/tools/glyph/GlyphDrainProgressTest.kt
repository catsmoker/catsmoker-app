package com.catsmoker.app.features.gamingtools.tools.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shutdown drain's ramp.
 *
 * The reverse animation is the one part of the Glyph mirror that cannot be observed in a unit
 * test (there is no LED strip on the JVM), so its arithmetic is kept pure and pinned here: a ramp
 * that lingered at 100 % would leave the strip lit after Gaming Mode was already off, and one
 * that jumped to 0 would look exactly like the abrupt wipe the reverse exists to replace.
 */
class GlyphDrainProgressTest {

    @Test
    fun startsFullyLit() {
        assertEquals(100, drainedPercent(0L, 1_500L))
    }

    @Test
    fun halfwayIsHalfLit() {
        assertEquals(50, drainedPercent(750L, 1_500L))
    }

    @Test
    fun endsDark() {
        assertEquals(0, drainedPercent(1_500L, 1_500L))
    }

    @Test
    fun neverGoesBelowZeroAfterTheEnd() {
        // A late tick (busy main thread) must clamp, not hand the GDK a negative value: the SDK
        // multiplies the percentage into a brightness ladder and does not clamp it itself.
        assertEquals(0, drainedPercent(1_900L, 1_500L))
        assertEquals(0, drainedPercent(Long.MAX_VALUE, 1_500L))
    }

    @Test
    fun aMissingDurationCannotDivideByZero() {
        assertEquals(0, drainedPercent(0L, 0L))
        assertEquals(0, drainedPercent(500L, -1L))
    }

    @Test
    fun rampIsMonotonicAndStaysInRange() {
        // Sampled at the controller's own 60 ms tick, which is how the strip is actually driven.
        var previous = 101
        var elapsed = 0L
        while (elapsed <= 1_500L) {
            val value = drainedPercent(elapsed, 1_500L)
            assertTrue("$elapsed ms produced $value", value in 0..100)
            assertTrue("$elapsed ms went up ($previous → $value)", value <= previous)
            previous = value
            elapsed += 60L
        }
        assertEquals(0, previous)
    }
}
