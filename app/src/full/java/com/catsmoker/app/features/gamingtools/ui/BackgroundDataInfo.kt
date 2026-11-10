package com.catsmoker.app.features.gamingtools.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnLock
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.components.InfoText
import com.catsmoker.app.shared.ui.components.InfoTopic

internal fun backgroundNetworkIcon() = Icons.Default.VpnLock

internal fun backgroundDataInfo() = InfoTopic(
    name = InfoText.Res(R.string.gt_tool_net_title),
    sections = listOf(
        InfoTopics.section(R.string.gt_explainer_what, R.string.gt_net_what_1, R.string.gt_net_what_2),
        InfoTopics.section(
            R.string.gt_net_vpn_how_title, R.string.gt_net_vpn_how_1,
            R.string.gt_net_vpn_how_2, R.string.gt_net_vpn_how_3, R.string.gt_net_vpn_how_4
        ),
        InfoTopics.section(
            R.string.gt_net_ds_how_title, R.string.gt_net_ds_how_1,
            R.string.gt_net_ds_how_2, R.string.gt_net_ds_how_3
        ),
        InfoTopics.section(
            R.string.gt_net_which_title, R.string.gt_net_which_1,
            R.string.gt_net_which_2, R.string.gt_net_which_3, R.string.gt_net_which_4
        )
    )
)
