package com.catsmoker.app.screens.editgamefiles.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.R
import com.catsmoker.app.screens.editgamefiles.EditGameFilesViewModel
import com.catsmoker.app.screens.editgamefiles.model.GameType
import com.catsmoker.app.screens.editgamefiles.model.ConfigControl
import com.catsmoker.app.screens.editgamefiles.model.ConfigField
import com.catsmoker.app.screens.editgamefiles.model.ConfigUnit
import com.catsmoker.app.screens.editgamefiles.storage.AdditionalConfigStorage
import com.catsmoker.app.ui.components.CatsmokerButton
import com.catsmoker.app.ui.components.CatsmokerOutlinedButton

/** File Engineering owns navigation/scrolling. A keyed VM pins pending work to its original game. */
@Composable
fun AdditionalConfigRoute(
    game: GameType,
    document: EditGameFilesViewModel.ScopedDocument?,
    onPickDocument: (Int) -> Unit,
    onDocumentHandled: (EditGameFilesViewModel.ScopedDocument) -> Unit
) {
    val viewModel: AdditionalConfigViewModel = hiltViewModel(key = "config-${game.name}")
    viewModel.bind(game)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(game, document, state.busy) {
        if (document?.game == game && !state.busy) {
            viewModel.loadPickedDocument(document.fileIndex, document.uri)
            onDocumentHandled(document)
        }
    }
    val spec = state.spec ?: return
    val file = spec.files[state.fileIndex]
    var confirmation by remember(game, state.fileIndex) { mutableStateOf<Boolean?>(null) }
    val uriHandler = LocalUriHandler.current
    GameEditorLayout {
        GameEditorSection(file.name) {
            if (spec.files.size > 1) {
                com.catsmoker.app.ui.components.ChipFlowRow {
                    spec.files.forEachIndexed { index, config ->
                        FilterChip(selected = index == state.fileIndex, onClick = { viewModel.selectFile(index) },
                            enabled = !state.busy, label = { Text(config.name) })
                    }
                }
            }
            Text(stringResource(if (file.rootOnly) R.string.ce_root_only else if (file.paths.isEmpty())
                R.string.ce_document_only else R.string.ce_privileged), style = MaterialTheme.typography.bodySmall)
            file.paths.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (file.paths.isNotEmpty()) {
                CatsmokerOutlinedButton(onClick = { viewModel.load() }, modifier = Modifier.fillMaxWidth(),
                    isLoading = state.operation == ConfigEditorOperation.READ,
                    loadingLabel = stringResource(R.string.ce_working),
                    enabled = !state.busy) { Text(stringResource(R.string.ce_read_direct)) }
            }
            CatsmokerOutlinedButton(onClick = { onPickDocument(state.fileIndex) },
                modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
                isLoading = file.paths.isEmpty() && state.operation == ConfigEditorOperation.READ,
                loadingLabel = stringResource(R.string.ce_working)) {
                Text(stringResource(R.string.ce_choose, file.name))
            }
            if (state.loaded?.source is AdditionalConfigStorage.Source.Document || file.paths.isEmpty()) {
                Text(stringResource(R.string.ce_document_note), style = MaterialTheme.typography.bodySmall)
            }
            if (state.busy) Text(stringResource(R.string.ce_working))
            state.status?.let { Text(stringResource(R.string.gf_failed, it), color = MaterialTheme.colorScheme.error) }
            state.loaded?.source?.let { source ->
                Text(when (source) {
                    is AdditionalConfigStorage.Source.Document -> source.uri.toString()
                    is AdditionalConfigStorage.Source.Root -> source.path
                    is AdditionalConfigStorage.Source.Shizuku -> source.path
                }, style = MaterialTheme.typography.bodySmall)
            }
            if (state.verified) Text(stringResource(R.string.ce_verified))
            TextButton(onClick = { uriHandler.openUri(spec.evidenceUrl) }) {
                Text(stringResource(R.string.ce_evidence))
            }
        }
        val loaded = state.loaded
        GameEditorSection(stringResource(R.string.fe_controls)) {
            file.fields.forEach { field ->
                val available = loaded?.document?.values?.containsKey(field.id) == true
                val value = state.edits[field.id].orEmpty()
                GameConfigControl(field, value, available && !state.busy,
                    onChange = { viewModel.edit(field.id, it) })
                if (!available && field.control != ConfigControl.Unsupported) {
                    Text(stringResource(if (loaded == null) R.string.fe_load_first else R.string.ce_unavailable),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (loaded != null) {
            GameEditorSection(stringResource(R.string.gf_deploy)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.closed, onCheckedChange = viewModel::confirmClosed, enabled = !state.busy)
                    Text(stringResource(R.string.ce_closed), modifier = Modifier.weight(1f))
                }
                val valid = file.fields.filter { it.id in loaded.document.values }
                    .all { state.edits[it.id] == loaded.document.values[it.id] || it.acceptsEdit(state.edits[it.id].orEmpty()) }
                val changed = state.edits.any { (id, value) -> loaded.document.values[id] != value }
                Text(stringResource(if (changed) R.string.gf_unsaved else R.string.gf_no_pending_changes),
                    style = MaterialTheme.typography.bodySmall)
                CatsmokerButton(onClick = { confirmation = false }, modifier = Modifier.fillMaxWidth(),
                    isLoading = state.operation == ConfigEditorOperation.APPLY,
                    loadingLabel = stringResource(R.string.ce_working),
                    enabled = state.closed && valid && changed && !state.busy) {
                    Text(stringResource(R.string.ce_apply))
                }
                CatsmokerOutlinedButton(onClick = { confirmation = true }, modifier = Modifier.fillMaxWidth(),
                    isLoading = state.operation == ConfigEditorOperation.RESTORE,
                    loadingLabel = stringResource(R.string.ce_working),
                    enabled = state.closed && !state.busy) { Text(stringResource(R.string.ce_restore)) }
            }
        }
    }
    confirmation?.let { restore ->
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(stringResource(if (restore) R.string.ce_restore else R.string.ce_apply)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.ce_confirm, spec.game.displayName, file.name))
                    if (restore && state.edits.any { (id, value) -> state.loaded?.document?.values?.get(id) != value }) {
                        Text(stringResource(R.string.ce_discard))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { confirmation = null; viewModel.save(restore) }) {
                Text(stringResource(R.string.ce_continue))
            } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}

@Composable
private fun GameConfigControl(field: ConfigField, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    // Exact preset identity is part of the target; don't silently edit a different profile.
    if (field.scope.substringAfterLast('/') in setOf("BatterySaverPreset", "GraphicsPreset", "PerformancePreset")) {
        Text(field.scope.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall)
    }
    when (val control = field.control) {
        ConfigControl.Unsupported -> {
            Text(field.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(if (field.name.contains("fps", true) || field.name.contains("framerate", true))
                R.string.fe_unlock_unsupported else R.string.fe_mapping_unconfirmed), style = MaterialTheme.typography.bodySmall)
        }
        is ConfigControl.Toggle -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(field.name, modifier = Modifier.weight(1f))
                Switch(checked = value == control.on, enabled = enabled && value in listOf(control.off, control.on),
                    onCheckedChange = { onChange(if (it) control.on else control.off) })
            }
        }
        is ConfigControl.Number -> {
            OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
                enabled = enabled, singleLine = true, label = { Text(field.name) },
                isError = enabled && !field.acceptsEdit(value),
                supportingText = { Text(stringResource(when (control.unit) {
                    ConfigUnit.PIXELS -> R.string.fe_pixels
                    ConfigUnit.BLOCKS -> R.string.fe_blocks
                    ConfigUnit.COUNT -> R.string.fe_count
                })) })
        }
        is ConfigControl.Choice -> {
            Text(field.name)
            com.catsmoker.app.ui.components.ChipFlowRow {
                control.values.forEach { option ->
                    FilterChip(selected = value == option, enabled = enabled,
                        onClick = { onChange(option) }, label = { Text(option) })
                }
            }
        }
        is ConfigControl.Unlock -> CatsmokerOutlinedButton(onClick = { onChange(control.value) },
            enabled = enabled && value != control.value, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.fe_unlock))
        }
    }
}
