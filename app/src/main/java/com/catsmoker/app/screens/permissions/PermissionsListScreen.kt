package com.catsmoker.app.screens.permissions

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.R
import com.catsmoker.app.application.distribution.VariantCapabilities
import com.catsmoker.app.application.privacy.GamingAccessConsent
import com.catsmoker.app.platform.storage.StorageAccess
import com.catsmoker.app.screens.permissions.OnboardingFooterButton
import com.catsmoker.app.screens.permissions.OnboardingScaffold
import com.catsmoker.app.screens.permissions.PermissionViewModel
import com.catsmoker.app.ui.components.InfoButton
import com.catsmoker.app.ui.components.InfoSection
import com.catsmoker.app.ui.components.InfoText
import com.catsmoker.app.ui.components.InfoTopic

@Composable
fun PermissionsListScreen(
    uiState: PermissionViewModel.UiState,
    onRefresh: () -> Unit,
    onRequestRoot: () -> Unit,
    onRequestShizuku: () -> Unit,
    onDone: () -> Unit,
    onAccessChange: (String, Boolean) -> Unit = { _, _ -> },
    onConfirmAccessRequest: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    fun openSettings(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.onb_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    val notificationSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val bluetoothSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val storageSupported =
        VariantCapabilities.HAS_ALL_FILES_ACCESS || Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    val essential = listOf(
        PermissionSpec(
            titleRes = R.string.core_perm_root_title,
            subtitleRes = R.string.core_perm_root_sub,
            granted = uiState.rootGranted,
            actionable = uiState.rootAvailable && !uiState.rootGranted,
            statusRes = when {
                uiState.rootGranted -> R.string.onb_perm_granted
                !uiState.rootAvailable -> R.string.onb_perm_unavailable
                else -> R.string.onb_perm_not_granted
            },
            extraRes = if (uiState.rootAvailable) null else R.string.core_perm_root_unavailable,
            onClick = onRequestRoot
        ),
        PermissionSpec(
            titleRes = R.string.core_perm_shizuku_title,
            subtitleRes = R.string.core_perm_shizuku_sub,
            granted = uiState.shizukuGranted,
            actionable = (uiState.shizukuRunning || uiState.shizukuGranted) && !uiState.shizukuGranted,
            statusRes = when {
                uiState.shizukuGranted -> R.string.onb_perm_granted
                uiState.shizukuRunning -> R.string.onb_perm_not_granted
                else -> R.string.onb_perm_not_running
            },
            extraRes = when {
                uiState.shizukuGranted -> null
                uiState.shizukuRunning -> R.string.core_perm_shizuku_not_granted
                else -> R.string.core_perm_shizuku_not_running
            },
            onClick = onRequestShizuku
        )
    )

    val system = buildList {
        if (storageSupported) {
            add(
                PermissionSpec(
                    titleRes = R.string.core_perm_storage_title,
                    subtitleRes = R.string.core_perm_storage_sub,
                    granted = uiState.storageGranted,
                    actionable = !uiState.storageGranted,
                    statusRes = if (uiState.storageGranted) R.string.onb_perm_granted
                    else R.string.onb_perm_not_granted,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            StorageAccess.settingsIntent(context)?.let {
                                openSettings(it)
                            }
                        } else {
                            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                )
            )
        }
        add(
            PermissionSpec(
                titleRes = R.string.core_perm_battery_title,
                subtitleRes = R.string.core_perm_battery_sub,
                granted = uiState.batteryGranted,
                actionable = !uiState.batteryGranted,
                statusRes = if (uiState.batteryGranted) R.string.onb_perm_granted
                else R.string.onb_perm_not_granted,
                onClick = {
                    openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            )
        )
        add(
            PermissionSpec(
                titleRes = R.string.core_perm_overlay_title,
                subtitleRes = R.string.core_perm_overlay_sub,
                granted = uiState.overlayGranted,
                actionable = !uiState.overlayGranted,
                statusRes = if (uiState.overlayGranted) R.string.onb_perm_granted
                else R.string.onb_perm_not_granted,
                extraRes = if (uiState.overlayGranted) null else R.string.core_perm_restricted_hint,
                onClick = {
                    openSettings(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )
                    )
                }
            )
        )
    }

    val additional = listOf(
        PermissionSpec(
            titleRes = R.string.core_perm_notif_title,
            subtitleRes = R.string.core_perm_notif_sub,
            granted = uiState.notifGranted,
            actionable = notificationSupported && !uiState.notifGranted,
            statusRes = when {
                uiState.notifGranted -> R.string.onb_perm_granted
                notificationSupported -> R.string.onb_perm_not_granted
                else -> R.string.onb_perm_unavailable
            },
            extraRes = if (notificationSupported) null else R.string.core_perm_need_13,
            onClick = {
                if (notificationSupported) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        ),
        PermissionSpec(
            titleRes = R.string.core_perm_mic_title,
            subtitleRes = R.string.core_perm_mic_sub,
            granted = uiState.micGranted,
            actionable = !uiState.micGranted,
            statusRes = if (uiState.micGranted) R.string.onb_perm_granted
            else R.string.onb_perm_not_granted,
            onClick = {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        ),
        PermissionSpec(
            titleRes = R.string.core_perm_bt_title,
            subtitleRes = R.string.core_perm_bt_sub,
            granted = uiState.bluetoothGranted,
            actionable = bluetoothSupported && !uiState.bluetoothGranted,
            statusRes = when {
                uiState.bluetoothGranted -> R.string.onb_perm_granted
                bluetoothSupported -> R.string.onb_perm_not_granted
                else -> R.string.onb_perm_unavailable
            },
            extraRes = if (bluetoothSupported) null else R.string.core_perm_need_12,
            onClick = {
                if (bluetoothSupported) {
                    bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
        )
    )

    OnboardingScaffold(
        step = 2,
        onBack = onBack,
        title = stringResource(R.string.core_permissions_title),
        subtitle = stringResource(R.string.core_permissions_subtitle),
        showProgress = uiState.isOnboarding,
        dense = true,
        hideSubtitleWhenCompact = true,
        trailing = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.logs_refresh),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        footer = {
            OnboardingFooterButton(
                label = stringResource(R.string.onb_save_access_finish),
                onClick = onDone
            )
        }
    ) { _ ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Short-viewport fallback (landscape / small screen): a compact arrangement
            // reduces spacing. At maxHeight < 420.dp, the long list of groups is condensed
            // so tiles remain tappable in landscape.
            val compact = maxHeight < 420.dp
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            ) {
                PermissionGroup(label = stringResource(R.string.policy_access_title)) {
                    Text(stringResource(R.string.onb_access_summary), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GamingAccessTile(
                            titleRes = R.string.onb_inventory_title,
                            explanationRes = R.string.policy_inventory,
                            selected = uiState.choices.inventory,
                            onChange = { onAccessChange(GamingAccessConsent.INVENTORY, it) },
                            status = stringResource(R.string.onb_inventory_status),
                            modifier = Modifier.weight(1f),
                        )
                        GamingAccessTile(
                            titleRes = R.string.onb_usage_title,
                            explanationRes = R.string.policy_usage,
                            selected = uiState.choices.usage,
                            onChange = { onAccessChange(GamingAccessConsent.USAGE, it) },
                            status = stringResource(if (uiState.usageGranted) R.string.onb_android_granted else R.string.onb_android_not_granted),
                            modifier = Modifier.weight(1f),
                            onRequest = {
                                onConfirmAccessRequest(GamingAccessConsent.USAGE)
                                openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    GamingAccessTile(
                        titleRes = R.string.onb_notifications_title,
                        explanationRes = R.string.policy_notifications,
                        selected = uiState.choices.notifications,
                        onChange = { onAccessChange(GamingAccessConsent.NOTIFICATIONS, it) },
                        status = stringResource(if (uiState.notificationAccessGranted) R.string.onb_android_granted else R.string.onb_android_not_granted),
                        onRequest = {
                            onConfirmAccessRequest(GamingAccessConsent.NOTIFICATIONS)
                            openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                    )
                }
                PermissionGroup(label = stringResource(R.string.onb_perm_group_essential)) {
                    PermissionGrid(specs = essential, tileHeight = 86.dp)
                }
                PermissionGroup(label = stringResource(R.string.onb_perm_group_system)) {
                    PermissionGrid(specs = system, tileHeight = 86.dp)
                }
                PermissionGroup(label = stringResource(R.string.onb_perm_group_additional)) {
                    PermissionGrid(specs = additional, tileHeight = 86.dp)
                }
            }
        } // BoxWithConstraints
    }
}

/** Choice and platform authorization are deliberately separate controls and labels. */
@Composable
private fun GamingAccessTile(
    @StringRes titleRes: Int,
    @StringRes explanationRes: Int,
    selected: Boolean,
    onChange: (Boolean) -> Unit,
    status: String,
    modifier: Modifier = Modifier,
    onRequest: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 128.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = selected, role = Role.Checkbox, onValueChange = onChange),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selected, onCheckedChange = null)
                Spacer(Modifier.width(5.dp))
                Text(
                    stringResource(titleRes),
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                InfoButton(
                    topic = InfoTopic(
                        name = InfoText.Res(titleRes),
                        sections = listOf(InfoSection(
                            title = InfoText.Res(R.string.sys_info_what_title),
                            paragraphs = buildList {
                                add(InfoText.Res(explanationRes))
                                if (onRequest == null) add(InfoText.Res(R.string.onb_inventory_visibility))
                                add(InfoText.Res(R.string.policy_access_intro))
                                add(InfoText.Res(R.string.core_perm_restricted_hint))
                            },
                        )),
                    ),
                    compact = true,
                )
            }
            Text(status, fontSize = 10.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onRequest != null) {
                TextButton(
                    onClick = onRequest,
                    enabled = selected,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text(stringResource(R.string.onb_allow_open_settings), fontSize = 11.sp, lineHeight = 13.sp)
                }
            }
        }
    }
}

/**
 * One permission as a compact tile: name + status always visible, explanation behind the
 * info control. The whole tile is the grant/open-settings target while an action exists;
 * once granted it becomes a plain status tile and only the info button stays interactive.
 */
internal class PermissionSpec(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val granted: Boolean,
    val actionable: Boolean,
    @StringRes val statusRes: Int,
    @StringRes val extraRes: Int? = null,
    val onClick: () -> Unit
)

@Composable
internal fun PermissionGroup(
    label: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
internal fun PermissionGrid(specs: List<PermissionSpec>, tileHeight: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        specs.chunked(2).forEach { row ->
            if (row.size == 1) {
                PermissionTile(
                    spec = row.first(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tileHeight)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { spec ->
                        PermissionTile(
                            spec = spec,
                            modifier = Modifier
                                .weight(1f)
                                .height(tileHeight)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PermissionTile(spec: PermissionSpec, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (spec.granted) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = shape
            )
            .clickable(
                enabled = spec.actionable,
                role = Role.Button,
                onClick = spec.onClick
            )
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(spec.titleRes),
                fontSize = 13.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (spec.granted || spec.actionable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
            )
            PermissionInfoButton(spec)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(permissionStatusColor(spec))
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(spec.statusRes),
                fontSize = 10.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (spec.actionable) {
                Text(
                    text = stringResource(R.string.core_grant),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (spec.granted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = permissionStatusColor(spec),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
internal fun permissionStatusColor(spec: PermissionSpec): Color = when {
    spec.granted -> PermissionGrantedGreen
    spec.actionable -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
}

/** One tap opens the shared info sheet; the explanation is never printed in the tile. */
@Composable
internal fun PermissionInfoButton(spec: PermissionSpec) {
    val topic = InfoTopic(
        name = InfoText.Res(spec.titleRes),
        sections = listOf(
            InfoSection(
                title = InfoText.Res(R.string.sys_info_what_title),
                paragraphs = buildList {
                    add(InfoText.Res(spec.subtitleRes))
                    spec.extraRes?.let { add(InfoText.Res(it)) }
                }
            )
        )
    )
    InfoButton(topic = topic, compact = true)
}

/** Material green that stays legible as a dot/check on both the paper and charcoal schemes. */
internal val PermissionGrantedGreen = Color(0xFF43A047)
