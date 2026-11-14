package com.catsmoker.app.screens.editgamefiles.games.roblox

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.R
import com.catsmoker.app.ui.components.CardHeading
import com.catsmoker.app.ui.components.CatsmokerButton
import com.catsmoker.app.ui.components.CatsmokerOutlinedButton
import com.catsmoker.app.ui.components.ChipFlowRow
import com.catsmoker.app.ui.components.SectionCard

/** Embedded only: File Engineering owns scrolling, selection and the header. */
@Composable
fun RobloxRoute() {
    val viewModel: RobloxViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    RobloxScreen(state, viewModel::select, viewModel::refresh, viewModel::apply,
        viewModel::restore, viewModel::closeGame)
}

@Composable
internal fun RobloxScreen(
    state: RobloxUiState,
    onSelect: (Int) -> Unit,
    onRefresh: () -> Unit,
    onApply: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit
) {
    var confirmation by remember { mutableStateOf<RobloxOperation?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardHeading(stringResource(R.string.rb_title))
                Text(stringResource(R.string.rb_warning), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                Text(RobloxConfig.PATH, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.rb_target))
                ChipFlowRow {
                    RobloxConfig.targets.takeLast(1).forEach { fps ->
                        FilterChip(selected = state.selected == fps, onClick = { onSelect(fps) },
                            enabled = !state.busy, label = { Text(fps.toString()) })
                    }
                }
                val status = state.status
                Text(when {
                    status == null -> stringResource(R.string.rb_unread)
                    status.file.encoded == null -> stringResource(R.string.rb_absent)
                    status.invalidConfig -> stringResource(R.string.rb_invalid)
                    status.target == null -> stringResource(R.string.rb_no_target)
                    else -> stringResource(R.string.rb_current, status.target)
                }, style = MaterialTheme.typography.bodySmall)
                if (status != null && !status.invalidConfig && !status.conflict) {
                    Text(stringResource(if (status.target == state.selected) R.string.gf_no_pending_changes else R.string.gf_unsaved),
                        style = MaterialTheme.typography.bodySmall)
                }
                if (state.installed == false) Text(stringResource(R.string.rb_not_installed))
                if (status?.conflict == true) Text(stringResource(R.string.rb_conflict),
                    color = MaterialTheme.colorScheme.error)
                state.problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error) }
                CatsmokerButton(onClick = { confirmation = RobloxOperation.APPLY },
                    modifier = Modifier.fillMaxWidth(), enabled = state.canApply,
                    isLoading = state.operation == RobloxOperation.APPLY) {
                    Text(stringResource(R.string.gf_apply))
                }
                CatsmokerOutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy, isLoading = state.operation == RobloxOperation.REFRESH) {
                    Text(stringResource(R.string.logs_refresh))
                }
            }
        }
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardHeading(stringResource(R.string.rb_recovery))
                Text(stringResource(if (state.status?.hasBackup == true) R.string.rb_backup_present else R.string.rb_backup_note),
                    style = MaterialTheme.typography.bodySmall)
                CatsmokerOutlinedButton(onClick = { confirmation = RobloxOperation.RESTORE },
                    modifier = Modifier.fillMaxWidth(), enabled = !state.busy && state.status?.canRestore == true,
                    isLoading = state.operation == RobloxOperation.RESTORE) {
                    Text(stringResource(R.string.gf_restore_backup))
                }
                Text(stringResource(R.string.rb_close_note), style = MaterialTheme.typography.bodySmall)
                CatsmokerOutlinedButton(onClick = { confirmation = RobloxOperation.CLOSE },
                    modifier = Modifier.fillMaxWidth(), enabled = !state.busy && state.status != null && state.installed != false,
                    isLoading = state.operation == RobloxOperation.CLOSE) {
                    Text(stringResource(R.string.rb_close))
                }
            }
        }
    }
    confirmation?.let { operation ->
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(stringResource(when (operation) {
                RobloxOperation.APPLY -> R.string.rb_apply_title
                RobloxOperation.RESTORE -> R.string.gf_restore_backup
                else -> R.string.rb_close
            })) },
            text = { Text(stringResource(when (operation) {
                RobloxOperation.APPLY -> R.string.rb_apply_confirm
                RobloxOperation.RESTORE -> R.string.rb_restore_confirm
                else -> R.string.rb_close_confirm
            })) },
            confirmButton = {
                CatsmokerButton(onClick = {
                    confirmation = null
                    when (operation) {
                        RobloxOperation.APPLY -> onApply()
                        RobloxOperation.RESTORE -> onRestore()
                        RobloxOperation.CLOSE -> onClose()
                        else -> Unit
                    }
                }) { Text(stringResource(R.string.rb_continue)) }
            },
            dismissButton = {
                CatsmokerOutlinedButton(onClick = { confirmation = null }) { Text(stringResource(R.string.gf_cancel)) }
            })
    }
}

private fun problemText(problem: RobloxProblem): Int = when (problem) {
    RobloxProblem.NO_PRIVILEGE -> R.string.rb_privilege
    RobloxProblem.UNSAFE_FILE -> R.string.rb_unsafe
    RobloxProblem.INVALID_CONFIG -> R.string.rb_invalid
    RobloxProblem.CONFLICT -> R.string.rb_conflict
    RobloxProblem.BACKUP -> R.string.rb_backup_error
    RobloxProblem.IO -> R.string.rb_io_error
    RobloxProblem.VERIFY -> R.string.rb_verify_error
}
