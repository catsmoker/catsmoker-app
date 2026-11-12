package com.catsmoker.app.ui.components

import com.catsmoker.app.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catsmoker.app.ui.theme.CatsmokerSpacing

/**
 * A stacked group of cards using the app-wide card rhythm.
 *
 * A card is a surface, not a layout, so it carries no outer margin of its own — the host has
 * to space siblings. Passing every sibling to this column keeps that spacing in one place
 * instead of a hand-placed `Spacer` per pair; a pair that forgot its spacer (the Settings
 * General cards) used to render flush against each other. Horizontal equivalents pass
 * [CatsmokerSpacing.CardGap] to `Arrangement.spacedBy`.
 */
@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap),
        content = content
    )
}

/**
 * A section label, with the label-to-card gap already applied.
 *
 * Replaces the hand-rolled `Text(style = labelSmall, color = onSurfaceVariant, padding(...))`
 * repetition: several screens forgot the bottom padding entirely, leaving the heading touching
 * the card it introduces.
 *
 * [accent] paints the heading in the theme accent (bold `primary`) instead of the neutral
 * `onSurfaceVariant` — Settings uses it to separate its five groups at a glance. The text is
 * never uppercased or letter-spaced: that is a no-op for Arabic/CJK and spacing between letters
 * breaks cursive Arabic.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (accent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = if (accent) FontWeight.Bold else null,
        modifier = modifier.padding(start = 4.dp, bottom = CatsmokerSpacing.LabelGap)
    )
}

/**
 * A card heading — the title inside a [SectionCard].
 *
 * Replaces the hand-rolled `Text(stringResource(R.string.KEY), style = labelSmall, color = primary, letterSpacing = 1.sp)`
 * pattern found across the File Engineering editors. The heading is bold primary with no letter-spacing
 * (letter-spacing breaks cursive Arabic and is unnecessary for Latin uppercase) and no outer padding:
 * [SectionCard] already applies its own content padding.
 *
 * [color] defaults to `primary`; pass `error` for failure-state headings (e.g. GAME NOT FOUND).
 */
@Composable
fun CardHeading(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}
