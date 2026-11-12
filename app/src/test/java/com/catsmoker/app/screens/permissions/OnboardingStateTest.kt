package com.catsmoker.app.screens.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {
    @Test
    fun analyticsRequiresAnExplicitSelection() {
        assertFalse(OnboardingState().analytics)
    }

    @Test
    fun termsRemainRequiredWithEitherAnalyticsChoice() {
        listOf(false, true).forEach { analytics ->
            val state = OnboardingState(step = OnboardingState.Step.TERMS, analytics = analytics)
            assertEquals(state, state.continueTerms())
            assertFalse(state.canFinish())
        }
    }

    @Test
    fun continuingAppliesEitherSwitchChoiceWithoutASecondConfirmation() {
        listOf(false, true).forEach { analytics ->
            val state = OnboardingState(step = OnboardingState.Step.TERMS, agreed = true, analytics = analytics)
            val next = state.continueTerms()
            assertEquals(OnboardingState.Step.PERMISSIONS, next.step)
            assertEquals(analytics, next.analytics)
            assertTrue(next.canFinish())
        }
    }

    @Test
    fun termsCannotBeSkippedFromWelcome() {
        val state = OnboardingState(agreed = true, analytics = true)
        assertEquals(state, state.continueTerms())
        assertFalse(state.canFinish())
    }

    @Test
    fun returningToWelcomeRetainsChoices() {
        val state = OnboardingState(
            step = OnboardingState.Step.PERMISSIONS, agreed = true, analytics = true,
            inventory = false, usage = false, notifications = false,
        )
        assertEquals(state.copy(step = OnboardingState.Step.WELCOME), state.back().back())
        assertEquals(state, state.back().back().continueWelcome().continueTerms())
    }
}
