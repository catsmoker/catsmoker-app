package com.catsmoker.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.catsmoker.app.ui.theme.AccentPrimary

/**
 * A custom loading spinner using Material3 CircularProgressIndicator styled with the app's
 * accent color for a more polished look than the default.
 */
@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = AccentPrimary,
    trackColor: Color = AccentPrimary.copy(alpha = 0.2f)
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        strokeWidth = strokeWidth,
        color = color,
        trackColor = trackColor
    )
}

/**
 * A smaller inline spinner for buttons ([CatsmokerButton] and [CatsmokerOutlinedButton] use
 * its 20dp default). The track follows [color] so it stays visible on tinted containers.
 */
@Composable
fun InlineLoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.5.dp,
    color: Color = AccentPrimary
) {
    LoadingSpinner(
        modifier = modifier,
        size = size,
        strokeWidth = strokeWidth,
        color = color,
        trackColor = color.copy(alpha = 0.15f)
    )
}
