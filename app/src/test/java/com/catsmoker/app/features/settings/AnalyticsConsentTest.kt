package com.catsmoker.app.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The analytics switch must be a control, not decoration.
 *
 * Firebase Analytics is bundled in both flavours and starts before app code
 * (`FirebaseInitProvider`), so a preference nothing reads is a false promise in Settings and a
 * false statement in the Privacy Policy. This lock ties the three surfaces together: the consent
 * owner that calls the SDK, the startup path that re-applies the stored choice, and the Settings
 * toggle that writes through it.
 *
 * Legal copy is checked in every shipped locale because the previous text ("No analytics SDK is
 * bundled") was translated five times — fixing only the English file would leave four languages
 * telling users the opposite of the truth.
 */
class AnalyticsConsentTest {

    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("missing source under test: $relative")
    }

    private fun legalBody(locale: String): String {
        val file = listOf(
            File("src/main/res/$locale/strings_legal.xml"),
            File("app/src/main/res/$locale/strings_legal.xml")
        ).firstOrNull { it.isFile } ?: error("missing strings_legal.xml for $locale")
        val match = Regex("""<string name="legal_p_analytics_body">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(file.readText())
        assertTrue("legal_p_analytics_body missing from $locale", match != null)
        return match!!.groupValues[1]
    }

    @Test
    fun theConsentOwnerDrivesTheBundledSdk() {
        val consent = source("src/main/java/com/catsmoker/app/system/config/AnalyticsConsent.kt")
        assertTrue(
            "the stored choice must reach Firebase",
            consent.contains("setAnalyticsCollectionEnabled")
        )
        assertTrue("the key the Settings screen already shipped stays the key", consent.contains("analytics_enabled"))
        assertEquals(
            "Settings and the SDK must agree on the default",
            true,
            consent.contains("DEFAULT_ENABLED = true")
        )
    }

    @Test
    fun startupReappliesTheStoredChoice() {
        val app = source("src/main/java/com/catsmoker/app/system/CatsmokerApp.kt")
        assertTrue(
            "a choice made in a previous process must be honoured before collection starts",
            app.contains("AnalyticsConsent.sync(this)")
        )
    }

    @Test
    fun settingsWritesThroughTheConsentOwnerInsteadOfAPreferencesOnlyFlag() {
        val vm = source("src/main/java/com/catsmoker/app/features/settings/SettingsViewModel.kt")
        assertTrue(vm.contains("AnalyticsConsent.setEnabled(context, enabled)"))
        assertFalse(
            "the decorative preference write is the defect this change removed",
            vm.contains("putBoolean(PREF_ANALYTICS")
        )
        assertTrue(
            "the toggle's initial state comes from the same owner",
            vm.contains("AnalyticsConsent.isEnabled(context)")
        )
    }

    @Test
    fun everyLocaleDescribesTheBundledSdkTruthfully() {
        val locales = listOf("values", "values-en-rGB", "values-ar", "values-es", "values-zh-rCN", "values-pt-rBR")
        locales.forEach { locale ->
            val body = legalBody(locale)
            assertTrue("$locale must name the bundled SDK", body.contains("Firebase Analytics"))
            assertFalse(
                "$locale still claims no analytics SDK is bundled",
                body.contains("No analytics SDK", ignoreCase = true)
            )
            assertFalse(
                "$locale still claims no usage data is transmitted",
                body.contains("no usage data is transmitted", ignoreCase = true)
            )
        }
    }
}
