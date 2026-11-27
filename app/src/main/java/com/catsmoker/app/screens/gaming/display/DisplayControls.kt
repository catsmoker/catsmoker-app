package com.catsmoker.app.screens.gaming.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.R
import com.catsmoker.app.platform.display.AttachedDisplay
import com.catsmoker.app.platform.display.FramePacing
import com.catsmoker.app.screens.gaming.components.ExpandableToolCard
import com.catsmoker.app.ui.components.ChipFlowRow

/**
 * The refresh-rate card.
 *
 * One compact control for ordinary use — pick a rate the display reports, apply it — over an
 * expandable technical block that names the mechanism that actually ran, what the device
 * reported as supported, and which execution identity each mechanism needs. The technical half
 * is folded away by default so the card is not a scrolling dashboard.
 *
 * Every rate shown comes from the display's own mode list. There is no catalogue of "nice"
 * rates in here: a rate the panel does not enumerate cannot be selected, because offering it
 * would be a promise the hardware has not made.
 */
@Composable
fun DisplayCard(viewModel: DisplayViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    ExpandableToolCard(
        title = stringResource(R.string.gt_display_title),
        subtitle = stringResource(R.string.gt_display_sub),
        icon = Icons.Default.Speed,
        // A pending revert is the one state that must not be folded away: the change is undone
        // in seconds unless the user answers.
        forceExpand = ui.awaitingConfirmation
    ) {
        DisplayContent(
            ui = ui,
            onSelectDisplay = viewModel::select,
            onApply = viewModel::apply,
            onRestore = viewModel::restore,
            onConfirm = viewModel::confirm,
            onRevert = viewModel::restore,
            onSavePreference = viewModel::savePreference,
            pacingOf = viewModel::pacingOf
        )
    }
}

@Composable
private fun DisplayContent(
    ui: DisplayUiState,
    onSelectDisplay: (Int) -> Unit,
    onApply: (Float) -> Unit,
    onRestore: () -> Unit,
    onConfirm: () -> Unit,
    onRevert: () -> Unit,
    onSavePreference: (Float) -> Unit,
    pacingOf: (Float) -> FramePacing?
) {
    val target = ui.selected
    if (target == null) {
        Text(
            stringResource(R.string.gt_display_no_external),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Which display: only shown when there is a choice, so a phone on its own is one line.
    if (ui.engine.displays.size > 1) {
        ChipFlowRow {
            ui.engine.displays.forEach { display ->
                FilterChip(
                    selected = display.id == target.id,
                    onClick = { onSelectDisplay(display.id) },
                    label = {
                        Text(
                            "${if (display.isExternal) stringResource(R.string.gt_display_external) else stringResource(R.string.gt_display_builtin)} · ${display.name}",
                            fontSize = 11.sp
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    DisplayFacts(target)

    val rates = target.rates
    if (rates.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.gt_display_available),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        ChipFlowRow {
            rates.forEach { rate ->
                FilterChip(
                    selected = target.currentHz?.let {
                        kotlin.math.abs(it - rate) <= 0.6f
                    } == true,
                    onClick = { onApply(rate) },
                    enabled = !ui.busy,
                    label = {
                        Column {
                            Text("${DisplayPlanner.formatHz(rate)} Hz", fontSize = 11.sp)
                            pacingOf(rate)?.takeIf { it == FramePacing.UNEVEN }?.let {
                                Text(
                                    stringResource(R.string.gt_display_pacing_uneven),
                                    fontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onRestore, enabled = !ui.busy) {
            Text(
                stringResource(
                    if (ui.busy) R.string.gt_display_restoring else R.string.gt_display_restore
                ),
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { onSavePreference(target.currentHz ?: rates.lastOrNull() ?: return@TextButton) }) {
            Text(stringResource(R.string.gt_display_save_pref), fontSize = 11.sp)
        }
    }
    Text(
        ui.savedHz?.let {
            stringResource(
                R.string.gt_display_saved_pref,
                DisplayPlanner.formatHz(it),
                ui.savedScope
            )
        } ?: stringResource(R.string.gt_display_saved_none),
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    ui.engine.revertSeconds?.let { seconds ->
        Spacer(Modifier.height(12.dp))
        ConfirmBanner(seconds = seconds, onKeep = onConfirm, onRevert = onRevert)
    }

    ui.engine.lastReport?.let { report ->
        Spacer(Modifier.height(10.dp))
        Text(report.summary, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Spacer(Modifier.height(14.dp))
    TechnicalDetails(ui)
}

/** The measured facts of one display: what it runs now, at what size, in which mode. */
@Composable
private fun DisplayFacts(display: AttachedDisplay) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Fact(
            label = stringResource(R.string.gt_display_now),
            value = display.currentHz?.let { "${DisplayPlanner.formatHz(it)} Hz" }
                ?: stringResource(R.string.gt_display_unavailable)
        )
        display.activeMode?.let { mode ->
            Fact(
                label = stringResource(R.string.gt_display_resolution),
                value = "${mode.width}x${mode.height}"
            )
            Fact(
                label = stringResource(R.string.gt_display_mode_label),
                value = stringResource(R.string.gt_display_mode_id, mode.id)
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * The countdown for a mode switch.
 *
 * It exists because a mode the panel or monitor cannot drive shows nothing at all, and a black
 * screen has no button on it. The revert is automatic; this banner only lets the user say "keep
 * it" before that happens.
 */
@Composable
private fun ConfirmBanner(seconds: Int, onKeep: () -> Unit, onRevert: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp)
    ) {
        Text(
            stringResource(R.string.gt_display_confirm_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.gt_display_confirm_text, seconds),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = true, onClick = onKeep, label = {
                Text(stringResource(R.string.gt_display_keep), fontSize = 11.sp)
            })
            FilterChip(selected = false, onClick = onRevert, label = {
                Text(stringResource(R.string.gt_display_revert_now), fontSize = 11.sp)
            })
        }
    }
}

/**
 * The technical half: which identity can do what, and what this device answered.
 *
 * Folded by default because it answers a question most people do not ask — but it is the answer
 * to "why is there no 144 Hz here", so it is never hidden behind a dialog.
 */
@Composable
private fun TechnicalDetails(ui: DisplayUiState) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.gt_display_technical),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.height(16.dp)
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val probe = ui.engine.probe
            TechnicalLine(
                label = stringResource(R.string.gt_display_backend),
                value = if (probe.availableBackends.isEmpty()) {
                    stringResource(R.string.gt_display_backend_settings)
                } else {
                    probe.availableBackends.joinToString(" · ") { it.name }
                }
            )
            probe.reason?.let { TechnicalLine(label = stringResource(R.string.gt_display_capabilities), value = it) }
            if (!ui.engine.backendIncludesHiddenInterfaces) {
                TechnicalLine(
                    label = stringResource(R.string.gt_display_capabilities),
                    value = "settings only in this distribution"
                )
            }
            ui.engine.lastReport?.details?.forEach { line ->
                Text(line, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.gt_display_identity_matrix),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            UidMatrix.rows.forEach { row ->
                Text(
                    "${row.mechanism} — app=${yesNo(row.app)} shell=${yesNo(row.shell)} " +
                        "system=${yesNo(row.system)} root=${yesNo(row.root)}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(row.note, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TechnicalLine(label: String, value: String) {
    Row {
        Text("$label: ", fontSize = 9.sp, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
