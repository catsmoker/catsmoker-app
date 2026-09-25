package com.catsmoker.app.features.main.engine.parsers

import com.catsmoker.app.shared.data.model.MetricReadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behaviour lock for the `PowerManager.getThermalHeadroom` reading.
 *
 * The platform documents three outcomes: a non-negative float (1.0 = the SEVERE throttle
 * threshold, values above 1.0 allowed), NaN when the HAL exposes nothing or the call came too
 * soon after the previous one, and an exception on API < 30 or a dead service. NaN must never
 * become a displayed number, and an out-of-range value must never be rescaled into one.
 */
class ThermalHeadroomTest {

    @Test
    fun nanMeansUnsupported() {
        val reading = ThermalHeadroom.classify(Float.NaN)
        assertNull(reading.headroom)
        assertEquals(MetricReadStatus.Unsupported, reading.status)
    }

    @Test
    fun zeroIsARealReading() {
        val reading = ThermalHeadroom.classify(0.0f)
        assertEquals(0.0f, reading.headroom)
        assertEquals(MetricReadStatus.Ok, reading.status)
    }

    @Test
    fun midRangeHeadroomIsOk() {
        val reading = ThermalHeadroom.classify(0.65f)
        assertEquals(0.65f, reading.headroom)
        assertEquals(MetricReadStatus.Ok, reading.status)
    }

    @Test
    fun aboveSevereThresholdIsStillARealReading() {
        // The platform allows values past 1.0 (heavier than SEVERE); clamping would invent data.
        val reading = ThermalHeadroom.classify(1.5f)
        assertEquals(1.5f, reading.headroom)
        assertEquals(MetricReadStatus.Ok, reading.status)
    }

    @Test
    fun negativeIsParseFailed() {
        val reading = ThermalHeadroom.classify(-0.5f)
        assertNull(reading.headroom)
        assertEquals(MetricReadStatus.ParseFailed, reading.status)
    }

    @Test
    fun infiniteIsParseFailed() {
        val reading = ThermalHeadroom.classify(Float.POSITIVE_INFINITY)
        assertNull(reading.headroom)
        assertEquals(MetricReadStatus.ParseFailed, reading.status)
    }
}
