package com.catsmoker.app.shared

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locale-mirror parity for the language-switch-sensitive strings.
 *
 * A key missing from the active locale's table does not crash — it falls back to another
 * table at runtime and shows the wrong language. That is how `sys_theme_dynamic` (absent from
 * `values-en-rGB`) resolved to Arabic on devices carrying ar in the locale list, and the same
 * hole would turn the PUBG profile labels or the Gaming Mode refusal note foreign the moment a
 * translation is forgotten. The intentional English-only keys (`app_name`, `res_method_*`,
 * `url_*`) are deliberately excluded — only user-visible copy that must be translated.
 */
class LocaleParityTest {

    private val localeDirs = listOf(
        "values",
        "values-ar-rSA",
        "values-en-rGB",
        "values-es-rES",
        "values-zh-rCN"
    )

    /** User-visible copy in the areas that must follow an in-app language switch. */
    private val requiredKeys = setOf(
        // PUBG / Genshin profile labels (EditGameFilesViewModel.buildPubgConfig/buildGenshinConfig).
        "gf_profile_unlock_120",
        "gf_profile_tablet",
        "gf_profile_genshin",
        // Gaming Mode refusal reasons (GamingEngine activation / recovery / revert).
        "gt_eng_un_suspend",
        "gt_eng_un_dnd_off",
        "gt_eng_un_dnd_perm",
        "gt_eng_un_refresh",
        "gt_eng_un_touch",
        "gt_eng_un_fixed",
        "gt_eng_un_gpu",
        "gt_eng_un_qti",
        "gt_eng_un_discard",
        "gt_eng_un_limit",
        "gt_eng_un_data_on",
        "gt_eng_un_data_silent",
        "gt_eng_un_data_fail",
        "gt_eng_un_framecap",
        "gt_eng_un_framecap_no",
        "gt_eng_un_framecap_kept",
        "gt_eng_un_data_revert",
        "gt_eng_still_suspended",
        "gt_eng_thermal",
        "gt_eng_revert_fail",
        // Gaming Mode note titles (GamingModeCard NoticeBlocks).
        "gt_gm_unavailable_title",
        "gt_gm_revert_leftovers",
        // Theme option added by the Dynamic-color pick (once absent from en-rGB).
        "sys_theme_dynamic",
        // Support dialog "Do not show again" checkbox (shown from the 2nd appearance).
        "sys_support_never",
        // Donate screen chrome (coin/network names and addresses are data, kept verbatim).
        "donate_title",
        "donate_subtitle",
        "donate_binance",
        "donate_binance_id",
        "donate_qr",
        "donate_crypto",
        "donate_copy",
        "donate_copied",
        "donate_open",
        "donate_paypal",
        // About screen community-button titles (icon-only before; now labeled).
        "about_social_github",
        "about_social_website",
        "about_social_discord",
        "about_social_telegram",
    )

    private fun keysIn(dir: String): Set<String> {
        val names = mutableSetOf<String>()
        val pattern = Regex("""<string name="([^"]+)"""")
        File("src/main/res/$dir").listFiles { f -> f.extension == "xml" }.orEmpty().forEach { file ->
            pattern.findAll(file.readText()).forEach { names += it.groupValues[1] }
        }
        return names
    }

    @Test
    fun languageSwitchStringsExistInDefaultAndEveryMirror() {
        localeDirs.forEach { dir ->
            val missing = requiredKeys - keysIn(dir)
            assertTrue("missing from $dir: ${missing.sorted()}", missing.isEmpty())
        }
    }
}
