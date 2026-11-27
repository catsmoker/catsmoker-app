package com.catsmoker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.catsmoker.app.ui.theme.CatsmokerTheme

/**
 * One peer section of a workspace, as the switcher needs to draw it.
 *
 * The identifier is the value the host persists, not the label: labels change with translation
 * and short labels exist for the same section, so keying selection on text would break both.
 */
data class SectionTab(
    val id: String,
    val label: String,
    val icon: ImageVector
)

/**
 * The horizontal section switcher for a workspace whose views are peers, drawn as one
 * equal-width tab per section.
 *
 * Replaces `Row + horizontalScroll` around a strip of filter chips. That strip is why the
 * Gaming Tools workspace hid most of its own navigation: six chips with their full headings
 * measured 2274 px of content in a 954 px row on a 411 dp phone, so "Performance" was clipped
 * at the screen edge and the last two sections were off-screen with nothing but a cut-off chip
 * to suggest they existed. Equal-width tabs put every section on screen at once, in one row of
 * fixed height, so switching is always one tap and never a swipe followed by a tap.
 *
 * The label is `maxLines = 1` and may ellipsize at extreme widths; [SectionRail] is the
 * fallback for windows wide enough to give the labels their own column, and the section's own
 * heading is always drawn in the content pane, so an ellipsized tab never hides which view is
 * open. The whole row is a `selectableGroup` so TalkBack announces "tab, 2 of 6" and moves
 * between tabs with a swipe rather than reading six unrelated buttons.
 */
/**
 * Six captions share the row, so each gets a sixth of the width. Past this system font scale a
 * one-word caption ("Overlays") no longer fits and Compose ellipsizes it, which reads as a typo
 * rather than as a tab. Kept as a pure predicate so the threshold is unit-tested rather than
 * asserted by reading the source.
 */
internal fun sectionTabLabelsFit(fontScale: Float): Boolean = fontScale <= 1.15f

@Composable
fun SectionTabRow(
    sections: List<SectionTab>,
    selectedId: String,
    onSelect: (SectionTab) -> Unit,
    modifier: Modifier = Modifier,
    /** Minimum height of the whole row; each tab fills it. */
    height: Dp = 56.dp
) {
    // At a large system font scale six captions no longer fit a sixth of a phone's width and
    // Compose ellipsizes them ("Prof…", "Displ…"), which is worse than no caption: a clipped word
    // still reads as a word. Above this scale the tabs go icon-only and carry the label as their
    // content description, so TalkBack still announces "Display, tab, 2 of 6" while the eye gets
    // six unclipped targets. The section's own heading inside the pane names where you are.
    val largeText = !sectionTabLabelsFit(LocalDensity.current.fontScale)
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = height).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sections.forEach { section ->
            val selected = section.id == selectedId
            Column(
                modifier = Modifier
                    .weight(1f)
                    // Fitts: the target is the full cell, not the glyph. 56 dp of height and
                    // a sixth of the row's width stays above the 48 dp minimum even at 320 dp.
                    .heightIn(min = height)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(section) }
                    )
                    .then(
                        if (largeText) {
                            Modifier.semantics { contentDescription = section.label }
                        } else {
                            Modifier
                        }
                    )
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (largeText) 22.dp else 18.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (!largeText) {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * The vertical section switcher for expanded windows, where a tab row would stretch every tab
 * across half a metre of screen.
 *
 * Same selection contract as [SectionTabRow] — one tap, all sections visible — but stacked, so
 * a desktop or DeX window reads as a workspace with a navigation column instead of a phone
 * toolbar at the top. The selected row carries a filled pill and a 3 dp leading indicator; the
 * indicator is the non-colour cue (underline shape), so selection does not depend on tint alone.
 */
@Composable
fun SectionRail(
    sections: List<SectionTab>,
    selectedId: String,
    onSelect: (SectionTab) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 176.dp
) {
    Column(
        modifier = modifier.widthIn(min = 120.dp, max = width).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        sections.forEach { section ->
            val selected = section.id == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(section) })
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 18.dp)
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 9.dp).size(18.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

@Preview(name = "Tabs 411dp", widthDp = 411, showBackground = true)
@Composable
private fun SectionTabRowPhonePreview() {
    CatsmokerTheme {
        SectionTabRow(
            sections = PreviewSections,
            selectedId = "profile",
            onSelect = {},
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Preview(name = "Tabs 320dp", widthDp = 320, showBackground = true)
@Composable
private fun SectionTabRowSmallPreview() {
    CatsmokerTheme {
        SectionTabRow(
            sections = PreviewSections,
            selectedId = "display",
            onSelect = {},
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(name = "Rail", widthDp = 200, showBackground = true)
@Composable
private fun SectionRailPreview() {
    CatsmokerTheme {
        SectionRail(sections = PreviewSections, selectedId = "network", onSelect = {})
    }
}

private val PreviewSections = listOf(
    SectionTab("profile", "Profile", Icons.Default.Tune),
    SectionTab("display", "Display", Icons.Default.Monitor),
    SectionTab("speed", "Speed", Icons.Default.Speed),
    SectionTab("overlays", "Overlays", Icons.Default.Layers),
    SectionTab("network", "Network", Icons.Default.Wifi),
    SectionTab("cleanup", "Clean", Icons.Default.CleaningServices)
)
