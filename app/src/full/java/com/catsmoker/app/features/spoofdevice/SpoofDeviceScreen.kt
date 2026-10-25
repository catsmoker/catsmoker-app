package com.catsmoker.app.features.spoofdevice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.components.QuickActionButton
import com.catsmoker.app.shared.ui.components.ScreenScaffold
import com.catsmoker.app.shared.ui.components.SectionCard
import com.catsmoker.app.system.navigation.Routes

@Composable
fun SpoofRoute(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: SpoofDeviceViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    SpoofDeviceScreen(
        uiState = uiState,
        onNavigate = onNavigate,
        onDownloadMagisk = viewModel::installBundledMagiskZip,
        onOpenRootManager = viewModel::launchRootManager,
        onRefresh = viewModel::refreshStatus,
        onBack = onBack
    )
}

@Composable
fun SpoofDeviceScreen(
    uiState: SpoofDeviceViewModel.UiState,
    onNavigate: (String) -> Unit,
    onDownloadMagisk: () -> Unit,
    onOpenRootManager: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    // Pending Magisk generation behind the exclusivity confirm: building the
    // ZIP only starts after the user accepts the second method explicitly.
    var confirmGenerate by remember { mutableStateOf(false) }
    ScreenScaffold(
        title = stringResource(R.string.dash_spoof_title),
        subtitle = stringResource(R.string.spoof_home_subtitle),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)
        ) {
            // Status Card
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.spoof_home_status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusIndicator(active = uiState.isRooted)
                            Text(stringResource(R.string.spoof_home_root), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }
                        // "Root" alone does not say which manager granted it. The subtitle names
                        // the provider from a privileged probe, or says honestly that root
                        // answered without a recognizable provider.
                        Text(
                            text = rootProviderSubtitle(uiState),
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val headline = VectorActivation.headline(uiState.xposedEvidence)
                            StatusIndicator(
                                active = headline == VectorActivation.Headline.ACTIVE,
                                unknown = headline == VectorActivation.Headline.UNKNOWN,
                                warn = headline == VectorActivation.Headline.ENABLED_UNVERIFIED
                            )
                            Text(
                                stringResource(
                                    when (headline) {
                                        VectorActivation.Headline.ACTIVE -> R.string.spoof_home_lsposed_on
                                        VectorActivation.Headline.ENABLED_UNVERIFIED ->
                                            R.string.spoof_home_lsposed_unverified
                                        else -> R.string.spoof_home_lsposed_off
                                    }
                                ),
                                color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp
                            )
                        }
                        // What the headline above is built from: framework presence,
                        // manager-enabled state, scope contents, observed loads and
                        // observed spoofing are separate facts. "Enabled" alone never
                        // proves the module loaded (nor does a missing heartbeat prove
                        // it did not — see VectorActivation). Read-only evidence; our
                        // own package needs no scope entry for any of this.
                        XposedEvidenceLine(evidence = uiState.xposedEvidence)
                        Spacer(modifier = Modifier.height(6.dp))
                        // The second spoofing method, stated as plainly as the first:
                        // installed/disabled/absent when the probe could answer,
                        // honestly unknown when it could not (no root to look).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusIndicator(
                                active = uiState.magiskModuleStatus ==
                                    SpoofMethodConflict.MagiskModuleStatus.INSTALLED,
                                unknown = uiState.magiskModuleStatus ==
                                    SpoofMethodConflict.MagiskModuleStatus.UNKNOWN
                            )
                            Text(
                                stringResource(
                                    when (uiState.magiskModuleStatus) {
                                        SpoofMethodConflict.MagiskModuleStatus.INSTALLED ->
                                            R.string.spoof_method_magisk_installed
                                        SpoofMethodConflict.MagiskModuleStatus.DISABLED ->
                                            R.string.spoof_method_magisk_disabled
                                        SpoofMethodConflict.MagiskModuleStatus.ABSENT ->
                                            R.string.spoof_method_magisk_absent
                                        SpoofMethodConflict.MagiskModuleStatus.UNKNOWN ->
                                            R.string.spoof_method_magisk_unknown
                                    }
                                ),
                                color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp
                            )
                        }
                        Text(
                            text = stringResource(
                                when (uiState.magiskModuleStatus) {
                                    SpoofMethodConflict.MagiskModuleStatus.INSTALLED ->
                                        R.string.spoof_method_magisk_installed_sub
                                    SpoofMethodConflict.MagiskModuleStatus.DISABLED ->
                                        R.string.spoof_method_magisk_disabled_sub
                                    SpoofMethodConflict.MagiskModuleStatus.ABSENT ->
                                        R.string.spoof_method_magisk_absent_sub
                                    SpoofMethodConflict.MagiskModuleStatus.UNKNOWN ->
                                        R.string.spoof_method_magisk_unknown_sub
                                }
                            ),
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.logs_refresh),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            if (uiState.isRefreshing) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            }
            // Both methods armed at once rewrite the same identity through
            // different channels. The app deactivates only its own Vector row
            // automatically when the manager-enabled state is readable; this
            // banner remains while a conflict is visible, including the case
            // where that supported change could not be verified.
            if (
                SpoofMethodConflict.isConflict(
                    vectorModuleEnabled = uiState.xposedEvidence.moduleEnabled,
                    xposedLoaded = uiState.moduleActive,
                    magisk = uiState.magiskModuleStatus
                )
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                SectionCard {
                    Text(
                        stringResource(R.string.spoof_method_conflict_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.spoof_method_conflict_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.conflictResolutionFailed) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.spoof_method_conflict_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = onRefresh, enabled = !uiState.isRefreshing) {
                            Text(stringResource(R.string.spoof_method_conflict_retry))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(stringResource(R.string.spoof_home_management), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpoofMenuCard(
                    title = stringResource(R.string.spoof_menu_profiles),
                    subtitle = stringResource(R.string.spoof_menu_identities, uiState.profiles.size),
                    icon = Icons.Default.AccountBox,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Routes.SPOOF_PROFILES) }
                )
                SpoofMenuCard(
                    title = stringResource(R.string.spoof_menu_apps),
                    // Ladder packages are hooked too — they just get their identity per tier, so
                    // counting only the plain assignments would under-report.
                    subtitle = stringResource(R.string.spoof_menu_hooked, (uiState.assignments.keys + uiState.rateAssignments.keys).size),
                    icon = Icons.Default.Apps,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Routes.SPOOF_APPS) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpoofMenuCard(
                    title = stringResource(R.string.spoof_menu_safe),
                    // The card opens both lists now; the count is their union, so an app in
                    // both is one row, not two.
                    subtitle = stringResource(
                        R.string.spoof_menu_apps_count,
                        (uiState.safeModePackages + uiState.versionBypassPackages).size
                    ),
                    icon = Icons.Default.Security,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Routes.SPOOF_SAFE_MODE) }
                )
                SpoofMenuCard(
                    title = stringResource(R.string.spoof_menu_diag),
                    subtitle = stringResource(R.string.spoof_menu_verify),
                    icon = Icons.Default.Troubleshoot,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Routes.SPOOF_DIAGNOSTICS) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(stringResource(R.string.spoof_home_advanced), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionCard {
                QuickActionButton(
                    title = stringResource(R.string.spoof_magisk_title),
                    subtitle = if (uiState.isGeneratingMagisk) stringResource(R.string.spoof_magisk_building) else stringResource(R.string.spoof_magisk_desc),
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconContentColor = MaterialTheme.colorScheme.onSurface,
                    icon = { Icon(Icons.Default.FileDownload, null) },
                    onClick = {
                        // Unloaded store means the assignment lists are unknown,
                        // not empty — treat as potentially armed, never as clear.
                        if (
                            !uiState.storeLoaded ||
                            SpoofMethodConflict.needsGenerateConfirm(
                                moduleActive = uiState.moduleActive,
                                hasAssignments = uiState.assignments.isNotEmpty(),
                                hasLadders = uiState.rateAssignments.isNotEmpty()
                            )
                        ) {
                            confirmGenerate = true
                        } else {
                            onDownloadMagisk()
                        }
                    },
                    isFullWidth = true,
                    isLoading = uiState.isGeneratingMagisk
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                // Global rule: always show, gray when unsupported. Opening a root
                // manager without root can do nothing, so the tile stays visible
                // but disabled with the reason instead of disappearing.
                QuickActionButton(
                    title = stringResource(R.string.spoof_root_manager_title),
                    subtitle = if (uiState.isRooted) stringResource(R.string.spoof_root_manager_desc)
                    else stringResource(R.string.spoof_root_manager_need),
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconContentColor = MaterialTheme.colorScheme.onSurface,
                    icon = { Icon(Icons.Default.Settings, null) },
                    onClick = onOpenRootManager,
                    isFullWidth = true,
                    enabled = uiState.isRooted,
                    modifier = Modifier.alpha(if (uiState.isRooted) 1f else 0.6f)
                )
            }
        }
    }
    // The exclusivity gate for the Magisk direction: Confirm starts the
    // generation the tap asked for; anything else drops it silently.
    if (confirmGenerate) {
        AlertDialog(
            onDismissRequest = { confirmGenerate = false },
            title = { Text(stringResource(R.string.spoof_method_confirm_generate_title)) },
            text = { Text(stringResource(R.string.spoof_method_confirm_generate_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmGenerate = false
                    onDownloadMagisk()
                }) { Text(stringResource(R.string.spoof_method_use_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmGenerate = false }) {
                    Text(stringResource(R.string.spoof_action_cancel))
                }
            }
        )
    }
}

@Composable
fun SpoofMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SectionCard(
        modifier = modifier.clickable { onClick() }
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Gray detail lines under the Xposed status row naming the evidence behind it.
 *
 * Framework presence, manager-enabled state, scope contents, observed loads and
 * observed spoofing are told apart here instead of collapsing into one dot: the
 * row above answers with the headline, these lines answer "how do you know, and
 * what is missing". Unknown stays unknown (no privilege to check reads as
 * unchecked, never as absent). Our own package being out of scope is the normal
 * state and gets no complaint line.
 */
@Composable
private fun XposedEvidenceLine(evidence: VectorActivation.Evidence) {
    val detail = when (evidence.framework) {
        VectorActivation.Framework.UNKNOWN ->
            stringResource(R.string.spoof_home_xposed_nopriv)
        VectorActivation.Framework.ABSENT ->
            stringResource(R.string.spoof_home_xposed_nofw)
        VectorActivation.Framework.VECTOR -> {
            val framework = evidence.frameworkVersion?.let { "Vector $it" } ?: "Vector"
            when (evidence.moduleEnabled) {
                true -> stringResource(R.string.spoof_home_xposed_active_detail, framework)
                false -> stringResource(R.string.spoof_home_xposed_disabled_detail, framework)
                null -> stringResource(R.string.spoof_home_xposed_unknown_detail, framework)
            }
        }
    }
    Text(
        text = detail,
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
        modifier = Modifier.padding(start = 16.dp, top = 2.dp)
    )
    // Second line: whether execution was observed at all. Individual target
    // package names and models stay on Diagnostics; this card answers status,
    // not per-app attribution.
    val observed: String? = when {
        evidence.loadedProof -> stringResource(R.string.spoof_home_xposed_loads_seen)
        evidence.framework == VectorActivation.Framework.VECTOR &&
            evidence.moduleEnabled == true ->
            stringResource(R.string.spoof_home_xposed_load_pending)
        else -> null
    }
    observed?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp)
        )
    }
}

/**
 * Names the active root provider without turning a package presence into a claim.
 * Unrooted, rooted-without-a-known-provider, and multiple providers are three distinct
 * answers; none of them is silently replaced by "Magisk".
 */
@Composable
private fun rootProviderSubtitle(uiState: SpoofDeviceViewModel.UiState): String {
    if (!uiState.isRooted) return stringResource(R.string.spoof_root_provider_none)
    val providers = uiState.rootProviders
    return when {
        providers.isEmpty() -> stringResource(R.string.spoof_root_provider_unknown)
        providers.size > 1 -> stringResource(
            R.string.spoof_root_provider_multiple,
            providers.map { providerName(it) }.sorted().joinToString(", ")
        )
        else -> when (providers.first()) {
            RootProvider.MAGISK -> stringResource(R.string.spoof_root_provider_magisk)
            RootProvider.KERNELSU -> stringResource(R.string.spoof_root_provider_kernelsu)
            RootProvider.APATCH -> stringResource(R.string.spoof_root_provider_apatch)
        }
    }
}

@Composable
private fun providerName(provider: RootProvider): String = stringResource(
    when (provider) {
        RootProvider.MAGISK -> R.string.spoof_root_name_magisk
        RootProvider.KERNELSU -> R.string.spoof_root_name_kernelsu
        RootProvider.APATCH -> R.string.spoof_root_name_apatch
    }
)

@Composable
fun StatusIndicator(active: Boolean, unknown: Boolean = false, warn: Boolean = false) {
    Box(
        modifier = Modifier.padding(end = 8.dp).size(8.dp).clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    unknown -> Color.Gray
                    warn -> Color(0xFFFFB300)
                    active -> Color.Green
                    else -> Color.Red
                }
            )
    )
}
