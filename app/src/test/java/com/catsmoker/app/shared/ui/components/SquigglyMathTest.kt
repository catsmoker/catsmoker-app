package com.catsmoker.app.shared.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the Android 13 (AOSP SystemUI `SquigglyProgress`) math the squiggly bar
 * port relies on: the wave-endpoint stretch that keeps a visible squiggle even
 * near zero progress, and the tapered amplitude envelope around the progress
 * head. Reference: `platform/frameworks/base/.../SquigglyProgress.kt`
 * (`transitionPeriods=1.5`, `minWaveEndpoint=0.2`, `matchedWaveEndpoint=0.6`).
 */
class SquigglyMathTest {

    @Test
    fun waveEndpointStretchesSmallProgressSoSquiggleStaysVisible() {
        // progress=0 still shows a 20%-wide wave, exactly like AOSP.
        assertEquals(0.2f, SquigglyMath.waveEndpoint(0f), 1e-6f)
        // midway through the matched zone lerps linearly: 0.3 -> 0.4.
        assertEquals(0.4f, SquigglyMath.waveEndpoint(0.3f), 1e-6f)
        // at/above the matched endpoint the wave follows progress exactly.
        assertEquals(0.6f, SquigglyMath.waveEndpoint(0.6f), 1e-6f)
        assertEquals(1f, SquigglyMath.waveEndpoint(1f), 1e-6f)
    }

    @Test
    fun waveEndpointWithoutTransitionFollowsProgressExactly() {
        assertEquals(0f, SquigglyMath.waveEndpoint(0f, transitionEnabled = false), 1e-6f)
        assertEquals(0.3f, SquigglyMath.waveEndpoint(0.3f, transitionEnabled = false), 1e-6f)
        assertEquals(1f, SquigglyMath.waveEndpoint(1f, transitionEnabled = false), 1e-6f)
    }

    @Test
    fun amplitudeIsFullBehindHeadAndZeroAheadOfTransition() {
        val waveLength = 20f
        // progress=1 -> wave end at full width; transition spans 1.5 waves (30px).
        val waveEndPx = 1000f * SquigglyMath.waveEndpoint(1f)
        // Deep behind the head: full amplitude.
        assertEquals(1f, SquigglyMath.amplitudeCoeff(x = 100f, waveEndPx = waveEndPx, waveLength = waveLength), 1e-6f)
        // At the head centre the envelope is half faded (AOSP tapers across the head).
        assertEquals(0.5f, SquigglyMath.amplitudeCoeff(x = waveEndPx, waveEndPx = waveEndPx, waveLength = waveLength), 1e-6f)
        // Beyond the transition (1.5 waves past the head): zero amplitude.
        assertEquals(0f, SquigglyMath.amplitudeCoeff(x = waveEndPx + 30f, waveEndPx = waveEndPx, waveLength = waveLength), 1e-6f)
    }

    @Test
    fun lerpHelpersMatchAospMathUtils() {
        assertEquals(0.4f, SquigglyMath.lerp(0.2f, 0.6f, 0.5f), 1e-6f)
        assertEquals(0.5f, SquigglyMath.lerpInv(0f, 0.6f, 0.3f), 1e-6f)
        // Saturated: clamps outside the range instead of extrapolating.
        assertEquals(0f, SquigglyMath.lerpInvSat(0f, 10f, -5f), 1e-6f)
        assertEquals(1f, SquigglyMath.lerpInvSat(0f, 10f, 99f), 1e-6f)
        // Reversed edges (a > b) taper downward, as used by the wave envelope.
        assertEquals(1f, SquigglyMath.lerpInvSat(100f, 70f, 60f), 1e-6f)
        assertEquals(0f, SquigglyMath.lerpInvSat(100f, 70f, 110f), 1e-6f)
    }
}
