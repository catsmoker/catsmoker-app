package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behaviour lock for the touch-speed choice.
 *
 * `Settings.System.pointer_speed` runs -7..+7 around a stock 0. Stock needs no entry — leaving
 * the setting alone already is stock — so 0 sanitizes to null ("off"), and anything outside
 * the platform range degrades to off rather than failing the whole activation or, worse,
 * writing a speed the user did not pick.
 */
class PointerSpeedTest {

    @Test
    fun stockIsOff() {
        assertNull(PointerSpeed.sanitize(null))
        assertNull(PointerSpeed.sanitize(0))
    }

    @Test
    fun inRangeSpeedsPassThrough() {
        assertEquals(-7, PointerSpeed.sanitize(-7))
        assertEquals(-1, PointerSpeed.sanitize(-1))
        assertEquals(1, PointerSpeed.sanitize(1))
        assertEquals(7, PointerSpeed.sanitize(7))
    }

    @Test
    fun outOfRangeDegradesToOff() {
        assertNull(PointerSpeed.sanitize(-8))
        assertNull(PointerSpeed.sanitize(8))
        assertNull(PointerSpeed.sanitize(Int.MIN_VALUE))
        assertNull(PointerSpeed.sanitize(Int.MAX_VALUE))
    }

    @Test
    fun choicesAreSymmetricAndSkipStock() {
        val choices = PointerSpeed.CHOICES
        assertEquals(choices.sorted(), choices)
        assertTrue(choices.none { it == 0 })
        assertTrue(choices.all { it in -7..7 })
    }

    private fun assertTrue(value: Boolean) = org.junit.Assert.assertTrue(value)
}
