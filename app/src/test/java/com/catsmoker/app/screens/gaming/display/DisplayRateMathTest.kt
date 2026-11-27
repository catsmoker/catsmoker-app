package com.catsmoker.app.screens.gaming.display

import com.catsmoker.app.platform.display.AttachedDisplay
import com.catsmoker.app.platform.display.DisplayMode
import com.catsmoker.app.platform.display.DisplayRateMath
import com.catsmoker.app.platform.display.FramePacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The display rules that decide what the card may offer.
 *
 * These are the answers that must never be guessed at runtime: which rate is on the list, which
 * mode a rate belongs to, and whether a request divides the panel evenly. A regression in any
 * of them would put a "120 Hz" chip on a 90 Hz panel, which is the exact failure this feature
 * exists to avoid.
 */
class DisplayRateMathTest {

    private fun mode(id: Int, w: Int, h: Int, hz: Float) = DisplayMode(id, w, h, hz)

    private val phone = listOf(
        mode(1, 1440, 3120, 60f),
        mode(2, 1440, 3120, 90f),
        mode(3, 1440, 3120, 120f),
        mode(4, 1080, 2340, 165f)
    )

    @Test
    fun distinctRatesCollapseStorageDuplicates() {
        val modes = phone + mode(9, 1440, 3120, 120f) + mode(10, 1440, 3120, 90.001f)
        assertEquals(listOf(60f, 90f, 120f, 165f), DisplayRateMath.distinctRates(modes))
    }

    @Test
    fun nearestRateRefusesRatherThanApproximating() {
        val rates = DisplayRateMath.distinctRates(phone)
        assertEquals(120f, DisplayRateMath.nearestRate(rates, 120f))
        // 144 is not on this panel; the answer is "no", not "165".
        assertNull(DisplayRateMath.nearestRate(rates, 144f))
    }

    @Test
    fun modeAtRatePrefersTheActiveResolution() {
        // The 120 Hz mode exists only at 1440x3120 here, so the 1080p panel group must not win.
        val chosen = DisplayRateMath.modeAtRate(phone, 120f, atWidth = 1440, atHeight = 3120)
        assertEquals(3, chosen?.id)
    }

    @Test
    fun modeAtRateFallsBackToTheLargestModeAtThatRate() {
        val modes = listOf(mode(1, 1280, 720, 240f), mode(2, 1920, 1080, 240f))
        val chosen = DisplayRateMath.modeAtRate(modes, 240f, atWidth = 3840, atHeight = 2160)
        assertEquals(2, chosen?.id)
    }

    @Test
    fun modeAtRateReturnsNullForAnAbsentRate() {
        assertNull(DisplayRateMath.modeAtRate(phone, 144f))
    }

    @Test
    fun pacingDistinguishesEvenUnevenAndImpossible() {
        assertEquals(FramePacing.EVEN, DisplayRateMath.pacing(panelHz = 120f, requestHz = 60f))
        assertEquals(FramePacing.EVEN, DisplayRateMath.pacing(panelHz = 240f, requestHz = 80f))
        assertEquals(FramePacing.UNEVEN, DisplayRateMath.pacing(panelHz = 144f, requestHz = 120f))
        assertEquals(FramePacing.EXCEEDS, DisplayRateMath.pacing(panelHz = 60f, requestHz = 120f))
    }

    @Test
    fun peakAtActiveResolutionIgnoresABiggerModeAtAnotherResolution() {
        val display = AttachedDisplay(
            id = 0,
            name = "Built-in",
            isDefault = true,
            isExternal = false,
            state = 2,
            modes = phone,
            activeMode = mode(1, 1440, 3120, 60f)
        )
        // 165 Hz exists, but only at 1080x2340 — at the active 1440p resolution the peak is 120.
        assertEquals(120f, DisplayRateMath.peakRateAtActiveResolution(display))
    }

    // ------------------------------------------------------------------ planner

    private val external = AttachedDisplay(
        id = 2,
        name = "Monitor",
        isDefault = false,
        isExternal = true,
        state = 2,
        modes = listOf(mode(11, 1920, 1080, 60f), mode(12, 1920, 1080, 120f)),
        activeMode = mode(11, 1920, 1080, 60f)
    )

    @Test
    fun plannerRefusesARateTheDisplayDoesNotReport() {
        val plan = DisplayPlanner.plan(
            target = external,
            rateHz = 144f,
            probe = DisplayProbe(modeSpecs = true),
            canWriteSettings = true,
            isDeXCapable = false
        )
        assertTrue(plan is DisplayPlan.Refused)
    }

    @Test
    fun plannerUsesTheComposerWhenTheComposerIsAvailable() {
        val plan = DisplayPlanner.plan(
            target = external,
            rateHz = 120f,
            probe = DisplayProbe(modeSpecs = true),
            canWriteSettings = true,
            isDeXCapable = false
        )
        assertTrue(plan is DisplayPlan.Composer)
        assertEquals(12, (plan as DisplayPlan.Composer).mode.id)
        assertTrue(plan.switchesMode)
    }

    @Test
    fun plannerPrefersDeXOnASamsungWithTheDeXSwitch() {
        val plan = DisplayPlanner.plan(
            target = external,
            rateHz = 120f,
            probe = DisplayProbe(modeSpecs = true, dexHighResolution = true),
            canWriteSettings = true,
            isDeXCapable = true
        )
        assertTrue(plan is DisplayPlan.DeX)
    }

    @Test
    fun plannerFallsBackToSettingsWithoutPrivilegedInterfaces() {
        val plan = DisplayPlanner.plan(
            target = external,
            rateHz = 120f,
            probe = DisplayProbe(),
            canWriteSettings = true,
            isDeXCapable = false
        )
        assertTrue(plan is DisplayPlan.Settings)
    }

    @Test
    fun plannerRefusesWhenNothingCanWrite() {
        val plan = DisplayPlanner.plan(
            target = external,
            rateHz = 120f,
            probe = DisplayProbe(),
            canWriteSettings = false,
            isDeXCapable = false
        )
        assertTrue(plan is DisplayPlan.Refused)
    }

    @Test
    fun formatHzDropsPhantomPrecisionButKeepsRealFractions() {
        assertEquals("120", DisplayPlanner.formatHz(120f))
        assertEquals("119.88", DisplayPlanner.formatHz(119.88f))
        assertEquals("59.94", DisplayPlanner.formatHz(59.94f))
    }
}
