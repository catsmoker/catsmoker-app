package com.catsmoker.app.features.gamingtools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour lock for Gaming Mode's job ownership (2026-10-28).
 *
 * Activation writes system settings for about a minute and a half. It used to run on the
 * ViewModel's own scope, so a phone that closed the activity mid-activation — screen off, or an
 * OEM that finishes activities aggressively — cancelled the job, and the engine reported that
 * cancellation as "Activation failed" while some levers were already written. The toggle now
 * belongs to an app-scoped runner that no screen can cancel; this test fails if an entry point
 * drifts back onto a scope that dies with the screen.
 */
class GamingModeJobOwnershipTest {

    private val root = "src/main/java/com/catsmoker/app/features/gamingtools"
    private val viewModel = File(root, "GamingToolsViewModel.kt").readText()
    private val runner = File(root, "engine/GamingModeJobRunner.kt")

    /** The two card entry points, from the activation function to the next unrelated action. */
    private val toggles: String =
        viewModel.substringAfter("fun activateGamingMode").substringBefore("fun restoreSuspendedApps")

    @Test
    fun activationAndRevertDoNotRunOnTheScreensScope() {
        assertFalse(
            "a toggle still runs on viewModelScope, so closing the screen cancels it",
            toggles.contains("viewModelScope")
        )
        assertTrue("activation does not go through the app-scoped runner", toggles.contains("gamingModeJobs.activate()"))
        assertTrue("revert does not go through the app-scoped runner", toggles.contains("gamingModeJobs.deactivate()"))
    }

    @Test
    fun theRunnerOwnsAScopeNoCallerCanCancel() {
        assertTrue("GamingModeJobRunner.kt is missing", runner.isFile)
        val text = runner.readText()
        assertTrue("the runner must be one per app process", text.contains("@Singleton"))
        assertTrue("the runner's job must not be a child of a screen", text.contains("SupervisorJob()"))
        assertFalse("the runner must not reach for a ViewModel scope", text.contains("viewModelScope"))
    }

    @Test
    fun theHoldingServiceStillStartsOnlyWhenTheEngineIsActive() {
        // The original failure this guard prevents: an "active" notification over an idle engine.
        val text = runner.readText()
        assertTrue(
            "the foreground service must follow the engine, not the request",
            text.contains("GamingModeState.Active")
        )
    }
}
