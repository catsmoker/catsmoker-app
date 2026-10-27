package com.catsmoker.app.features.gamingtools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.components.CatsmokerOutlinedButton

/** One picked app in either Gaming Mode app picker, resolved to its display label. */
data class GamingModePickedApp(
    val label: String,
    val packageName: String
)

/** The names shown while an app picker is collapsed, plus how many were left out. */
data class GamingModePickedPreview(
    val labels: List<String>,
    val hiddenCount: Int
)

/**
 * Resolves a picked set for display, never for storage.
 *
 * A package the picker source does not know still renders as the package name instead of
 * disappearing — the same rule the old standalone card followed. Sorting is by package so the row
 * order cannot change just because a picker label changed.
 */
internal fun gamingModePickedApps(
    picked: Set<String>,
    nameByPackage: Map<String, String>
): List<GamingModePickedApp> = picked.sorted().map { pkg ->
    GamingModePickedApp(label = nameByPackage[pkg] ?: pkg, packageName = pkg)
}

/**
 * An app picker's fold, count and collapsed preview, kept pure so they can be tested without a
 * device. Folding only changes what is drawn: the stored set is never passed here for mutation.
 */
internal object GamingModeAppPicker {
    /** Collapsed until the user asks for the full list. */
    const val DEFAULT_EXPANDED = false

    /** How many names fit on the collapsed summary line before "+N more". */
    const val PREVIEW_LIMIT = 3

    /**
     * The configured app count. `picked` is normally a set, but accepting a collection and
     * de-duplicating keeps the count honest if a caller ever hands over a list with repeats.
     */
    fun count(picked: Collection<String>): Int = picked.distinct().size

    /** Flips only the disclosure state; no app is added, removed or launched. */
    fun toggle(expanded: Boolean): Boolean = !expanded

    fun preview(
        rows: List<GamingModePickedApp>,
        limit: Int = PREVIEW_LIMIT
    ): GamingModePickedPreview {
        val shown = rows.take(limit.coerceAtLeast(1))
        return GamingModePickedPreview(
            labels = shown.map { it.label },
            hiddenCount = (rows.size - shown.size).coerceAtLeast(0)
        )
    }
}

/**
 * The one layout both Gaming Mode app lists use.
 *
 * "Apps kept running" and "Extra suspended apps" edit two sides of the same suspend sweep, so they
 * must read as one control: a heading with its configured count, a fixed subtitle, an empty/status
 * line, a short collapsed name preview, one Show/Hide disclosure, one full-width Add button, one
 * privilege notice and one explanation. The only per-list content is the optional [rowBadge], which
 * the freeze list uses to say whether a configured app has actually been suspended this session.
 *
 * The body starts collapsed and the expanded rows live in a bounded scrolling area, so a large list
 * cannot make the Gaming Mode card indefinitely tall. Folding or unfolding never edits a stored set.
 *
 * @param count the distinct configured count, also used for [countDescription].
 * @param statusText an optional line under the subtitle (the freeze list reports selected vs frozen
 *   here). Empty lists show [emptyText] instead.
 * @param rowBadge optional trailing badge on each expanded row, before Remove.
 * @param canEdit whether root or Shizuku is available. Adding is disabled without one, with the
 *   reason stated on the card. Removing a pick needs no privilege because it only edits this app's
 *   own list.
 */
@Composable
internal fun GamingModeAppPickerSection(
    title: String,
    subtitle: String,
    count: Int,
    countDescription: String,
    rows: List<GamingModePickedApp>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    emptyText: String,
    showText: String,
    hideText: String,
    addText: String,
    removeText: String,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    canEdit: Boolean,
    explainerLines: List<String>,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    rowBadge: (@Composable (GamingModePickedApp) -> Unit)? = null
) {
    val preview = remember(rows) { GamingModeAppPicker.preview(rows) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // The count belongs to the stored set, not to the rows currently on screen. The
            // localized "N app(s) ..." description is the accessibility label for this badge.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .clearAndSetSemantics { contentDescription = countDescription }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        when {
            rows.isEmpty() -> Text(
                text = emptyText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            else -> {
                if (statusText != null) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (expanded) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(rows, key = { it.packageName }) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.label,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                rowBadge?.invoke(row)
                                TextButton(onClick = { onRemove(row.packageName) }) {
                                    Text(removeText)
                                }
                            }
                        }
                    }
                } else {
                    // The phrasing is generic ("A, B and 2 more"); the resource keeps its
                    // historic name because both pickers share it.
                    val summary = if (preview.hiddenCount > 0) {
                        stringResource(
                            R.string.gt_keep_summary_more,
                            preview.labels.joinToString(", "),
                            preview.hiddenCount
                        )
                    } else {
                        preview.labels.joinToString(", ")
                    }
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (rows.isNotEmpty()) {
            TextButton(onClick = onToggleExpanded) {
                Text(if (expanded) hideText else showText)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        CatsmokerOutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit
        ) {
            Text(addText)
        }
        if (!canEdit) {
            Spacer(modifier = Modifier.height(8.dp))
            RequirementNotice(stringResource(R.string.gt_needs_root_shizuku_close))
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleExplainer(
            title = stringResource(R.string.gt_explainer_what),
            lines = explainerLines
        )
    }
}
