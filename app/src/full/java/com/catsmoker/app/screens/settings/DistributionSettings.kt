package com.catsmoker.app.screens.settings

import com.catsmoker.app.BuildConfig

import com.catsmoker.app.R

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.ui.components.CatsmokerButton
import com.catsmoker.app.ui.components.SectionCard
import com.catsmoker.app.ui.components.SectionLabel
import com.catsmoker.app.ui.theme.CatsmokerSpacing

/** Only Full constructs updater state, including the dashboard's update badge. */
@Composable
internal fun rememberAvailableUpdateName(): String? {
    val viewModel: FullSettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshUpdateStatus() }
    return state.availableUpdateName
}

@Composable
internal fun DistributionUpdateSettings() {
    val viewModel: FullSettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshUpdateStatus() }
    LaunchedEffect(viewModel) {
        viewModel.toasts.collect { android.widget.Toast.makeText(
            context, it, android.widget.Toast.LENGTH_SHORT
        ).show() }
    }
    Spacer(Modifier.height(CatsmokerSpacing.SectionGap))
    SectionLabel(stringResource(R.string.sys_section_updates), accent = true)
    UpdatesCard(state, viewModel)
    state.updateDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdateDialog,
            title = { Text(stringResource(R.string.sys_update_available, dialog.tagName),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sys_update_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    dialog.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            UpdateNotesPreview(notes, Modifier.fillMaxWidth().heightIn(max = 340.dp)
                                .padding(horizontal = 12.dp, vertical = 10.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::startUpdateDownload) {
                Text(stringResource(R.string.sys_download))
            } },
            dismissButton = { TextButton(onClick = viewModel::dismissUpdateDialog) {
                Text(stringResource(R.string.sys_later))
            } }
        )
    }
}

@Composable
internal fun DistributionAdsSettings(onOpenDonate: () -> Unit) {
    val viewModel: FullSettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsToggleCard(
        icon = Icons.Default.AdsClick,
        title = stringResource(R.string.sys_ads_title),
        summary = stringResource(if (state.adsEnabled) R.string.sys_state_on else R.string.sys_state_off),
        checked = state.adsEnabled,
        onCheckedChange = viewModel::onAdsToggled,
        info = SettingsInfoTopics.topic(R.string.sys_ads_title, R.string.sys_ads_sub)
    )
    if (state.showAdsDonationDialog) {
        AdsDonationDialog(
            onDonate = {
                // Donating never silently changes the ad preference.
                viewModel.onAdsDonationDismissed()
                onOpenDonate()
            },
            onKeepAds = viewModel::onAdsDonationDismissed,
            onTurnOffAnyway = viewModel::onAdsTurnOffAnyway
        )
    }
}

@Composable
internal fun DistributionStoreSettings() = Unit

@Composable
private fun UpdatesCard(state: FullSettingsViewModel.UiState, viewModel: FullSettingsViewModel) {
    SectionCard(contentPadding = 0.dp) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsIconBadge(Icons.Default.SystemUpdate, MaterialTheme.colorScheme.onSurface)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sys_section_updates), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(if (state.availableUpdateName != null) {
                        stringResource(R.string.sys_updates_available, state.availableUpdateName)
                    } else stringResource(R.string.sys_updates_summary, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (state.availableUpdateName != null) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.sys_updates_badge), style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                if (state.isUpdating) {
                    Text(stringResource(R.string.sys_updating), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { state.updateProgress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = viewModel::cancelUpdateDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sys_cancel))
                    }
                } else {
                    CatsmokerButton(onClick = viewModel::onCheckUpdates, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sys_check_updates))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant)
                InlineToggleRow(stringResource(R.string.sys_auto_check), state.autoCheck, viewModel::onAutoCheckToggled)
                InlineToggleRow(stringResource(R.string.sys_prerelease), state.isPreRelease, viewModel::onPreReleaseToggled)
            }
        }
    }
}

@Composable
private fun InlineToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(
        value = checked, role = Role.Switch, onValueChange = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onCheckedChange(it)
        }).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
