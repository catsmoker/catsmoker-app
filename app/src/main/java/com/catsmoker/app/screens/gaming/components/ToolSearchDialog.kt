package com.catsmoker.app.screens.gaming.components

import com.catsmoker.app.R

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Find a tool by name instead of by knowing which of six sections it lives in.
 *
 * The workspace is organised by kind of work (Profile, Display, Speed, Overlays, Network, Clean),
 * which is right for browsing and wrong for finding: "refresh rate" is under Display, DNS under
 * Network, the cleaner under Clean, and nothing on screen tells you that until you have opened
 * them. This is the recognition-over-recall fix — one field, the tool's own translated name and
 * subtitle as the match text, and the section it lives in named on the row so the result does not
 * arrive as a mystery jump.
 *
 * It deliberately navigates rather than editing: this finds *where* a control is, it does not
 * become a second control surface.
 */
@Composable
fun ToolSearchDialog(
    onDismiss: () -> Unit,
    onOpenSection: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Rebuilt per composition: the labels are string resources, so a language change has to
    // rebuild them. Fifteen entries cost nothing next to recomposing the dialog itself.
    val entries = toolSearchCatalog()
    val matches = remember(query, entries) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            entries
        } else {
            entries.filter { entry ->
                entry.label.contains(needle, ignoreCase = true) ||
                    entry.hint.contains(needle, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gt_search_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.gt_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (matches.isEmpty()) {
                    Text(
                        stringResource(R.string.gt_search_no_match),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    // Bounded height, lazy list: the catalog grows with the workspace and the
                    // dialog must not become a scroll inside a dialog's own scroll.
                    LazyColumn(modifier = Modifier.padding(top = 8.dp).heightIn(max = 360.dp)) {
                        items(matches, key = { it.label + it.sectionId }) { entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        onOpenSection(entry.sectionId)
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = entry.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    // The section name is the answer to "where is it", so it is
                                    // on the row rather than discovered after tapping.
                                    text = if (entry.hint.isBlank()) {
                                        stringResource(entry.sectionLabel)
                                    } else {
                                        "${stringResource(entry.sectionLabel)} · ${entry.hint}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sys_cancel)) }
        }
    )
}

/** One findable control: what it is called, where it lives, and extra words that should find it. */
private data class ToolSearchEntry(
    val label: String,
    @StringRes val sectionLabel: Int,
    val sectionId: String,
    val hint: String
)

/**
 * The catalog, built from the strings the workspace already renders.
 *
 * Kept as a list rather than derived from the section composables: those are lambdas inside the
 * screen, not data, and inventing a registry to generate this would be a much larger change than
 * the problem justifies. When a tool is renamed its entry is renamed with it — the labels are the
 * same resources, not copies.
 */
@Composable
private fun toolSearchCatalog(): List<ToolSearchEntry> {
    return listOf(
        ToolSearchEntry(
            label = stringResource(R.string.gt_gm_title),
            sectionLabel = R.string.gt_tab_profile,
            sectionId = "PROFILE",
            hint = stringResource(R.string.gt_section_gaming_mode)
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_fp_title),
            sectionLabel = R.string.gt_tab_profile,
            sectionId = "PROFILE",
            hint = ""
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_tool_screen_title),
            sectionLabel = R.string.gt_tab_display,
            sectionId = "GRAPHICS",
            hint = ""
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_display_title),
            sectionLabel = R.string.gt_tab_display,
            sectionId = "GRAPHICS",
            hint = stringResource(R.string.gt_display_sub)
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_cpu_policies),
            sectionLabel = R.string.gt_tab_speed,
            sectionId = "PERFORMANCE",
            hint = ""
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_tool_sound_title),
            sectionLabel = R.string.gt_tab_speed,
            sectionId = "PERFORMANCE",
            hint = ""
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_tool_booster_title),
            sectionLabel = R.string.gt_tab_speed,
            sectionId = "PERFORMANCE",
            hint = ""
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_tool_devopts_title),
            sectionLabel = R.string.gt_tab_speed,
            sectionId = "PERFORMANCE",
            hint = ""
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_tool_fps_title),
            sectionLabel = R.string.gt_tab_overlays,
            sectionId = "OVERLAYS",
            hint = ""
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_tool_crosshair_title),
            sectionLabel = R.string.gt_tab_overlays,
            sectionId = "OVERLAYS",
            hint = ""
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_tool_dnd_title),
            sectionLabel = R.string.gt_tab_network,
            sectionId = "NETWORK",
            hint = ""
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_tool_net_title),
            sectionLabel = R.string.gt_tab_network,
            sectionId = "NETWORK",
            hint = ""
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_tool_dns_title),
            sectionLabel = R.string.gt_tab_network,
            sectionId = "NETWORK",
            hint = ""
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_tool_afs_title),
            sectionLabel = R.string.gt_tab_network,
            sectionId = "NETWORK",
            hint = ""
        ),
        ToolSearchEntry(
            label = stringResource(R.string.gt_tool_cleaner_title),
            sectionLabel = R.string.gt_tab_cleanup,
            sectionId = "MAINTENANCE",
            hint = ""
        )
    )
}
