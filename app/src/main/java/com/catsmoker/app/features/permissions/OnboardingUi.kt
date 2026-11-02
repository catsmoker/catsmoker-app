package com.catsmoker.app.features.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.app.shared.ui.components.CatsmokerButton
import com.catsmoker.app.shared.ui.components.languageDisplayName
import com.catsmoker.app.shared.ui.components.languageOptions
import com.catsmoker.app.shared.ui.components.themeModeLabel
import com.catsmoker.app.system.config.AppearanceStore

/**
 * Shared frame for the three first-run screens.
 *
 * The onboarding is deliberately not scrollable: every step must be readable at a glance
 * on the smallest supported phone. The frame therefore owns the fixed parts (progress,
 * heading, footer action) and gives the remaining height to the step's own content, which
 * is expected to distribute itself instead of stacking past the bottom. [content] receives
 * [compact] so a step can tighten its own cards/pills on short viewports.
 *
 * [dense] is for the permission checklist, which carries nine rows and therefore trades
 * the display heading for a 22 sp title. It is not a separate visual language — the
 * progress bar, section labels, surfaces and footer button stay identical.
 */
@Composable
fun OnboardingScaffold(
    step: Int,
    title: String,
    subtitle: String?,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    dense: Boolean = false,
    /**
     * Drops the subtitle on short viewports. Only the permission checklist uses this: it
     * carries the most content and its title already names the task.
     */
    hideSubtitleWhenCompact: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.(compact: Boolean) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        val compact = maxHeight < 700.dp
        val titleStyle = when {
            dense -> MaterialTheme.typography.titleLarge
            compact -> MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp, lineHeight = 32.sp)
            else -> MaterialTheme.typography.headlineLarge
        }
        val subtitleStyle = if (dense) {
            MaterialTheme.typography.bodySmall
        } else {
            MaterialTheme.typography.bodyMedium
        }
        val resolvedSubtitle = subtitle.takeUnless { compact && hideSubtitleWhenCompact }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 18.dp else 24.dp)
        ) {
            Spacer(Modifier.height(if (compact) 10.dp else 20.dp))

            if (showProgress) {
                OnboardingProgress(step = step, total = 3)
                Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
            }

            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = titleStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (resolvedSubtitle != null) {
                        Spacer(Modifier.height(if (dense) 2.dp else 5.dp))
                        Text(
                            text = resolvedSubtitle,
                            style = subtitleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trailing?.invoke()
            }

            Spacer(Modifier.height(if (compact) 10.dp else 16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = { content(compact) }
            )

            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
            footer()
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        }
    }
}

/**
 * Three-segment progress bar with a numeric counter. Segments before the active step are
 * already filled, so the user reads "how far in" without a second label.
 */
@Composable
private fun OnboardingProgress(step: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .padding(end = if (index == total - 1) 0.dp else 6.dp)
                    .height(3.dp)
                    .weight(if (index == step) 1.8f else 1f)
                    .clip(CircleShape)
                    .background(
                        if (index <= step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${step + 1}/$total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Numbered section heading used by the welcome step ("01 Theme", "02 Language"). */
@Composable
fun OnboardingSectionHeader(
    index: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/** The single primary action pinned above the navigation bar on every onboarding step. */
@Composable
fun OnboardingFooterButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    CatsmokerButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Visual theme picker: each option is a miniature of the app rather than a radio row, so
 * the choice is understood before it is read. Selecting one repaints the whole app live.
 *
 * The four previews use fixed palettes (not the current scheme) so light stays light and
 * dark stays dark while the surrounding screen is in the other mode. Only the Dynamic
 * preview borrows the live scheme, because its colours come from the wallpaper.
 */
@Composable
fun ThemeModeCards(
    selected: AppearanceStore.ThemeMode,
    onSelect: (AppearanceStore.ThemeMode) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val modes = remember { AppearanceStore.availableThemeModes() }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // A short step keeps the four previews on one line; a tall step gives them the
        // leftover height as a 2x2 board, so the screen has no dead band in the middle.
        if (maxHeight >= 150.dp) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { mode ->
                            ThemeModeCard(
                                mode = mode,
                                selected = selected == mode,
                                onClick = { onSelect(mode) },
                                compact = compact,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.forEach { mode ->
                    ThemeModeCard(
                        mode = mode,
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        compact = compact,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeCard(
    mode: AppearanceStore.ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = shape
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            ThemePreview(mode = mode, modifier = Modifier.fillMaxSize())
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }
        Text(
            text = themeModeLabel(mode),
            fontSize = if (compact) 9.5.sp else 10.5.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemePreview(
    mode: AppearanceStore.ThemeMode,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val lightBackground = Color(0xFFF2F2F2)
    val lightSurface = Color(0xFFFFFFFF)
    val lightInk = Color(0xFF161616)
    val darkBackground = Color(0xFF080808)
    val darkSurface = Color(0xFF1C1C1C)
    val darkInk = Color(0xFFFFFFFF)

    when (mode) {
        AppearanceStore.ThemeMode.SYSTEM -> Row(modifier.clip(shape)) {
            PreviewPane(
                background = lightBackground,
                surface = lightSurface,
                ink = lightInk,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            PreviewPane(
                background = darkBackground,
                surface = darkSurface,
                ink = darkInk,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        AppearanceStore.ThemeMode.LIGHT -> PreviewPane(
            background = lightBackground,
            surface = lightSurface,
            ink = lightInk,
            modifier = modifier.clip(shape)
        )
        AppearanceStore.ThemeMode.DARK -> PreviewPane(
            background = darkBackground,
            surface = darkSurface,
            ink = darkInk,
            modifier = modifier.clip(shape)
        )
        AppearanceStore.ThemeMode.DYNAMIC -> Box(
            modifier = modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
        ) {
            PreviewBars(color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/**
 * A miniature app surface: text bars, a floating card and the brand accent. Every
 * measurement is a fraction of the pane, so the same sketch reads as a preview at 60 dp
 * and still fills a 200 dp tile on a tall phone.
 */
@Composable
private fun PreviewPane(
    background: Color,
    surface: Color,
    ink: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.background(background)) {
        val pad = (maxHeight * 0.09f).coerceIn(5.dp, 16.dp)
        val barHeight = (maxHeight * 0.028f).coerceIn(2.dp, 5.dp)
        val barGap = (maxHeight * 0.03f).coerceIn(3.dp, 7.dp)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(pad),
            verticalArrangement = Arrangement.spacedBy(barGap)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.38f)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.85f))
            )
            Box(
                Modifier
                    .fillMaxWidth(0.58f)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.35f))
            )
            Box(
                Modifier
                    .fillMaxWidth(0.28f)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.18f))
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(pad)
                .fillMaxWidth(0.68f)
                .height((maxHeight * 0.20f).coerceIn(9.dp, 40.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(surface)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(pad)
                .size((maxHeight * 0.065f).coerceIn(5.dp, 12.dp))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun PreviewBars(color: Color) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pad = (maxHeight * 0.10f).coerceIn(7.dp, 18.dp)
        val barHeight = (maxHeight * 0.035f).coerceIn(3.dp, 7.dp)
        val barGap = (maxHeight * 0.04f).coerceIn(4.dp, 9.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.50f)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.90f))
            )
            Spacer(Modifier.height(barGap))
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.50f))
            )
            Spacer(Modifier.height(barGap))
            Box(
                Modifier
                    .fillMaxWidth(0.36f)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.30f))
            )
        }
    }
}

/**
 * Language picker as wrapping pills. Six options stay visible at once (no dropdown, no
 * horizontal scroll); the selected pill fills with the brand accent and carries a check,
 * while the language itself is staged until Continue because applying it restarts the
 * process.
 */
@Composable
fun LanguagePills(
    selectedTag: String,
    onSelect: (String) -> Unit,
    compact: Boolean
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        languageOptions().forEach { tag ->
            val selected = selectedTag == tag
            val shape = CircleShape
            Row(
                modifier = Modifier
                    .heightIn(min = if (compact) 38.dp else 42.dp)
                    .clip(shape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = shape
                    )
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(tag) }
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = languageDisplayName(tag),
                    fontSize = if (compact) 12.sp else 13.sp,
                    maxLines = 1,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
