package com.catsmoker.app.features.gamingtools.ui

import com.catsmoker.app.features.gamingtools.engine.GamingModeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The progress bar is an operation indicator, not a permanent line on the card.
 *
 * Forward progress is the engine's own read-back sequence, so [GamingModeProgress.progressFor]
 * must pass those values through unchanged. Disabling has no per-step progress in the engine, so
 * the bar starts at full and the composable animates it back to [GamingModeProgress.REVERSE_END].
 */
class GamingModeProgressTest {

    @Test
    fun idleHidesTheBar() {
        assertFalse(GamingModeProgress.isVisible(GamingModeState.Idle))
        assertNull(GamingModeProgress.progressFor(GamingModeState.Idle))
    }

    @Test
    fun activeModeHidesTheBar() {
        assertFalse(GamingModeProgress.isVisible(GamingModeState.Active))
        assertNull(GamingModeProgress.progressFor(GamingModeState.Active))
    }

    @Test
    fun failedOperationHidesTheBar() {
        assertFalse(GamingModeProgress.isVisible(GamingModeState.Error("refused")))
        assertNull(GamingModeProgress.progressFor(GamingModeState.Error("refused")))
    }

    @Test
    fun enablingShowsTheEngineProgressUnchanged() {
        val state = GamingModeState.Enabling(progress = 0.35f, statusText = "Suspending")

        assertTrue(GamingModeProgress.isVisible(state))
        assertEquals(0.35f, GamingModeProgress.progressFor(state)!!, 0f)
    }

    @Test
    fun enablingProgressIsClampedToTheBarRange() {
        assertEquals(
            1f,
            GamingModeProgress.progressFor(GamingModeState.Enabling(progress = 1.4f))!!,
            0f
        )
        assertEquals(
            0f,
            GamingModeProgress.progressFor(GamingModeState.Enabling(progress = -0.2f))!!,
            0f
        )
    }

    @Test
    fun disablingStartsAtFullProgress() {
        assertTrue(GamingModeProgress.isVisible(GamingModeState.Disabling))
        assertEquals(
            GamingModeProgress.REVERSE_START,
            GamingModeProgress.progressFor(GamingModeState.Disabling)!!,
            0f
        )
    }

    @Test
    fun reverseAnimationEndsAtZeroAndTakesTime() {
        assertEquals(0f, GamingModeProgress.REVERSE_END, 0f)
        assertTrue(GamingModeProgress.REVERSE_START > GamingModeProgress.REVERSE_END)
        assertTrue(GamingModeProgress.REVERSE_DURATION_MS > 0)
    }
}
