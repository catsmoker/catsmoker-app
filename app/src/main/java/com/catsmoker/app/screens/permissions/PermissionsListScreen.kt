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
        // No subtitle: "Grant permissions to enable all features" restated the title and the rows
        // below already say, per line, what is granted. The step's own intro carries the one thing
        // a user needs to know before the first tap.
        subtitle = "",
        showProgress = uiState.isOnboarding,
        // A Settings revisit reuses this checklist but is entered from the Settings back stack,
        // so only the first-run flow offers a previous step.
        showBack = uiState.isOnboarding,
        dense = true,
        hideSubtitleWhenCompact = true,
        progressTrailing = {
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
                // 4 dp, not 16: this step is laid out to fit one screen, and padding the list for a
                // scroll it no longer needs is what kept a 96 px scroll alive at the very bottom.
                // No overscroll stretch: the step is laid out to fit one screen, and the rubber
                // band was the only thing left that looked like scrolling. Content that genuinely
                // overflows (large font scale, short window) still scrolls.
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState(), overscrollEffect = null)
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            ) {
                PermissionGroup(label = stringResource(R.string.policy_access_title)) {
                    Text(stringResource(R.string.onb_access_summary), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    // One card of three rows, not three cards: each consent is a checkbox and a
                    // state, and the Android request sits on the row instead of in a button band
                    // of its own. Same choices, same gates, a third of the height.
                    GamingAccessCard {
                        GamingAccessRow(
                            titleRes = R.string.onb_inventory_title,
                            explanationRes = R.string.policy_inventory,
                            selected = uiState.choices.inventory,
                            onChange = { onAccessChange(GamingAccessConsent.INVENTORY, it) },
                            status = GamingAccessStatus.of(
                                selected = uiState.choices.inventory,
                                confirmed = GamingAccessConsent.INVENTORY in uiState.confirmedGamingAccess,
                            ),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        GamingAccessRow(
                            titleRes = R.string.onb_usage_title,
                            explanationRes = R.string.policy_usage,
                            selected = uiState.choices.usage,
                            onChange = { onAccessChange(GamingAccessConsent.USAGE, it) },
                            status = GamingAccessStatus.of(
                                selected = uiState.choices.usage,
                                confirmed = GamingAccessConsent.USAGE in uiState.confirmedGamingAccess,
                                androidGranted = uiState.usageGranted,
                            ),
                            onRequest = {
                                onConfirmAccessRequest(GamingAccessConsent.USAGE)
                                openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        GamingAccessRow(
                            titleRes = R.string.onb_notifications_title,
                            explanationRes = R.string.policy_notifications,
                            selected = uiState.choices.notifications,
                            onChange = { onAccessChange(GamingAccessConsent.NOTIFICATIONS, it) },
                            status = GamingAccessStatus.of(
                                selected = uiState.choices.notifications,
                                confirmed = GamingAccessConsent.NOTIFICATIONS in uiState.confirmedGamingAccess,
                                androidGranted = uiState.notificationAccessGranted,
                            ),
                            onRequest = {
                                onConfirmAccessRequest(GamingAccessConsent.NOTIFICATIONS)
                                openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                        )
                    }
                }
                PermissionGroup(label = stringResource(R.string.onb_perm_group_essential)) {
                    PermissionGrid(specs = essential, tileHeight = 92.dp)
                }
                // Two groups, one row of the screen: each column is a single-file stack of its own
                // three tiles. Side by side they cost three tile heights instead of four, and the
                // two labels stay apart so "System" and "Additional" keep their meaning.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        PermissionGroup(label = stringResource(R.string.onb_perm_group_system)) {
                            PermissionGrid(specs = system, tileHeight = 92.dp, columns = 1)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        PermissionGroup(label = stringResource(R.string.onb_perm_group_additional)) {
                            PermissionGrid(specs = additional, tileHeight = 92.dp, columns = 1)
                        }
                    }
                }
            }
        } // BoxWithConstraints
    }
}

/**
 * The card that holds the three optional consents.
 *
 * It replaced three separate tiles, each with its own border, its own 48 dp row and — for the two
 * that need an Android grant — a full-width request button underneath. Same three choices, same
 * explicit checkboxes, less than half the height.
 */
@Composable
private fun GamingAccessCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        // The last row's request button would otherwise sit a couple of dp above the border.
        Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
    }
}

/**
 * One consent: the checkbox edits the choice, the status reflects the access in effect now.
 *
 * The Android request lives on the row rather than in a band below it. It is only offered while the
 * state is ANDROID_REQUIRED, which is exactly when the checkbox alone cannot deliver the feature.
 */
@Composable
private fun GamingAccessRow(
    @StringRes titleRes: Int,
    @StringRes explanationRes: Int,
    selected: Boolean,
    onChange: (Boolean) -> Unit,
    status: GamingAccessStatus,
    onRequest: (() -> Unit)? = null,
) {
    val granted = status == GamingAccessStatus.GRANTED
    val statusRes = when (status) {
        GamingAccessStatus.GRANTED -> R.string.onb_perm_granted
        GamingAccessStatus.NOT_GRANTED -> R.string.onb_perm_not_granted
        GamingAccessStatus.ANDROID_REQUIRED -> R.string.onb_android_not_granted
        GamingAccessStatus.PENDING_SAVE -> R.string.onb_access_pending_save
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).heightIn(min = 60.dp)
                .toggleable(value = selected, role = Role.Checkbox, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(Modifier.width(2.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(titleRes),
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (granted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = PermissionGrantedGreen,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = stringResource(statusRes),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
        if (status == GamingAccessStatus.ANDROID_REQUIRED && onRequest != null) {
            // Drawn as a bordered pill: in this screen's plain link style the words read as another
            // status label, and this is the only control on the row that leaves the app.
            TextButton(
                onClick = onRequest,
                modifier = Modifier
                    .padding(start = 6.dp, end = 2.dp)
                    .heightIn(min = 48.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    stringResource(R.string.onb_allow_open_settings),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 2
                )
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
internal fun PermissionGrid(specs: List<PermissionSpec>, tileHeight: Dp, columns: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        specs.chunked(columns).forEach { row ->
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
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (spec.granted) {
                    // Green, not the accent: the accent is this screen's "act on me" colour, and a
                    // granted tile wearing it read as an error while its own dot said Granted.
                    PermissionGrantedGreen.copy(alpha = 0.45f)
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
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        // Intentional: see the arrangement below, the tile centres its two rows.

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
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (spec.actionable) {
                Text(
                    text = stringResource(R.string.core_grant),
                    fontSize = 11.sp,
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
    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
