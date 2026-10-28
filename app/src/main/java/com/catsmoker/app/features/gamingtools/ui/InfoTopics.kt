package com.catsmoker.app.features.gamingtools.ui

import androidx.annotation.StringRes
import com.catsmoker.app.R
import com.catsmoker.app.features.gamingtools.tools.dns.DnsFeature
import com.catsmoker.app.shared.ui.components.InfoSection
import com.catsmoker.app.shared.ui.components.InfoText
import com.catsmoker.app.shared.ui.components.InfoTone
import com.catsmoker.app.shared.ui.components.InfoTopic

/**
 * The single home of every question-and-answer explainer in Gaming Tools.
 *
 * These are the same sentences the old inline accordions showed, kept word for word; only their
 * container changed. Each feature owns one [InfoTopic], so a feature with several questions still
 * renders one [com.catsmoker.app.shared.ui.components.InfoButton] and opens one sheet.
 *
 * Text is stored as string-resource ids and resolved at composition. Nothing here caches a
 * translated string, so switching the app language repaints the sheet in the new language.
 *
 * Sections are marked [InfoTone.Caution] only where the copy is about a cost, risk or safety
 * limit. That tone drives the amber warning triangle; the wording itself never changes.
 */
object InfoTopics {

    // ---------------------------------------------------------------------------------------------
    // Gaming Mode
    // ---------------------------------------------------------------------------------------------

    /** Gaming Mode's activation, snapshot and honest-refusal account. */
    val gamingMode = InfoTopic(
        name = InfoText.Res(R.string.gt_gm_title),
        sections = listOf(
            section(
                R.string.gt_gm_what_title,
                R.string.gt_gm_what_1,
                R.string.gt_gm_what_2,
                R.string.gt_gm_what_3
            )
        )
    )

    /** The standalone fixed-performance switch and when not to leave it on. */
    val fixedPerformance = InfoTopic(
        name = InfoText.Res(R.string.gt_fp_title),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_fp_what_1,
                R.string.gt_fp_what_2,
                R.string.gt_fp_what_3
            ),
            section(
                R.string.gt_fp_should_title,
                R.string.gt_fp_should_1,
                R.string.gt_fp_should_2,
                R.string.gt_fp_should_3,
                tone = InfoTone.Caution
            )
        )
    )

    /** The keep-running exception list inside the Gaming Mode card. */
    val keepRunning = InfoTopic(
        name = InfoText.Res(R.string.gt_keep_title),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_keep_what_1,
                R.string.gt_keep_what_2
            )
        )
    )

    /** The extra freeze list inside the Gaming Mode card. */
    val extraSuspend = InfoTopic(
        name = InfoText.Res(R.string.gt_suspend_title),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_suspend_what_1,
                R.string.gt_suspend_what_2
            )
        )
    )

    // ---------------------------------------------------------------------------------------------
    // Performance
    // ---------------------------------------------------------------------------------------------

    /** Volume boost: what it does and the hearing/quality cost. */
    val boost = InfoTopic(
        name = InfoText.Res(R.string.gt_tool_sound_title),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_boost_what_1,
                R.string.gt_boost_what_2
            ),
            section(
                R.string.gt_boost_safe_title,
                R.string.gt_boost_safe_1,
                R.string.gt_boost_safe_2,
                tone = InfoTone.Caution
            )
        )
    )

    /** ART pre-compilation: what it buys, what it costs, and why there is only one mode. */
    val appBooster = InfoTopic(
        name = InfoText.Res(R.string.gt_tool_booster_title),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_booster_what_1,
                R.string.gt_booster_what_2,
                R.string.gt_booster_what_3
            ),
            section(
                R.string.gt_booster_cost_title,
                R.string.gt_booster_cost_1,
                R.string.gt_booster_cost_2,
                R.string.gt_booster_cost_3,
                tone = InfoTone.Caution
            ),
            section(
                R.string.gt_booster_why_title,
                R.string.gt_booster_why_1,
                R.string.gt_booster_why_2,
                R.string.gt_booster_why_3
            )
        )
    )

    /** Animation-speed rows in Developer Options. */
    val animationScales = InfoTopic(
        name = InfoText.Res(R.string.gt_dev_anim),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_dev_anim_what_1,
                R.string.gt_dev_anim_what_2
            )
        )
    )

    /** "Keep fewer apps in memory" switch. */
    val keepFewerApps = InfoTopic(
        name = InfoText.Res(R.string.gt_dev_keep_title),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_dev_keep_1,
                R.string.gt_dev_keep_2
            )
        )
    )

    /** "Close apps as soon as you leave" switch. */
    val closeOnLeave = InfoTopic(
        name = InfoText.Res(R.string.gt_dev_close_title),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_dev_close_1,
                R.string.gt_dev_close_2
            )
        )
    )

    /** Peak-refresh-rate switch that Android only exposes on its own screen. */
    val peakRefresh = InfoTopic(
        name = InfoText.Res(R.string.gt_dev_peak),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_dev_peak_1,
                R.string.gt_dev_peak_2
            )
        )
    )

    /** Game default-frame-rate switch that Android only exposes on its own screen. */
    val gameDefaultFrameRate = InfoTopic(
        name = InfoText.Res(R.string.gt_dev_game_speed),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_dev_game_speed_1,
                R.string.gt_dev_game_speed_2
            )
        )
    )

    /** Per-game graphics-driver choice. */
    val angleDriver = InfoTopic(
        name = InfoText.Res(R.string.gt_angle_title),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_graphics_layers,
                R.string.gt_angle_1,
                R.string.gt_angle_2
            )
        )
    )

    // ---------------------------------------------------------------------------------------------
    // Graphics
    // ---------------------------------------------------------------------------------------------

    /**
     * Screen resolution, its safety account, and how the three graphics layers stack.
     *
     * Graphics is three independent layers (F11): this card's display override, the render-scale
     * subsection (per-game internal rendering), and the ANGLE driver table. Stating the stack here
     * keeps a lower resolution from reading as a device-wide performance cap.
     *
     * [source] is the already-resolved name of where the real screen size came from ("your phone",
     * or the reader that reported it). The old accordion formatted the same value into the third
     * safety line, so the sentence stays identical.
     */
    fun resolution(source: String): InfoTopic = InfoTopic(
        name = InfoText.Res(R.string.gt_tool_screen_title),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_res_what_1,
                R.string.gt_res_what_2,
                R.string.gt_res_what_3
            ),
            InfoSection(
                title = InfoText.Res(R.string.gt_res_safe_title),
                paragraphs = listOf(
                    InfoText.Res(R.string.gt_res_safe_1),
                    InfoText.Res(R.string.gt_res_safe_2),
                    InfoText.Formatted(R.string.gt_res_safe_3, listOf(source))
                ),
                tone = InfoTone.Caution
            ),
            section(
                R.string.gt_graphics_layers_title,
                R.string.gt_graphics_layers
            )
        )
    )

    // ---------------------------------------------------------------------------------------------
    // Focus & network
    // ---------------------------------------------------------------------------------------------

    /** Background Data: the shared purpose, both mechanisms, and which one to use. */
    val backgroundData = InfoTopic(
        name = InfoText.Res(R.string.gt_tool_net_title),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_net_what_1,
                R.string.gt_net_what_2
            ),
            section(
                R.string.gt_net_vpn_how_title,
                R.string.gt_net_vpn_how_1,
                R.string.gt_net_vpn_how_2,
                R.string.gt_net_vpn_how_3,
                R.string.gt_net_vpn_how_4
            ),
            section(
                R.string.gt_net_ds_how_title,
                R.string.gt_net_ds_how_1,
                R.string.gt_net_ds_how_2,
                R.string.gt_net_ds_how_3
            ),
            section(
                R.string.gt_net_which_title,
                R.string.gt_net_which_1,
                R.string.gt_net_which_2,
                R.string.gt_net_which_3,
                R.string.gt_net_which_4
            )
        )
    )

    /**
     * Private DNS: the two standing questions, plus the selected provider's own note.
     *
     * [provider] is non-null only when the device is actually in provider mode, so the extra section
     * appears exactly when the old inline "About …" explainer did.
     */
    fun dns(provider: DnsFeature.Provider?): InfoTopic {
        val sections = mutableListOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_dns_what_1,
                R.string.gt_dns_what_2,
                R.string.gt_dns_what_3
            ),
            section(
                R.string.gt_dns_ping_title,
                R.string.gt_dns_ping_1,
                R.string.gt_dns_ping_2,
                R.string.gt_dns_ping_3
            )
        )
        if (provider != null) {
            sections += InfoSection(
                title = InfoText.Formatted(R.string.gt_dns_about, listOf(provider.label)),
                paragraphs = listOf(
                    InfoText.Res(provider.noteRes()),
                    InfoText.Formatted(
                        R.string.gt_dns_about_proof,
                        listOf(provider.addresses.joinToString())
                    )
                )
            )
        }
        return InfoTopic(
            name = InfoText.Res(R.string.gt_tool_dns_title),
            sections = sections
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Background apps & memory
    // ---------------------------------------------------------------------------------------------

    /** Auto Force Stop: behaviour, the two grants, and the cost of closing every app. */
    val autoForceStop = InfoTopic(
        name = InfoText.Res(R.string.gt_tool_afs_title),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_afs_what_1,
                R.string.gt_afs_what_2,
                R.string.gt_afs_what_3
            ),
            section(
                R.string.gt_afs_perm_title,
                R.string.gt_afs_perm_1,
                R.string.gt_afs_perm_2,
                R.string.gt_afs_perm_3
            ),
            section(
                R.string.gt_afs_should_title,
                R.string.gt_afs_should_1,
                R.string.gt_afs_should_2,
                R.string.gt_afs_should_3,
                tone = InfoTone.Caution
            )
        )
    )

    /** Storage cleaner: what it deletes, and which rows are the stronger ones. */
    val cleaning = InfoTopic(
        name = InfoText.Res(R.string.gt_tool_cleaner_title),
        sections = listOf(
            section(
                R.string.gt_explainer_what,
                R.string.gt_cleaner_what_1,
                R.string.gt_cleaner_what_2,
                R.string.gt_cleaner_what_3
            ),
            section(
                R.string.gt_cleaner_safe_title,
                R.string.gt_cleaner_safe_1,
                R.string.gt_cleaner_safe_2,
                R.string.gt_cleaner_safe_3,
                tone = InfoTone.Caution
            )
        )
    )

    /** The cleaner's scheduled sweep. */
    val cleaningSchedule = InfoTopic(
        name = InfoText.Res(R.string.gt_booster_schedule),
        sections = listOf(
            section(
                R.string.gt_booster_sched_what_title,
                R.string.gt_cleaner_sched_1,
                R.string.gt_cleaner_sched_2,
                R.string.gt_cleaner_sched_3
            )
        )
    )

    /** The dexopt sweep's scheduled run. */
    val dexoptSchedule = InfoTopic(
        name = InfoText.Res(R.string.gt_booster_schedule),
        sections = listOf(
            section(
                R.string.gt_booster_sched_what_title,
                R.string.gt_booster_sched_1,
                R.string.gt_booster_sched_2,
                R.string.gt_booster_sched_3,
                R.string.gt_booster_sched_4
            )
        )
    )

    // ---------------------------------------------------------------------------------------------
    // Builders
    // ---------------------------------------------------------------------------------------------

    private fun section(
        @StringRes title: Int,
        vararg paragraphs: Int,
        tone: InfoTone = InfoTone.Neutral
    ): InfoSection = InfoSection(
        title = InfoText.Res(title),
        paragraphs = paragraphs.map { InfoText.Res(it) },
        tone = tone
    )

    /** The localized provider note, keyed by id; hostnames/addresses stay verbatim. */
    @StringRes
    private fun DnsFeature.Provider.noteRes(): Int = when (id) {
        "google" -> R.string.gt_dns_note_google
        "quad9" -> R.string.gt_dns_note_quad9
        "adguard" -> R.string.gt_dns_note_adguard
        else -> R.string.gt_dns_note_cloudflare
    }
}
