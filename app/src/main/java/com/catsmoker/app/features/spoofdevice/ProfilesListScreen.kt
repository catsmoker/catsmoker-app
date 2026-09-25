package com.catsmoker.app.features.spoofdevice

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.app.R
import com.catsmoker.app.shared.data.model.DevicePreset
import com.catsmoker.app.shared.data.repository.SpoofRepository
import com.catsmoker.app.shared.ui.components.CatsmokerButton
import com.catsmoker.app.shared.ui.components.ScreenScaffold
import com.catsmoker.app.shared.ui.components.SectionCard

/**
 * The profile list: Default Profile plus user profiles, nothing else.
 *
 * Profiles and presets are different things — this screen lists profiles only.
 * Presets appear solely inside the creation dialog's selector. Tapping a profile
 * opens it for editing; non-default profiles expose Share/Delete via the menu.
 */
@Composable
fun ProfilesListScreen(
    uiState: SpoofDeviceViewModel.UiState,
    presets: List<DevicePreset>,
    onNavigateToEditor: (String) -> Unit,
    onCreateProfile: (String, DevicePreset?) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onShareProfile: (String) -> Unit,
    onConfirmImportProfile: (SpoofProfileSharing.ImportPreview, String) -> Unit,
    onConfirmImportPreset: (SpoofProfileSharing.ImportPreview, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SpoofRepository.ProfileEntry?>(null) }
    var preview by remember { mutableStateOf<SpoofProfileSharing.ImportPreview?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    // Default first, then the rest in store order. Matched by fixed name, never by
    // position, so the Default Profile cannot be displaced.
    val ordered = remember(uiState.profiles) {
        uiState.profiles.sortedBy { if (SpoofRepository.isDefault(it)) 0 else 1 }
    }

    fun handleImportText(text: String) {
        when (val result = SpoofProfileSharing.parseImportJson(text)) {
            is SpoofProfileSharing.ImportResult.Valid -> {
                val existing = uiState.profiles.map { it.name }.toSet() +
                    uiState.userPresets.map { it.name }
                val unique = SpoofProfileSharing.uniqueProfileName(result.preview.name, existing)
                preview = result.preview.copy(name = unique)
            }
            is SpoofProfileSharing.ImportResult.Invalid -> importError = result.reason
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8)
        }.getOrNull()
        if (text.isNullOrBlank()) {
            importError = context.getString(R.string.spoof_import_invalid_title)
        } else {
            handleImportText(text)
        }
    }

    // Profiles shared from another app (ACTION_SEND / ACTION_VIEW) land in the inbox
    // via MainActivity; surface them here as a preview instead of silently importing.
    LaunchedEffect(Unit) {
        SpoofProfileImportInbox.take()?.let { handleImportText(it) }
    }

    ScreenScaffold(
        title = stringResource(R.string.spoof_profiles_title),
        subtitle = stringResource(R.string.spoof_profiles_subtitle),
        onBack = onBack,
        trailingContent = {
            Row {
                TextButton(onClick = { picker.launch("*/*") }) {
                    Text(stringResource(R.string.spoof_action_import))
                }
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    ) {
        if (!uiState.storeLoaded) {
            // Saying "no profiles" before the store has been read would be inventing an answer.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ordered, key = { it.id }) { entry ->
                    val isDefault = SpoofRepository.isDefault(entry)
                    ProfileRow(
                        name = entry.name,
                        details = "${entry.profile.brand} ${entry.profile.model}".trim()
                            .ifBlank { entry.profile.deviceCode },
                        showMenu = !isDefault,
                        onOpen = { onNavigateToEditor(entry.id) },
                        onShare = { onShareProfile(entry.id) },
                        onDelete = { pendingDelete = entry }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProfileDialog(
            presets = presets,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, preset ->
                onCreateProfile(name, preset)
                showCreateDialog = false
            },
            onDeletePreset = onDeletePreset
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.spoof_dialog_delete_title, entry.name)) },
            text = { Text(stringResource(R.string.spoof_dialog_delete_text)) },
            confirmButton = {
                CatsmokerButton(onClick = {
                    onDeleteProfile(entry.id)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.spoof_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.spoof_action_cancel))
                }
            }
        )
    }

    preview?.let { current ->
        ImportPreviewDialog(
            preview = current,
            nameTaken = uiState.profiles.any { it.name == current.name } ||
                uiState.userPresets.any { it.name == current.name },
            onDismiss = { preview = null },
            onImportProfile = { chosenName ->
                onConfirmImportProfile(current, chosenName)
                preview = null
            },
            onImportPreset = { chosenName ->
                onConfirmImportPreset(current, chosenName)
                preview = null
            }
        )
    }

    importError?.let { reason ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(stringResource(R.string.spoof_import_invalid_title)) },
            text = { Text(reason) },
            confirmButton = {
                TextButton(onClick = { importError = null }) {
                    Text(stringResource(R.string.spoof_action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProfileRow(
    name: String,
    details: String,
    showMenu: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenuState by remember { mutableStateOf(false) }
    SectionCard(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (details.isNotBlank()) {
                    Text(details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showMenu) {
                // Overflow menu only: Share + Delete. Tapping the row is the edit action.
                Box {
                    IconButton(onClick = { showMenuState = true }) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                    DropdownMenu(expanded = showMenuState, onDismissRequest = { showMenuState = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.spoof_action_share)) },
                            onClick = { showMenuState = false; onShare() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.spoof_action_delete)) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { showMenuState = false; onDelete() }
                        )
                    }
                }
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateProfileDialog(
    presets: List<DevicePreset>,
    onDismiss: () -> Unit,
    onCreate: (String, DevicePreset?) -> Unit,
    onDeletePreset: (String) -> Unit
) {
    var newProfileName by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<DevicePreset?>(null) }
    var pendingDeletePreset by remember { mutableStateOf<DevicePreset?>(null) }
    val hint = stringResource(R.string.spoof_editor_preset_hint)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spoof_dialog_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text(stringResource(R.string.spoof_field_profile_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.spoof_create_choose_preset),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedPreset?.displayName?.trim()?.ifBlank { null } ?: hint,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        PresetSelectorMenu(
                            presets = presets,
                            hint = hint,
                            onPick = {
                                selectedPreset = it
                                expanded = false
                            },
                            onDeleteRequest = {
                                expanded = false
                                pendingDeletePreset = it
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            CatsmokerButton(
                onClick = {
                    if (newProfileName.isNotBlank()) {
                        onCreate(newProfileName, selectedPreset)
                        newProfileName = ""
                    }
                }
            ) {
                Text(stringResource(R.string.spoof_action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.spoof_action_cancel))
            }
        }
    )

    pendingDeletePreset?.let { preset ->
        PresetDeleteDialog(
            preset = preset,
            onConfirm = {
                onDeletePreset(preset.id)
                if (selectedPreset?.id == preset.id) selectedPreset = null
                pendingDeletePreset = null
            },
            onDismiss = { pendingDeletePreset = null }
        )
    }
}

@Composable
private fun ImportPreviewDialog(
    preview: SpoofProfileSharing.ImportPreview,
    nameTaken: Boolean,
    onDismiss: () -> Unit,
    onImportProfile: (String) -> Unit,
    onImportPreset: (String) -> Unit
) {
    var chosenName by remember(preview) { mutableStateOf(preview.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spoof_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = chosenName,
                    onValueChange = { chosenName = it },
                    label = { Text(stringResource(R.string.spoof_field_profile_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameTaken) {
                    Text(
                        stringResource(R.string.spoof_import_name_taken),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                preview.sourcePresetId?.let { source ->
                    Text(
                        "${stringResource(R.string.spoof_import_source)}: $source",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(R.string.spoof_import_fields),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SpoofProfileSharing.previewLines(preview.profile).forEach { (label, value) ->
                    Text(
                        "$label: $value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            // One import, two destinations: a profile (usable immediately) or a preset
            // (a template in the selector). Both mint fresh ids; nothing is overwritten.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onImportPreset(chosenName) }) {
                    Text(stringResource(R.string.spoof_import_save_preset))
                }
                CatsmokerButton(onClick = { onImportProfile(chosenName) }) {
                    Text(stringResource(R.string.spoof_import_save_profile))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.spoof_action_cancel))
            }
        }
    )
}
