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
        "values-en-rGB",
        "values-ar",
        "values-es",
        "values-zh-rCN",
        "values-pt-rBR"
    )

    /** User-visible copy in the areas that must follow an in-app language switch. */
    private val requiredKeys = setOf(
        // PUBG / Genshin / New State profile labels and outcomes
        // (EditGameFilesViewModel.buildPubgConfig/buildGenshinConfig/buildNewStateConfig).
        "gf_profile_unlock_120",
        "gf_profile_tablet",
        "gf_profile_genshin",
        "gf_profile_user_custom",
        "gf_profile_newstate",
        "gf_newstate_need_root",
        "gf_newstate_unrecognized",
        "gf_newstate_already",
        "gf_newstate_applied",
        "gf_newstate_read_failed",
        // Gaming Mode refusal reasons (GamingEngine activation / recovery / revert).
        "gt_eng_un_suspend",
        "gt_eng_un_dnd_off",
        "gt_eng_un_dnd_perm",
        "gt_eng_un_refresh",
        "gt_eng_un_touch",
        "gt_eng_un_fixed",
        "gt_eng_un_gpu",
        "gt_eng_un_qti",
        "gt_eng_un_snap",
        "gt_eng_un_input_boost",
        "gt_eng_un_governor",
        "gt_eng_un_spectrum",
        "gt_eng_un_storage",
        "gt_eng_un_memory",
        // vivo display-mode lock (enumerated mode id + vendor refresh switch).
        "gt_eng_un_vivo",
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
        "gt_eng_still_restricted",
        "gt_eng_un_appops",
        "gt_eng_thermal",
        "gt_eng_revert_fail",
        "gt_eng_restore_refused",
        // Gaming Mode note titles (GamingModeCard NoticeBlocks).
        "gt_gm_unavailable_title",
        "gt_gm_revert_leftovers",
        // Folded Gaming Mode read-back report (header, summary and empty state).
        "gt_gm_details_show",
        "gt_gm_details_hide",
        "gt_gm_details_summary",
        "gt_gm_details_empty",
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
        "donate_notice",
        // Settings analytics toggle — verifies the opt-out is a real switch, not cosmetic.
        "sys_analytics_title",
        "sys_analytics_sub",
        // Legal screens (Privacy Policy + Terms & Conditions, incl. onboarding dialogs).
        "legal_section_title",
        "legal_website_title",
        "legal_privacy_title",
        "legal_privacy_subtitle",
        "legal_privacy_updated",
        "legal_terms_title",
        "legal_terms_subtitle",
        "legal_terms_updated",
        "legal_p_intro",
        "legal_p_local_title",
        "legal_p_local_body",
        "legal_p_analytics_title",
        "legal_p_analytics_body",
        "legal_p_ads_title",
        "legal_p_ads_body",
        "legal_p_updates_title",
        "legal_p_updates_body",
        "legal_p_sharing_title",
        "legal_p_sharing_body",
        "legal_p_permissions_title",
        "legal_p_permissions_body",
        "legal_p_vpn_title",
        "legal_p_vpn_body",
        "legal_p_external_title",
        "legal_p_external_body",
        "legal_p_rights_title",
        "legal_p_rights_body",
        "legal_p_disclosure_title",
        "legal_p_disclosure_body",
        "legal_p_transfers_title",
        "legal_p_transfers_body",
        "legal_p_retention_title",
        "legal_p_retention_body",
        "legal_p_children_title",
        "legal_p_children_body",
        "legal_p_security_title",
        "legal_p_security_body",
        "legal_p_consent_title",
        "legal_p_consent_body",
        "legal_p_changes_title",
        "legal_p_changes_body",
        "legal_p_contact_title",
        "legal_p_contact_body",
        "legal_t_intro",
        "legal_t_license_title",
        "legal_t_license_body",
        "legal_t_ip_title",
        "legal_t_ip_body",
        "legal_t_eligibility_title",
        "legal_t_eligibility_body",
        "legal_t_use_title",
        "legal_t_use_body",
        "legal_t_games_title",
        "legal_t_games_body",
        "legal_t_risk_title",
        "legal_t_risk_body",
        "legal_t_content_title",
        "legal_t_content_body",
        "legal_t_thirdparty_title",
        "legal_t_thirdparty_body",
        "legal_t_payments_title",
        "legal_t_payments_body",
        "legal_t_privacy_title",
        "legal_t_privacy_body",
        "legal_t_updates_title",
        "legal_t_updates_body",
        "legal_t_termination_title",
        "legal_t_termination_body",
        "legal_t_liability_title",
        "legal_t_liability_body",
        "legal_t_indemnity_title",
        "legal_t_indemnity_body",
        "legal_t_law_title",
        "legal_t_law_body",
        "legal_t_misc_title",
        "legal_t_misc_body",
        "legal_t_changes_title",
        "legal_t_changes_body",
        "legal_t_contact_title",
        "legal_t_contact_body",
        "core_terms_read_privacy",
        "core_terms_read_terms",
        // Accessible names for previously unlabeled icon-only buttons.
        "core_desc_add",
        "core_desc_more",
        "core_desc_save",
        "core_desc_random",
        // Ads-toggle explanation (names the Start.io provider).
        "sys_ads_sub",
        // Play-variant paid ad removal (Settings purchase row + status + notices).
        "sys_remove_ads_title",
        "sys_remove_ads_sub",
        "sys_ads_removed_title",
        "sys_ads_removed_sub",
        "sys_billing_unavailable",
        "sys_remove_ads_pending",
        "sys_remove_ads_failed",
        // Updater cancel / verdict / install-confirmation copy.
        "sys_cancel",
        "sys_update_cancelled",
        "sys_update_rejected",
        "sys_update_installed",
        // Legal website label (About + legal screens + first-launch agreement).
        "legal_website_title",
        // Accessible name for the Gaming Mode power toggle (state-dependent).
        "gt_gm_toggle_on",
        "gt_gm_toggle_off",
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
        // Filesystem trim action and outcomes.
        "gt_trim_title",
        "gt_trim_need",
        "gt_trim_done",
        "gt_trim_some",
        "gt_trim_no",
        "gt_trim_no_reason",
        "gt_trim_done_unmeasured",
        // Extra suspend list (user picks frozen at Gaming Mode activation).
        "gt_suspend_title",
        "gt_suspend_sub",
        "gt_suspend_what_1",
        "gt_suspend_what_2",
        "gt_suspend_none",
        "gt_suspend_some",
        "gt_suspend_add",
        "gt_suspend_remove",
        // Keep-running list (user exceptions subtracted from the Gaming Mode sweep).
        "gt_keep_title",
        "gt_keep_sub",
        "gt_keep_what_1",
        "gt_keep_what_2",
        "gt_keep_none",
        "gt_keep_some",
        "gt_keep_summary_more",
        "gt_keep_show_list",
        "gt_keep_hide_list",
        "gt_keep_add",
        "gt_keep_remove",
        "gt_picker_add_keep",
        // DNS resolver ping presets (measure-only button and per-address outcome).
        "gt_dns_ping_measure",
        "gt_dns_ping_none",
        // Auto game session monitor (card and service notification).
        "gt_session_title",
        "gt_session_sub",
        "gt_session_need_games",
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
        // Per-field publish switches (row status line, toggle a11y label, partial-spoof warning).
        "spoof_field_not_published",
        "spoof_field_publish_toggle",
        "spoof_switches_note",
        // M117 user-declared hardware facts (group, four field labels, honesty note).
        "spoof_group_facts",
        "spoof_field_facts_cores",
        "spoof_field_facts_ram",
        "spoof_field_facts_heap",
        "spoof_field_facts_large_heap",
        "spoof_facts_note",
        // M116 emulator-file switch (group, switch label, honesty note).
        "spoof_group_emulator",
        "spoof_emulator_switch",
        "spoof_emulator_note",
        // M146 per-app Android ID seed (field label and note).
        "spoof_field_id_seed",
        "spoof_id_seed_note",
        // Safe-mode per-app lists (full-skip vs version-only copy, section text and row labels).
        "spoof_safe_subtitle",
        "spoof_safe_desc",
        "spoof_safe_full",
        "spoof_safe_version",
        "spoof_safe_version_note",
        // Diagnostics native-bridge row (load fact, never a capability claim).
        "spoof_diag_native_title",
        "spoof_diag_native_bridge",
        // Diagnostics identity rows (SoC, ABI) beyond the Build strings.
        "spoof_diag_soc",
        "spoof_diag_abi",
        // Editor undo/redo buttons and dirty line.
        "gf_undo",
        "gf_redo",
        "gf_unsaved",
        // PUBG custom upload: file types, resolved destination, format refusals,
        // read-back verification failure, and the unsupported-field note in the save editor.
        "gf_custom_file_type",
        "gf_custom_destination",
        "gf_file_active_sav",
        "gf_file_usercustom_ini",
        "gf_upload_type_unsupported",
        "gf_upload_empty",
        "gf_upload_not_gvas",
        "gf_upload_not_usercustom",
        "gf_upload_verify_failed",
        "gf_save_field_unsupported",
        // HSR apply/restore truthfulness: no-op apply explanation, restore reversibility,
        // unsaved-edit confirmation, and the resolution-presets caption.
        "gf_no_pending_changes",
        "gf_restore_backup_note",
        "gf_restore_confirm_title",
        "gf_restore_confirm_body",
        "gf_hsr_res_presets_note",
        // GRID's collapsed advanced controls (FXAA + window scale stay reachable).
        "gf_more_options",
        "gf_fewer_options",
        // WuWa unscored chipset note. (The battle-record keys and the log-import
        // keys lived here and were removed with Game Log Analysis — product requirement.)
        "gf_chipset",
        // WuWa custom-CVar overrides editor.
        "gf_fx_override_t",
        "gf_fx_override_c",
        "gf_fx_override_hint",
        "gf_fx_override_applied",
        // WuWa generation report lines.
        "gf_preview_report",
        "gf_preview_report_rejected",
        "gf_preview_report_rt",
        // WuWa backup-vs-generated diff row (newest Engine.ini backup vs preview).
        "gf_backup_diff_detail",
        "gf_backup_diff_same",
        // Smart Recommend current-vs-recommended distinction.
        "gf_wuwa_recommended",
        "gf_wuwa_current_preset",
        // WuWa CVar-database hygiene switch (Advanced card) and its loading toast.
        "gf_adv_cvardb_t",
        "gf_adv_cvardb_c",
        "gf_wuwa_cvardb_needed",
        // GRID FXAA + high-preset window scale (the shipped file's two extra keys).
        "gf_grid_sw_fxaa",
        "gf_grid_window_scale",
        "gf_grid_window_scale_caption",
        // WuWa shader-cache cleanup (reference Protocol A; delete + read-back).
        "gf_wuwa_cache_btn",
        "gf_wuwa_cache_caption",
        "gf_wuwa_cache_cleaning",
        "gf_wuwa_cache_done",
        "gf_wuwa_cache_none",
        "gf_wuwa_cache_failed",
        "gf_wuwa_cache_no_channel",
        // System Logs live viewer (title, states, three menu actions, clear confirm).
        "logs_title",
        "logs_subtitle",
        "logs_empty",
        "logs_copy",
        "logs_copied",
        "logs_export_report",
        "logs_export_failed",
        "logs_share",
        "logs_clear",
        "logs_clear_title",
        "logs_clear_body",
        "logs_collection_failed",
        // Pre-launch single-game compile (card button and outcome toasts).
        "gt_library_optimize",
        // Launcher-style game grid: the icon is the button and announces the game name.
        "gt_library_actions",
        // Game library search/cap controls (only appear with a large library).
        "gt_library_search",
        "gt_library_no_match",
        "gt_library_show_all",
        "gt_library_show_less",
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
        // Gaming Mode not-applicable rows (shown grayed out instead of omitted).
        "gt_gm_na_value",
        "gt_gm_na_qualcomm",
        "gt_gm_na_mediatek",
        "gt_gm_na_snapdragon",
        "gt_gm_na_kernel",
        "gt_gm_label_input_boost",
        "gt_gm_label_governor",
        "gt_gm_governor_perf",
        "gt_gm_label_spectrum",
        "gt_gm_spectrum_gaming",
        "gt_gm_spectrum_perf",
        "gt_gm_label_readahead",
        "gt_gm_kb",
        "gt_gm_label_mem",
        "gt_gm_na_intervention",
        "gt_gm_na_notif",
        "gt_gm_na_nogame",
        // Permission rows that need a newer Android version (shown disabled, not hidden).
        "core_perm_need_13",
        "core_perm_need_12",
        // Root denial feedback and Shizuku tri-state (not running / running without grant).
        "core_perm_root_denied",
        "core_perm_shizuku_not_running",
        "core_perm_shizuku_not_granted",
        // Spoof home root-manager tile (shown disabled when not rooted, not hidden).
        "spoof_root_manager_need",
        // Spoofing method exclusivity (Xposed vs Catsmoker module conflict + confirms).
        "spoof_method_magisk_installed",
        "spoof_method_magisk_installed_sub",
        "spoof_method_magisk_disabled",
        "spoof_method_magisk_disabled_sub",
        "spoof_method_magisk_absent",
        "spoof_method_magisk_absent_sub",
        "spoof_method_magisk_unknown",
        "spoof_method_magisk_unknown_sub",
        "spoof_method_conflict_title",
        "spoof_method_conflict_body",
        "spoof_method_conflict_failed",
        "spoof_method_conflict_retry",
        "spoof_method_conflict_disabled",
        "spoof_method_conflict_disable_failed",
        "spoof_method_confirm_assign_title",
        "spoof_method_confirm_assign_body",
        "spoof_method_confirm_generate_title",
        "spoof_method_confirm_generate_body",
        "spoof_method_use_anyway",
        // Root provider naming (Magisk / KernelSU / APatch, unknown and multiple).
        "spoof_root_provider_none",
        "spoof_root_provider_unknown",
        "spoof_root_provider_magisk",
        "spoof_root_provider_kernelsu",
        "spoof_root_provider_apatch",
        "spoof_root_provider_multiple",
        "spoof_root_name_magisk",
        "spoof_root_name_kernelsu",
        "spoof_root_name_apatch",
        // Shizuku row (stopped / running without grant / granted).
        "spoof_home_shizuku",
        "spoof_home_shizuku_granted",
        "spoof_home_shizuku_not_granted",
        "spoof_home_shizuku_stopped",
        // Xposed activation evidence line (manager state + observed load).
        "spoof_home_xposed_active_detail",
        "spoof_home_xposed_disabled_detail",
        "spoof_home_xposed_unknown_detail",
        "spoof_home_xposed_loaded_nopriv",
        "spoof_home_xposed_disabled_but_loaded",
        "spoof_home_xposed_nopriv",
        "spoof_home_xposed_nofw",
        "spoof_home_lsposed_unverified",
        "spoof_home_lsposed_disabled",
        "spoof_home_lsposed_loaded_disabled",
        "spoof_home_lsposed_absent",
        "spoof_home_lsposed_unknown",
        "spoof_home_xposed_loads_seen",
        "spoof_home_xposed_own_load_seen",
        "spoof_home_xposed_load_pending",
        // Manager scope readout (count and the actionable empty state).
        "spoof_home_xposed_scope_count",
        "spoof_home_xposed_scope_empty",
        // Performance readout reposition mode (position label, four hint states, buttons).
        "gt_overlay_position",
        "gt_overlay_off",
        "gt_overlay_move",
        "gt_overlay_moved_hint",
        "gt_overlay_default_hint",
        "gt_overlay_done",
        "gt_overlay_move_btn",
        "gt_overlay_reset",
        // Performance readout notification while in move mode (title, text, Done action).
        "gt_svc_perf_moving",
        "gt_svc_perf_taking",
        "gt_svc_perf_done",
        // Performance readout GPU row placeholder (utilization + core clock).
        "overlay_gpu_initial",
        // Gaming Mode interrupted-session recovery (status, restore action, honest outcomes).
        "gt_gm_checking",
        "gt_gm_interrupted",
        "gt_gm_recovery_title",
        "gt_gm_recovery_body",
        "gt_gm_recovery_unverified",
        "gt_gm_restore",
        "gt_gm_restore_done",
        "gt_gm_restore_partial",
        "gt_gm_restore_unverified",
        "gt_gm_restore_active",
        "gt_gm_revert_needed",
        "gt_gm_error_session_open",
        "gt_gm_recovery_first",
        // Extra freeze list inside the card: selected vs actually frozen, and its disclosure.
        "gt_suspend_count",
        "gt_suspend_show",
        "gt_suspend_hide",
        "gt_suspend_badge_frozen",
        "gt_suspend_badge_selected",
        // Merged cleaner card: review/clean disclosure and the advanced schedule/rules fold.
        "gt_cleaner_review",
        "gt_cleaner_review_hide",
        "gt_cleaner_advanced",
        "gt_cleaner_advanced_hide",
        "gt_tool_cleaner_sub",
    )

    private fun keysIn(dir: String): Set<String> {
        val names = mutableSetOf<String>()
        val pattern = Regex("""<string name="([^"]+)"""")
        // Flavor-merged view: shared res plus the full variant's res (spoof strings
        // live in src/full now). Both variants assert the same parity — the Play
        // build hides those screens but the strings still ship translated.
        listOf("src/main/res/$dir", "src/full/res/$dir").forEach { resDir ->
            File(resDir).listFiles { f -> f.extension == "xml" }.orEmpty().forEach { file ->
                pattern.findAll(file.readText()).forEach { names += it.groupValues[1] }
            }
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
