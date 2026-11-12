package com.catsmoker.app.platform

import com.catsmoker.app.system.SupportPromptState
import com.catsmoker.app.system.onSupportDialogShown
import com.catsmoker.app.system.shouldShowSupportDialog
import com.catsmoker.app.system.showNeverAskOption
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The support dialog shows every 5th launch, and from its second showing offers a
 * "Do not show again" checkbox. Pure policy — the Activity only persists the state
 * (`support_shown_count`, `support_never_ask`) in `app_prefs` next to `app_launch_count`.
 */
class SupportPromptTest {

    @Test
    fun showsOnEveryFifthLaunch() {
        val fresh = SupportPromptState()
        assertTrue(shouldShowSupportDialog(5, fresh))
        assertTrue(shouldShowSupportDialog(10, fresh))
    }

    @Test
    fun hidesOnOtherLaunches() {
        val fresh = SupportPromptState()
        assertFalse(shouldShowSupportDialog(0, fresh))
        assertFalse(shouldShowSupportDialog(1, fresh))
        assertFalse(shouldShowSupportDialog(4, fresh))
        assertFalse(shouldShowSupportDialog(6, fresh))
    }

    @Test
    fun hidesForeverOnceNeverAskIsSet() {
        val optedOut = SupportPromptState(timesShown = 3, neverAskAgain = true)
        assertFalse(shouldShowSupportDialog(5, optedOut))
        assertFalse(shouldShowSupportDialog(10, optedOut))
    }

    @Test
    fun firstRunNeverShowsSupportEvenOnEligibleLaunchesAfterLanguageRestarts() {
        listOf(1, 5, 10, 15).forEach { launch ->
            assertFalse(shouldShowSupportDialog(launch, SupportPromptState(), isFirstRun = true))
            assertFalse(shouldShowSupportDialog(launch, SupportPromptState(timesShown = 2), isFirstRun = true))
        }
    }

    @Test
    fun completedOnboardingRetainsNormalSupportPromptPolicy() {
        assertTrue(shouldShowSupportDialog(5, SupportPromptState(), isFirstRun = false))
        assertFalse(shouldShowSupportDialog(6, SupportPromptState(), isFirstRun = false))
        assertFalse(shouldShowSupportDialog(5, SupportPromptState(neverAskAgain = true), isFirstRun = false))
    }

    @Test
    fun activityPassesTheFirstRunFlagToThePresentationPolicy() {
        val activity = File("src/main/java/com/catsmoker/app/system/MainActivity.kt").readText()
        assertTrue(activity.contains("shouldShowSupportDialog(count, supportPromptState(prefs), isFirstRun = isFirstRun())"))
    }

    @Test
    fun neverAskOptionHiddenOnFirstShowing() {
        assertFalse(showNeverAskOption(SupportPromptState()))
    }

    @Test
    fun neverAskOptionVisibleFromSecondShowingOn() {
        assertTrue(showNeverAskOption(SupportPromptState(timesShown = 1)))
        assertTrue(showNeverAskOption(SupportPromptState(timesShown = 4)))
    }

    @Test
    fun showingTheDialogCountsTowardTheSecondShowing() {
        val afterFirst = onSupportDialogShown(SupportPromptState())
        assertEquals(1, afterFirst.timesShown)
        assertTrue(showNeverAskOption(afterFirst))
    }
}
