package com.catsmoker.app.features.spoofdevice

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.components.ScreenScaffold
import com.catsmoker.app.shared.ui.components.SectionCard
import com.catsmoker.app.shared.ui.theme.CatsmokerSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppAssignmentScreen(
    uiState: SpoofDeviceViewModel.UiState,
    onLoadApps: () -> Unit,
    onAssignProfile: (String, String?) -> Unit,
    onForceStop: suspend (String) -> Boolean,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<SpoofDeviceViewModel.AppEntry?>(null) }
    // An assignment held for exclusivity confirmation: non-null only between
    // the picker's Assign tap and the confirm dialog's verdict.
    var pendingAssign by remember { mutableStateOf<PendingAssign?>(null) }

    LaunchedEffect(Unit) {
        onLoadApps()
    }

    val filteredApps = remember(uiState.apps, searchQuery) {
        uiState.apps.filter {
            it.label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
        }.sortedWith(
            compareByDescending<SpoofDeviceViewModel.AppEntry> { it.assignedProfileName != null }
                .thenBy { it.label.lowercase() }
        )
    }

    ScreenScaffold(
        title = stringResource(R.string.spoof_apps_title),
        subtitle = stringResource(R.string.spoof_apps_subtitle),
        onBack = onBack
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SectionCard {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.spoof_search_apps)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoadingApps) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppItem(
                            app = app,
                            onClick = { selectedApp = app },
                            onForceStop = { onForceStop(app.packageName) }
                        )
                    }
                }
            }
        }
    }

    selectedApp?.let { app ->
        AssignProfileDialog(
            app = app,
            uiState = uiState,
            onAssignProfile = { profileId ->
                // The exclusivity gate for the Xposed direction lives one level
                // out: the picker dialog stays dumb, and a confirm wraps the
                // commit only when a Magisk module is installed.
                if (
                    SpoofMethodConflict.needsAssignConfirm(
                        profileId = profileId,
                        magisk = uiState.magiskModuleStatus
                    )
                ) {
                    pendingAssign = PendingAssign(app.packageName, profileId)
                } else {
                    onAssignProfile(app.packageName, profileId)
                    selectedApp = null
                }
            },
            onDismiss = { selectedApp = null }
        )
    }
    // Confirming commits the pending assignment; anything else drops it and
    // closes the picker too, so no half-chosen state lingers.
    pendingAssign?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                pendingAssign = null
                selectedApp = null
            },
            title = { Text(stringResource(R.string.spoof_method_confirm_assign_title)) },
            text = { Text(stringResource(R.string.spoof_method_confirm_assign_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onAssignProfile(pending.packageName, pending.profileId)
                    pendingAssign = null
                    selectedApp = null
                }) { Text(stringResource(R.string.spoof_method_use_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingAssign = null
                    selectedApp = null
                }) { Text(stringResource(R.string.spoof_action_cancel)) }
            }
        )
    }
}

/**
 * An assignment waiting on the method-exclusivity confirm: null means nothing
 * is pending. Kept outside the ViewModel because it is pure UI transit —
 * process death drops an unconfirmed tap rather than committing it later.
 */
private data class PendingAssign(val packageName: String, val profileId: String?)

/**
 * The per-app assignment dialog answers exactly one question: which spoofing
 * PROFILE should this app use. A direct single-choice list with an explicit
 * Assign confirmation — no nested pickers, no tier controls.
 *
 * A package may still carry a frame-rate ladder built by an older version (see
 * `SpoofRepository.rateAssignments`): such a ladder keeps resolving through the
 * normal publish path and is named as the current state, but this dialog neither
 * builds nor edits ladders — assigning a profile here replaces the ladder, and
 * clearing the assignment leaves the ladder alone.
 */
@Composable
private fun AssignProfileDialog(
    app: SpoofDeviceViewModel.AppEntry,
    uiState: SpoofDeviceViewModel.UiState,
    onAssignProfile: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val hasLadder = uiState.rateAssignments[app.packageName].orEmpty().isNotEmpty()
    val currentId = uiState.assignments[app.packageName]

    var selectedId by remember(app.packageName) { mutableStateOf(currentId) }

    val currentLabel = when {
        hasLadder -> stringResource(R.string.spoof_ladder_label)
        currentId != null -> uiState.profiles.firstOrNull { it.id == currentId }?.name
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spoof_assign_title, app.label)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(
                        R.string.spoof_assign_current,
                        currentLabel ?: stringResource(R.string.spoof_assign_none)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Direct profile choice: tapping a row selects, Assign commits.
                ProfileRadioRow(
                    selected = selectedId == null,
                    label = stringResource(R.string.spoof_assign_none),
                    onSelect = { selectedId = null }
                )
                uiState.profiles.forEach { profile ->
                    ProfileRadioRow(
                        selected = selectedId == profile.id,
                        label = profile.name,
                        onSelect = { selectedId = profile.id }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAssignProfile(selectedId)
            }) { Text(stringResource(R.string.spoof_action_assign)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.spoof_action_cancel)) }
        }
    )
}

@Composable
private fun ProfileRadioRow(selected: Boolean, label: String, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onSelect() }.padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Per-app row in the assignment list.
 *
 * Shows the app icon, label, package name, assigned profile (if any), and a
 * [ForceStopButton] so a running game can be restarted into its new profile.
 */
@Composable
fun AppItem(
    app: SpoofDeviceViewModel.AppEntry,
    onClick: () -> Unit,
    onForceStop: suspend (String) -> Boolean
) {
    SectionCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.label,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (app.assignedProfileName != null) {
                Text(
                    text = app.assignedProfileName,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
            ForceStopButton(
                packageName = app.packageName,
                appLabel = app.label,
                onForceStop = onForceStop
            )
        }
    }
}

/**
 * Force-stop action for one app row.
 *
 * Shows a spinner while `am force-stop` actually runs, then the real outcome: a check on
 * success or a cross on failure (each for 1.2 s), with the matching toast from
 * [SpoofDeviceViewModel.forceStopApp]. Needs root or Shizuku — from an unprivileged shell
 * Android refuses to stop another app, and that refusal is what the cross reports.
 */
@Composable
private fun ForceStopButton(
    packageName: String,
    appLabel: String,
    onForceStop: suspend (String) -> Boolean
) {
    // Keyed by package: the list is filtered by the search box, and an unkeyed remember
    // would leave a checkmark attached to whichever app lands on the same index.
    var state by remember(packageName) { mutableStateOf<ForceStopState>(ForceStopState.Idle) }
    var confirming by remember(packageName) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isLoading = state == ForceStopState.Loading
    val showCheck = state == ForceStopState.Success
    val showError = state == ForceStopState.Error

    val containerColor = when {
        showError -> MaterialTheme.colorScheme.errorContainer
        showCheck -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        showError -> MaterialTheme.colorScheme.onErrorContainer
        showCheck -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = { if (!isLoading) confirming = true },
        enabled = !isLoading,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            // Keep this row's colours while busy instead of the default faded pair.
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor
        )
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = contentColor
            )
            showCheck -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.spoof_toast_force_stopped, appLabel),
                tint = contentColor
            )
            showError -> Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.spoof_action_force_stop),
                tint = contentColor
            )
            else -> Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = stringResource(R.string.spoof_action_force_stop),
                tint = contentColor
            )
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.spoof_force_stop_confirm_title, appLabel)) },
            text = { Text(stringResource(R.string.spoof_force_stop_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    state = ForceStopState.Loading
                    scope.launch {
                        state = if (onForceStop(packageName)) ForceStopState.Success else ForceStopState.Error
                        delay(1200)
                        state = ForceStopState.Idle
                    }
                }) { Text(stringResource(R.string.spoof_action_force_stop)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.spoof_action_cancel))
                }
            }
        )
    }
}

private sealed class ForceStopState {
    object Idle : ForceStopState()
    object Loading : ForceStopState()
    object Success : ForceStopState()
    object Error : ForceStopState()
}
