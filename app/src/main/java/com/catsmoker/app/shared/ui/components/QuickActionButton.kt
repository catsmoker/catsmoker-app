package com.catsmoker.app.shared.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A tappable tile: icon, title, and a line of supporting text.
 *
 * Two layouts, one signature. [isFullWidth] lays the tile out as a row for a list entry; the default
 * is a column sized to sit in a grid, where the subtitle is pinned to `minLines = 2` so neighbouring
 * tiles in the same row keep the same height regardless of how long their text is.
 *
 * [statusTag] replaces the subtitle in the column layout, for a tile whose state the caller reads
 * back from the device and wants to render as a chip rather than prose.
 */
@Composable
fun QuickActionButton(
    title: String,
    subtitle: String,
    iconContainerColor: Color,
    iconContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFullWidth: Boolean = false,
    showChevron: Boolean = false,
    statusTag: (@Composable () -> Unit)? = null,
    /**
     * True while the tap's work is still running. The tile goes non-clickable and shows
     * a spinner plus a thin bar, so a slow action (ZIP export, shell push) reads as busy
     * instead of dead until its toast lands.
     */
    isLoading: Boolean = false,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
    /**
     * Density overrides so dense hosts (e.g. the no-scroll dashboard) can shrink the tile
     * without changing every other screen. Defaults preserve the original 18dp / 46dp look.
     */
    contentPadding: Dp = 18.dp,
    iconSize: Dp = 46.dp,
    gridTitleGap: Dp = 22.dp,
    /**
     * Pin the icon to the top and the texts to the bottom, spreading to fill
     * whatever height the parent gives the tile. Only set this when the caller
     * fixes the tile height (e.g. `weight` in a no-scroll grid) — in a
     * wrap-content parent the content would collapse.
     */
    spreadGridContent: Boolean = false
) {
    val clickable = enabled && !isLoading
    Card(
        onClick = onClick,
        enabled = clickable,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            DotGridBackground(modifier = Modifier.matchParentSize())

            if (isFullWidth) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .clip(RoundedCornerShape(14.dp))
                            .background(iconContainerColor)
                            .border(1.dp, iconContentColor.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                            icon()
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (showChevron) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else if (spreadGridContent) {
                // Fixed-height host (e.g. weight in a no-scroll grid): pin the
                // icon to the top and the texts to the bottom so the content
                // fills the tile instead of bunching up.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    ActionIconBadge(
                        iconSize = iconSize,
                        containerColor = iconContainerColor,
                        contentColor = iconContentColor,
                        isLoading = isLoading,
                        icon = icon
                    )
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        if (statusTag != null) {
                            statusTag()
                        } else {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(contentPadding),
                    horizontalAlignment = Alignment.Start
                ) {
                    ActionIconBadge(
                        iconSize = iconSize,
                        containerColor = iconContainerColor,
                        contentColor = iconContentColor,
                        isLoading = isLoading,
                        icon = icon
                    )
                    Spacer(modifier = Modifier.height(gridTitleGap))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    if (statusTag != null) {
                        statusTag()
                    } else {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isLoading) {
                // Android 13 squiggle in a compact strip so the travelling wave fits
                // the tile's rounded bottom edge instead of a flat bar.
                SquigglyProgressBar(
                    progress = null,
                    animate = true,
                    strokeWidth = 2.dp,
                    waveLength = 12.dp,
                    waveAmplitude = 1.5.dp,
                    barHeight = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                )
            }
        }
    }
}

/**
 * The rounded icon badge plus the optional busy spinner, shared by both grid
 * layouts so the two branches can never drift apart.
 */
@Composable
private fun ActionIconBadge(
    iconSize: Dp,
    containerColor: Color,
    contentColor: Color,
    isLoading: Boolean,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(14.dp))
                .background(containerColor)
                .border(1.dp, contentColor.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon()
            }
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        }
    }
}
