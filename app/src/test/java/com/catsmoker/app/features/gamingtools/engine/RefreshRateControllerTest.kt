package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for refresh-rate ownership (Phase 2 / F3).
 *
 * One authoritative controller for `min_refresh_rate` / peak behaviour.
 * Both Gaming Mode and Developer Options delegate here — never write the key directly.
 * Objective is always the panel's maximum, never a lower ceiling.
 */
class RefreshRateControllerTest {

    @Test
    fun atPeakWithinTolerance() {
        assertTrue(RefreshRateController.isAtPeak(120f, 120f))
        assertTrue(RefreshRateController.isAtPeak(119.8f, 120f))
        assertFalse(RefreshRateController.isAtPeak(60f, 120f))
        assertFalse(RefreshRateController.isAtPeak(0f, 120f))
    }

    @Test
    fun unsetReadsAsNoMinimum() {
        assertFalse(RefreshRateController.isAtPeak(0f, 120f))
    }
}
