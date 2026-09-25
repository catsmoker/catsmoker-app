package com.catsmoker.app.shared

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locale-mirror parity for the language-switch-sensitive strings.
 *
 * A key missing from the active locale's table does not crash — it falls back to another
 * table at runtime and shows the wrong language. That is how `sys_theme_dynamic` (once absent
 * from a mirror) resolved to the wrong language on devices carrying several locales in the
 * list, and the same hole would turn the PUBG profile labels or the Gaming Mode refusal note
 * foreign the moment a translation is forgotten. The intentional English-only keys (`app_name`,
 * `res_method_*`, `url_*`) are deliberately excluded — only user-visible copy that must be
 * translated.
 */
class LocaleParityTest {

    private val localeDirs = listOf(
        "values",
        "values-ar",
        "values-es",
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
        // Gaming Mode render-scale picker (opt-in downscale for the game intervention).
        "gt_downscale_title",
        "gt_downscale_sub",
        "gt_downscale_off",
        "gt_downscale_off_set",
        "gt_downscale_set",
        // Per-game graphics driver (ANGLE table shared with Developer Options).
        "gt_angle_title",
        "gt_angle_1",
        "gt_angle_2",
        "gt_angle_need",
        "gt_angle_pick",
        "gt_angle_angle",
        "gt_angle_native",
        "gt_angle_default",
        "gt_angle_now",
        "gt_angle_none",
        "gt_angle_set",
        "gt_angle_no",
        "gt_angle_no_reason",
        // Panel refresh readout (rates, seamless switches, adaptive support).
        "gt_panel_title",
        "gt_panel_rates",
        "gt_panel_seamless",
        "gt_panel_seamless_none",
        "gt_panel_seamless_unknown",
        "gt_panel_arr",
        "gt_panel_yes",
        "gt_panel_no",
        "gt_panel_unknown",
        "gt_panel_unreadable",
        // Filesystem trim action and outcomes.
        "gt_trim_title",
        "gt_trim_need",
        "gt_trim_done",
        "gt_trim_some",
        "gt_trim_no",
        "gt_trim_no_reason",
        // Extra suspend list (user picks frozen at Gaming Mode activation).
        "gt_suspend_title",
        "gt_suspend_sub",
        "gt_suspend_what_1",
        "gt_suspend_what_2",
        "gt_suspend_none",
        "gt_suspend_some",
        "gt_suspend_add",
        "gt_suspend_remove",
        // DNS resolver ping presets (measure-only button and per-address outcome).
        "gt_dns_ping_measure",
        "gt_dns_ping_none",
        // Auto game session monitor (card and service notification).
        "gt_session_title",
        "gt_session_sub",
        "gt_session_what_1",
        "gt_session_what_2",
        "gt_session_watching",
        "gt_session_active",
        "gt_svc_session_title",
        "gt_svc_session_channel",
        "gt_svc_session_off",
        "gt_svc_session_watching",
        "gt_svc_session_active",
        "gt_svc_session_manual",
        "gt_svc_session_no_usage",
        "gt_svc_session_no_priv",
        // Spoof profile editor resident-hook gate note.
        "spoof_refresh_note",
        // Editor undo/redo buttons and dirty line.
        "gf_undo",
        "gf_redo",
        "gf_unsaved",
        // WuWa battle record and unscored chipset note.
        "gf_battle_title",
        "gf_battle_fights",
        "gf_battle_move",
        "gf_battle_dodge",
        "gf_battle_echoes",
        "gf_battle_month",
        "gf_chipset",
        // Pre-launch single-game compile (card button and outcome toasts).
        "gt_library_optimize",
        "gt_library_edit_files",
        "gt_game_opt_done",
        "gt_game_opt_done_detail",
        "gt_game_opt_skip",
        "gt_game_opt_fail",
        // Unified App Compile Optimisation card (scope chips and single-game action).
        "gt_booster_scope_title",
        "gt_booster_scope_single",
        "gt_booster_scope_all",
        "gt_booster_scope_hint_single",
        "gt_booster_scope_hint_all",
        "gt_booster_precompile",
        "gt_booster_pick_game",
        "gt_booster_no_games_scope",
        // Gaming Mode ownership note and graphics-layer explainer.
        "gt_managed_by_gaming_mode",
        "gt_graphics_layers",
        "gt_graphics_layers_title",
        // Shared app picker titles (game library vs freeze list).
        "gt_picker_add_game",
        "gt_picker_add_freeze",
        // Gaming Mode touch-speed picker (pointer_speed choice with snapshot/restore).
        "gt_eng_un_pointer",
        "gt_pointer_title",
        "gt_pointer_sub",
        "gt_pointer_stock",
        "gt_pointer_stock_set",
        "gt_pointer_set",
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
