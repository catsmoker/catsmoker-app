package com.catsmoker.app.screens.gaming.mode

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catsmoker.app.R

internal sealed interface SuspensionRescueDialogState {
    data object Scanning : SuspensionRescueDialogState
    data object Restoring : SuspensionRescueDialogState
    data class Showing(
        val apps: List<GamingModePickedApp>,
        val available: Int? = null,
        val unconfirmed: Int = 0
    ) : SuspensionRescueDialogState
    data class Blocked(@StringRes val reason: Int) : SuspensionRescueDialogState
}

@Composable
internal fun SuspensionRescueDialog(
    state: SuspensionRescueDialogState,
    onDismiss: () -> Unit,
    onRestore: (Set<String>) -> Unit
) {
    var selected by remember(state) { mutableStateOf(emptySet<String>()) }
    var search by rememberSaveable { mutableStateOf("") }
    val restoring = state == SuspensionRescueDialogState.Restoring
    AlertDialog(
        onDismissRequest = { if (!restoring) onDismiss() },
        title = { Text(stringResource(R.string.gt_gm_rescue_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    SuspensionRescueDialogState.Scanning, SuspensionRescueDialogState.Restoring -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text(stringResource(if (restoring) R.string.gt_gm_rescue_working else R.string.gt_picker_loading))
                        }
                    }
                    is SuspensionRescueDialogState.Blocked -> Text(stringResource(state.reason))
                    is SuspensionRescueDialogState.Showing -> {
                        state.available?.let { available ->
                            Text(stringResource(R.string.gt_gm_rescue_result, available, state.unconfirmed))
                            if (state.unconfirmed > 0) {
                                Text(stringResource(R.string.gt_gm_rescue_retry), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (state.apps.isEmpty()) {
                            if (state.available == null) Text(stringResource(R.string.gt_gm_rescue_empty))
                        } else {
                            Text(stringResource(R.string.gt_gm_rescue_explain), style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                label = { Text(stringResource(R.string.gt_picker_search)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            val query = search.trim()
                            val visible = state.apps.filter {
                                it.label.contains(query, ignoreCase = true) ||
                                    it.packageName.contains(query, ignoreCase = true)
                            }
                            if (visible.isEmpty()) Text(stringResource(R.string.gt_picker_none))
                            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                                items(visible, key = { it.packageName }) { app ->
                                    val checked = app.packageName in selected
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .toggleable(checked, role = Role.Checkbox) { enabled ->
                                                selected = if (enabled) selected + app.packageName
                                                else selected - app.packageName
                                            }
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(checked = checked, onCheckedChange = null)
                                        Column(Modifier.weight(1f)) {
                                            Text(app.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                app.packageName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state is SuspensionRescueDialogState.Showing && state.apps.isNotEmpty()) {
                TextButton(enabled = selected.isNotEmpty(), onClick = { onRestore(selected) }) {
                    Text(stringResource(R.string.gt_gm_rescue_confirm, selected.size))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !restoring) {
                Text(stringResource(R.string.info_close))
            }
        }
    )
}
