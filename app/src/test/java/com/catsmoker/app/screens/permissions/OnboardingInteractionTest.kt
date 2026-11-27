package com.catsmoker.app.screens.permissions

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source contracts for Android wiring not exercised by the pure state tests. */
class OnboardingInteractionTest {
    private fun source(name: String): String = File(
        "src/main/java/com/catsmoker/app/screens/permissions/$name.kt"
    ).readText()

    @Test
    fun languageSelectionCommitsTheDraftAndLocaleBeforeRestartWithoutAdvancing() {
        val viewModel = source("PermissionViewModel")
        val selection = viewModel.substringAfter("fun onLanguageChanged(")
            .substringBefore("fun onAppearanceContinue(")
        val draft = selection.indexOf("saveDraft(commit = true)")
        val locale = selection.indexOf("AppearanceStore.setLanguage(context, normalized)")
        val restart = selection.indexOf("_events.tryEmit(UiEvent.RestartApp)")
        assertTrue(draft >= 0 && locale > draft && restart > locale)
        assertTrue(selection.contains("val previous = LocaleHelper.normalizeTag(AppearanceStore.languageTag(context))"))
        assertTrue(selection.contains("if (normalized == previous) return"))
        val storedNoOp = selection.indexOf("if (normalized == previous) return")
        val previousLanguage = selection.indexOf("val previousLanguage = LocaleHelper.effectiveLanguageTag(context, previous)")
        val selectedLanguage = selection.indexOf("val selectedLanguage = LocaleHelper.effectiveLanguageTag(context, normalized)")
        val update = selection.indexOf("_uiState.update")
        assertTrue(storedNoOp >= 0 && update > storedNoOp && draft > update)
        assertTrue(previousLanguage > storedNoOp && selectedLanguage > previousLanguage && update > selectedLanguage)
        val restartGuard = selection.indexOf("if (previousLanguage != selectedLanguage) {")
        assertTrue(restartGuard > locale && restart > restartGuard)
        assertFalse(selection.contains("if (normalized == LocaleHelper.effectiveLanguageTag(context)) return"))
        assertFalse(selection.contains("continueWelcome()"))
        assertFalse(selection.contains("setChosen(context)"))
        val continuing = viewModel.substringAfter("fun onAppearanceContinue(")
            .substringBefore("fun onAgreementContinue(")
        assertFalse(continuing.contains("UiEvent.RestartApp"))
        assertTrue(viewModel.contains("languageTag = AppearanceStore.languageTag(context)"))
    }

    @Test
    fun effectiveSystemLanguageDoesNotUseTheOverriddenProcessDefault() {
        val helper = File("src/main/java/com/catsmoker/app/application/appearance/LocaleHelper.kt").readText()
        val effective = helper.substringAfter("fun effectiveLanguageTag(")
            .substringBefore("fun wrap(")
        assertTrue(effective.contains("LocaleManagerCompat.getSystemLocales(context)"))
        assertFalse(effective.contains("Locale.getDefault()"))
    }

    @Test
    fun onlyWelcomeSuppressesTheBackArrowAndProgressRemainsIndependent() {
        assertTrue(source("AppearanceStepScreen").contains("showBack = false"))
        assertFalse(source("AgreementStepScreen").contains("showBack = false"))
        // Settings re-enters the permission checklist after onboarding; the back arrow only
        // belongs to the first-run flow that has a previous step.
        assertTrue(source("PermissionsListScreen").contains("showBack = uiState.isOnboarding"))
        val scaffold = source("OnboardingUi")
        assertTrue(scaffold.contains("showBack: Boolean = true"))
        assertTrue(scaffold.contains("if (showBack)"))
        assertTrue(scaffold.contains("else if (showBack)"))
    }

    @Test
    fun analyticsHasNoAutomaticPopupOrLegacyDefaultOnDraft() {
        val route = source("PermissionScreen")
        assertFalse(route.contains("confirmAnalytics"))
        assertFalse(route.contains("AlertDialog("))
        assertTrue(route.contains("onContinue = { viewModel.onAgreementContinue() }"))
        val vm = source("PermissionViewModel")
        assertTrue(vm.contains("if (accepted) AnalyticsConsent.isEnabled(context)"))
        assertTrue(vm.contains("AnalyticsConsent.acceptAgreement(context, current.choices.analytics)"))
        val draft = source("OnboardingDraftStore")
        assertTrue(draft.contains("getBoolean(\"analytics_opt_in\", initial.analytics)"))
        assertFalse(draft.contains("getBoolean(\"analytics\","))
        val agreement = source("AgreementStepScreen")
        assertTrue(agreement.contains("InfoText.Res(R.string.onb_analytics_details)"))
        assertTrue(agreement.contains("if (analytics) R.string.onb_enable_analytics_continue"))
        assertTrue(agreement.contains("else R.string.onb_without_analytics_continue"))
    }

    @Test
    fun compactAccessTilesKeepDisclosuresAndSeparateAndroidRequests() {
        val screen = source("PermissionsListScreen")
        // 92 dp: the tiles are sized to fill the screen they live on (the step fits, so the space
        // they take is the space available), and their two rows are centred inside rather than
        // pinned top and bottom, which is what made a taller tile look hollow.
        assertTrue(screen.contains("PermissionGrid(specs = essential, tileHeight = 92.dp)"))
        // System and Additional are stacked one per row and drawn side by side: on a phone that is
        // what makes the whole step fit without scrolling. Two full-width grids do not.
        listOf("system", "additional").forEach {
            assertTrue(screen.contains("PermissionGrid(specs = $it, tileHeight = 92.dp, columns = 1)"))
        }
        assertTrue(screen.contains("Row(horizontalArrangement = Arrangement.spacedBy(8.dp))"))
        // No overscroll stretch: with the step fitted to one screen, the rubber band was the last
        // thing that still looked like scrolling.
        assertTrue(screen.contains("overscrollEffect = null"))
        // Tall tiles centre their two rows; a weight spacer between them was a hollow middle.
        assertTrue(screen.contains("verticalArrangement = Arrangement.Center"))
        // The three optional consents are one card of three rows, not three cards with a request
        // button band under each. The choices, gates and disclosures are unchanged.
        assertTrue(screen.contains("GamingAccessCard {"))
        // One declaration plus three call sites.
        assertEquals(3, screen.split("GamingAccessRow(").size - 2)
        listOf("policy_inventory", "policy_usage", "policy_notifications", "policy_access_intro").forEach {
            assertTrue(screen.contains("R.string.$it"))
        }
        assertTrue(screen.contains("InfoText.Res(explanationRes)"))
        assertTrue(screen.contains("Settings.ACTION_USAGE_ACCESS_SETTINGS"))
        assertTrue(screen.contains("Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        assertTrue(screen.contains("status == GamingAccessStatus.ANDROID_REQUIRED && onRequest != null"))
        assertTrue(screen.contains("role = Role.Checkbox"))
    }

    @Test
    fun allLocalesContainTheNewOnboardingCopy() {
        listOf("values", "values-en-rGB", "values-ar", "values-es", "values-ja", "values-pt-rBR", "values-zh-rCN").forEach { locale ->
            val copy = File("src/main/res/$locale/strings_onboarding.xml").readText()
            listOf("onb_analytics_details", "onb_analytics_explanation", "onb_access_summary", "onb_access_pending_save").forEach { key ->
                assertTrue("$locale missing $key", copy.contains("name=\"$key\""))
            }
        }
    }
}
