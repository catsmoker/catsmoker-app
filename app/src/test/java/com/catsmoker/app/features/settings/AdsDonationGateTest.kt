package com.catsmoker.app.features.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The free "Enable Ads" switch is a control, not a suggestion: turning ads off must raise the
 * donation guilt-trip first and leave the preference untouched until the user insists. This lock
 * keeps the interception in the ViewModel (the single owner of `ads_enabled`) and keeps the
 * "turn off anyway" escape hatch behind one flag, so a future edit cannot silently make the first
 * tap write the preference or make the escape hatch unconditional.
 */
class AdsDonationGateTest {

    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("missing source under test: $relative")
    }

    private fun functionBody(source: String, name: String): String =
        source.substringAfter("fun $name").substringBefore("\n    }")

    @Test
    fun `turning ads off opens the guilt dialog instead of applying`() {
        val viewModel = source("src/main/java/com/catsmoker/app/features/settings/SettingsViewModel.kt")
        val onToggled = functionBody(viewModel, "onAdsToggled")
        val offBranch = onToggled.substringAfter("if (!enabled)").substringBefore("return")
        assertTrue("off tap must raise the dialog", offBranch.contains("showAdsDonationDialog = true"))
        assertFalse("off tap must not write the preference", offBranch.contains("setEnabled"))
    }

    @Test
    fun `turning ads on applies immediately and never opens the dialog`() {
        val viewModel = source("src/main/java/com/catsmoker/app/features/settings/SettingsViewModel.kt")
        val onToggled = functionBody(viewModel, "onAdsToggled")
        assertTrue(onToggled.contains("adManager.setEnabled(true)"))
        assertFalse(
            "the on path must not raise the dialog",
            onToggled.substringAfter("adManager.setEnabled(true)").contains("showAdsDonationDialog = true")
        )
    }

    @Test
    fun `the anyway action is the only explicit turn-off`() {
        val viewModel = source("src/main/java/com/catsmoker/app/features/settings/SettingsViewModel.kt")
        val anyway = functionBody(viewModel, "onAdsTurnOffAnyway")
        assertTrue(anyway.contains("adManager.setEnabled(false)"))
        assertTrue(anyway.contains("showAdsDonationDialog = false"))
    }

    @Test
    fun `the donate button uses the in-app donate route, not a browser`() {
        val screen = source("src/main/java/com/catsmoker/app/features/settings/SettingsScreen.kt")
        assertFalse("no external intent may remain", screen.contains("Intent.ACTION_VIEW"))
        assertFalse("no hard-coded donate URL may remain", screen.contains("DONATE_URL"))
        assertTrue("the dialog must hand off to navigation", screen.contains("onOpenDonate()"))

        val navHost = source("src/main/java/com/catsmoker/app/system/navigation/AppNavHost.kt")
        assertTrue(
            "Settings must navigate to the existing donate destination",
            navHost.contains("onOpenDonate = { navController.navigate(Routes.DONATE) }")
        )
    }

    @Test
    fun `the dialog shows the vector cat icon and keeps the escape hatch`() {
        val dialog = source("src/main/java/com/catsmoker/app/features/settings/AdsDonationDialog.kt")
        assertTrue(dialog.contains("R.drawable.ic_cat_sad"))
        assertTrue(dialog.contains("const val ALLOW_TURN_OFF_ANYWAY = true"))
        assertTrue("dismissal must count as Keep ads", dialog.contains("onDismissRequest = onKeepAds"))
        assertTrue(
            "the drawable must exist",
            listOf("src/main/res/drawable-nodpi/ic_cat_sad.png", "src/main/res/drawable/ic_cat_sad.xml")
                .flatMap { listOf(File(it), File("app/$it")) }
                .any { it.isFile }
        )
    }

    @Test
    fun `the dialog copy ships in english and french`() {
        val keys = listOf(
            "sys_ads_guilt_title",
            "sys_ads_guilt_message",
            "sys_ads_guilt_donate",
            "sys_ads_guilt_keep",
            "sys_ads_guilt_turn_off_anyway",
            "sys_ads_guilt_cat_desc"
        )
        listOf("values", "values-fr").forEach { locale ->
            val strings = source("src/main/res/$locale/strings_sys.xml")
            keys.forEach { key ->
                assertTrue("$key missing from $locale", strings.contains("name=\"$key\""))
            }
        }
    }
}
