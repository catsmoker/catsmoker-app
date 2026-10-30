package com.catsmoker.app.features.settings

import androidx.annotation.StringRes
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.components.InfoSection
import com.catsmoker.app.shared.ui.components.InfoText
import com.catsmoker.app.shared.ui.components.InfoTopic

/**
 * The long-form answers behind the Settings screen's info buttons.
 *
 * Only the Privacy & Data switches keep an info icon: analytics and ads. Every other row is
 * self-explanatory (icon, title, current value) with no long description. Copy is stored as
 * `@StringRes` ids and resolved at composition, so a language switch repaints the sheet.
 */
object SettingsInfoTopics {

    /** Analytics: the full disclosure of what Firebase receives. */
    val analytics: InfoTopic = topic(
        name = R.string.sys_analytics_title,
        body = R.string.sys_analytics_sub
    )

    /** Ads: the full disclosure of what the ad provider collects. */
    val ads: InfoTopic = topic(
        name = R.string.sys_ads_title,
        body = R.string.sys_ads_sub
    )

    private fun topic(@StringRes name: Int, @StringRes body: Int): InfoTopic = InfoTopic(
        name = InfoText.Res(name),
        sections = listOf(
            InfoSection(
                title = InfoText.Res(R.string.sys_info_what_title),
                paragraphs = listOf(InfoText.Res(body))
            )
        )
    )
}
