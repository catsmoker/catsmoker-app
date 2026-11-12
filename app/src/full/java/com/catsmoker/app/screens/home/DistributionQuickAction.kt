package com.catsmoker.app.screens.home

import com.catsmoker.app.R

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.catsmoker.app.application.navigation.SpoofRoutes
import com.catsmoker.app.ui.components.QuickActionButton
import com.catsmoker.app.ui.theme.NothingRed

@Composable
internal fun RowScope.DistributionQuickAction(onNavigate: (String) -> Unit, contentPadding: Dp, iconSize: Dp) {
    QuickActionButton(
        title = stringResource(R.string.dash_spoof_title),
        subtitle = stringResource(R.string.dash_spoof_subtitle),
        iconContainerColor = NothingRed.copy(alpha = 0.15f),
        iconContentColor = NothingRed,
        onClick = { onNavigate(SpoofRoutes.SPOOF_DEVICE) },
        modifier = Modifier.weight(1f).fillMaxHeight(),
        spreadGridContent = true,
        showChevron = true,
        contentPadding = contentPadding,
        iconSize = iconSize,
        icon = { Icon(Icons.Default.SettingsInputComponent, null) }
    )
}
