package com.catsmoker.app.features.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.catsmoker.app.BuildConfig
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.components.CardGroup
import com.catsmoker.app.shared.ui.components.CatsmokerButton
import com.catsmoker.app.shared.ui.components.InfoButton
import com.catsmoker.app.shared.ui.components.InfoTopic
import com.catsmoker.app.shared.ui.components.LanguageOptions
import com.catsmoker.app.shared.ui.components.ScreenScaffold
import com.catsmoker.app.shared.ui.components.SectionCard
import com.catsmoker.app.shared.ui.components.SectionLabel
import com.catsmoker.app.shared.ui.components.ThemeModeOptions
import com.catsmoker.app.shared.ui.components.languageDisplayName
import com.catsmoker.app.shared.ui.theme.CatsmokerSpacing
import com.catsmoker.app.shared.ui.theme.CatsmokerTheme
import com.catsmoker.app.system.VariantCapabilities
import com.catsmoker.app.system.config.AppearanceStore
import com.catsmoker.app.system.config.LocaleHelper

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenDonate: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // A new language restarts the process — the ViewModel asks, the screen obeys.
    // recreate() alone is not enough: app-context getString caches, engine state and
    // running services would all stay on the previous language. Theme changes need
    // none of this; they recompose live.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event == SettingsViewModel.UiEvent.RestartApp) {
                LocaleHelper.restartApp(context)
            }
        }
    }

    // Returning from the Play purchase sheet resumes this screen, which re-checks the
    // remove_ads entitlement (restores after reinstall, picks up purchases made elsewhere).
    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPurchases()
            }
        }
        if (owner != null) owner.lifecycle.addObserver(observer)
        onDispose {
            if (owner != null) owner.lifecycle.removeObserver(observer)
        }
    }

    val activity = context as? Activity

    SettingsScreen(
        adsEnabled = uiState.adsEnabled,
        isAdFree = uiState.isAdFree,
        removeAdsPrice = uiState.removeAdsPrice,
        isBillingWorking = uiState.isBillingWorking,
        autoCheck = uiState.autoCheck,
        isPreRelease = uiState.isPreRelease,
        isUpdating = uiState.isUpdating,
        updateProgress = uiState.updateProgress,
        availableUpdateName = uiState.availableUpdateName,
        isSharingApk = uiState.isSharingApk,
        themeMode = uiState.themeMode,
        languageTag = uiState.languageTag,
        notificationsEnabled = uiState.notificationsEnabled,
        onAdsToggled = viewModel::onAdsToggled,
        onRemoveAdsClicked = { activity?.let(viewModel::onRemoveAdsClicked) },
        onAutoCheckToggled = viewModel::onAutoCheckToggled,
        onPreReleaseToggled = viewModel::onPreReleaseToggled,
        onThemeModeChanged = viewModel::onThemeModeChanged,
        onLanguageChanged = viewModel::onLanguageChanged,
        onOpenPermissions = onOpenPermissions,
        onOpenLogs = onOpenLogs,
        onOpenAbout = onOpenAbout,
        onShareApp = viewModel::onShareApp,
        onBack = onBack,
        onCheckUpdates = viewModel::onCheckUpdates,
        onCancelDownload = viewModel::cancelUpdateDownload,
        analyticsEnabled = uiState.analyticsEnabled,
        onAnalyticsToggled = viewModel::onAnalyticsToggled
    )

    if (uiState.showAdsDonationDialog) {
        AdsDonationDialog(
            onDonate = {
                // Ads stay ON: donating must never double as a hidden turn-off shortcut.
                viewModel.onAdsDonationDismissed()
                onOpenDonate()
            },
            onKeepAds = viewModel::onAdsDonationDismissed,
            onTurnOffAnyway = viewModel::onAdsTurnOffAnyway
        )
    }

    uiState.updateDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdateDialog,
            title = {
                Text(
                    text = stringResource(R.string.sys_update_available, dialog.tagName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.sys_update_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    dialog.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            UpdateNotesPreview(
                                notes = notes,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 340.dp)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::startUpdateDownload) {
                    Text(stringResource(R.string.sys_download))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissUpdateDialog) {
                    Text(stringResource(R.string.sys_later))
                }
            }
        )
    }
}

/**
 * Settings, regrouped into five labelled sections.
 *
 * Every row is one short line — icon, title, current value — with no long descriptions or
 * info icons except on the Privacy & Data switches, where the shared [InfoButton] opens a
 * bottom sheet explaining what is collected (same pattern as Gaming Tools). Card icons use
 * the theme's colorScheme.onSurface so they read correctly in light and dark themes.
 *
 * The screen stays a plain scroll column: at this length a search field would be more chrome
 * than help, and collapsible sections would hide the values the cards exist to show.
 *
 * All state and callbacks still come from [SettingsViewModel]; this composable owns only the
 * theme/language pickers and the update dialog it opens.
 */
@Composable
fun SettingsScreen(
    adsEnabled: Boolean,
    isAdFree: Boolean,
    removeAdsPrice: String?,
    isBillingWorking: Boolean,
    autoCheck: Boolean,
    isPreRelease: Boolean,
    isUpdating: Boolean,
    updateProgress: Float,
    availableUpdateName: String?,
    isSharingApk: Boolean,
    themeMode: AppearanceStore.ThemeMode,
    languageTag: String,
    notificationsEnabled: Boolean,
    onAdsToggled: (Boolean) -> Unit,
    onRemoveAdsClicked: () -> Unit,
    onAutoCheckToggled: (Boolean) -> Unit,
    onPreReleaseToggled: (Boolean) -> Unit,
    onThemeModeChanged: (AppearanceStore.ThemeMode) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    onShareApp: () -> Unit,
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
    onCancelDownload: () -> Unit,
    analyticsEnabled: Boolean,
    onAnalyticsToggled: (Boolean) -> Unit
) {
    var themeDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ScreenScaffold(
        title = stringResource(R.string.sys_settings_title),
        subtitle = stringResource(R.string.sys_settings_subtitle),
        onBack = onBack
    ) {
        // Centred and capped on tablets/landscape; phones keep the full width. The header
        // stays full-bleed because ScreenScaffold owns it.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // ---------------------------------------------------------------------------
                // General — the choices people change most.
                // ---------------------------------------------------------------------------
                SectionLabel(stringResource(R.string.sys_section_general), accent = true)
                CardGroup {
                    SettingsRowCard(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.sys_section_appearance),
                        summary = stringResource(R.string.sys_appearance_summary, themeModeLabel(themeMode)),
                        onClick = { themeDialog = true }
                    )
                    SettingsRowCard(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.sys_notifications_title),
                        summary = stringResource(
                            if (notificationsEnabled) {
                                R.string.sys_notifications_allowed
                            } else {
                                R.string.sys_notifications_blocked
                            }
                        ),
                        onClick = { openNotificationSettings(context) }
                    )
                    SettingsRowCard(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.sys_language_title),
                        summary = stringResource(
                            R.string.sys_language_summary,
                            languageDisplayName(languageTag)
                        ),
                        onClick = { languageDialog = true }
                    )
                }

                // ---------------------------------------------------------------------------
                // Updates — GitHub self-updater only. Play updates come from the store itself.
                // ---------------------------------------------------------------------------
                if (VariantCapabilities.HAS_SELF_UPDATE) {
                    Spacer(modifier = Modifier.height(CatsmokerSpacing.SectionGap))
                    SectionLabel(stringResource(R.string.sys_section_updates), accent = true)
                    UpdatesCard(
                        autoCheck = autoCheck,
                        isPreRelease = isPreRelease,
                        isUpdating = isUpdating,
                        updateProgress = updateProgress,
                        availableUpdateName = availableUpdateName,
                        onAutoCheckToggled = onAutoCheckToggled,
                        onPreReleaseToggled = onPreReleaseToggled,
                        onCheckUpdates = onCheckUpdates,
                        onCancelDownload = onCancelDownload
                    )
                }

                // ---------------------------------------------------------------------------
                // Privacy & Data — both switches that send something off the device.
                // Only these two switches keep an info icon, opening a bottom sheet that
                // explains what is collected.
                // ---------------------------------------------------------------------------
                Spacer(modifier = Modifier.height(CatsmokerSpacing.SectionGap))
                SectionLabel(stringResource(R.string.sys_section_privacy), accent = true)
                CardGroup {
                    SettingsToggleCard(
                        icon = Icons.Default.Analytics,
                        title = stringResource(R.string.sys_analytics_title),
                        summary = stringResource(
                            if (analyticsEnabled) R.string.sys_state_on else R.string.sys_state_off
                        ),
                        checked = analyticsEnabled,
                        onCheckedChange = onAnalyticsToggled,
                        info = SettingsInfoTopics.analytics
                    )
                    // Full keeps the free toggle; Play sells removal via Google Play
                    // Billing instead, so the free switch must not appear there.
                    if (VariantCapabilities.HAS_FREE_ADS_TOGGLE) {
                        SettingsToggleCard(
                            icon = Icons.Default.AdsClick,
                            title = stringResource(R.string.sys_ads_title),
                            summary = stringResource(
                                if (adsEnabled) R.string.sys_state_on else R.string.sys_state_off
                            ),
                            checked = adsEnabled,
                            onCheckedChange = onAdsToggled,
                            info = SettingsInfoTopics.ads
                        )
                    } else if (isAdFree) {
                        AdFreeCard()
                    } else {
                        RemoveAdsCard(
                            price = removeAdsPrice,
                            isWorking = isBillingWorking,
                            onRemoveAdsClicked = onRemoveAdsClicked
                        )
                    }
                }

                // ---------------------------------------------------------------------------
                // Help & Info — diagnostics and permissions. No info icons here: the rows
                // are self-explanatory, and App Permissions hands the user to Android's own
                // settings page via the explicit action below the row.
                // ---------------------------------------------------------------------------
                Spacer(modifier = Modifier.height(CatsmokerSpacing.SectionGap))
                SectionLabel(stringResource(R.string.sys_section_help), accent = true)
                CardGroup {
                    SettingsRowCard(
                        icon = Icons.Default.Terminal,
                        title = stringResource(R.string.sys_logs_title),
                        summary = stringResource(R.string.sys_logs_subtitle),
                        onClick = onOpenLogs
                    )
                    SettingsRowCard(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.sys_permissions_title),
                        onClick = onOpenPermissions,
                        extraContent = {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            TextButton(
                                onClick = { openAppSystemSettings(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.sys_permissions_open_system))
                            }
                        }
                    )
                    // Play Store variant: rate the app on Google Play.
                    if (VariantCapabilities.HAS_PLAY_STORE) {
                        SettingsRowCard(
                            icon = Icons.Default.Star,
                            title = stringResource(R.string.sys_rate_app_title),
                            summary = stringResource(R.string.sys_rate_app_summary),
                            onClick = { openRateApp(context) }
                        )
                    }
                    SettingsRowCard(
                        icon = Icons.Default.Share,
                        title = stringResource(R.string.sys_share_app_title),
                        summary = stringResource(R.string.sys_share_app_summary),
                        onClick = onShareApp,
                        // The APK copy happens off the main thread; while it runs the card
                        // shows progress where its chevron would be, so the tap is visibly
                        // working without adding a second line of text.
                        trailing = if (isSharingApk) {
                            {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            null
                        }
                    )
                }

                // ---------------------------------------------------------------------------
                // About — version and licence at the bottom. Tapping opens the About screen,
                // which is the canonical home for the GitHub/website/legal links.
                // ---------------------------------------------------------------------------
                Spacer(modifier = Modifier.height(CatsmokerSpacing.SectionGap))
                SectionLabel(stringResource(R.string.sys_section_about), accent = true)
                CardGroup {
                    SettingsRowCard(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.about_header_title),
                        summary = stringResource(R.string.sys_about_summary, BuildConfig.VERSION_NAME),
                        onClick = onOpenAbout
                    )
                }
            }
        }
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text(stringResource(R.string.sys_theme_title)) },
            text = {
                ThemeModeOptions(
                    selected = themeMode,
                    onSelect = { onThemeModeChanged(it); themeDialog = false }
                )
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { themeDialog = false }) { Text(stringResource(R.string.sys_later)) } }
        )
    }

    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(stringResource(R.string.sys_language_title)) },
            text = {
                LanguageOptions(
                    selectedTag = languageTag,
                    onSelect = { onLanguageChanged(it); languageDialog = false }
                )
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { languageDialog = false }) { Text(stringResource(R.string.sys_later)) } }
        )
    }
}

/**
 * One navigational setting: icon, title, current value, and a tap that opens the picker or
 * destination. The whole card is the touch target (56 dp minimum).
 *
 * [extraContent] is a second, explicit action below the row (the Permissions card's system
 * shortcut), kept out of the clickable row so the two taps cannot be confused.
 */
@Composable
private fun SettingsRowCard(
    icon: ImageVector,
    title: String,
    summary: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    SectionCard(modifier = modifier, contentPadding = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsIconBadge(icon = icon, tint = iconTint)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (summary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (trailing != null) {
                    trailing()
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            extraContent?.invoke(this)
        }
    }
}

/**
 * One on/off setting. The row itself is the switch ([toggleable] with [Role.Switch]), so a tap
 * anywhere — not just on the small thumb — changes it, and TalkBack announces one control
 * instead of two. The switch is drawn non-interactive to avoid a second focus target.
 */
@Composable
private fun SettingsToggleCard(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    info: InfoTopic? = null
) {
    val haptics = LocalHapticFeedback.current
    SectionCard(modifier = modifier, contentPadding = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCheckedChange(it)
                    }
                )
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsIconBadge(icon = icon, tint = iconTint)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (info != null) InfoButton(info)
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** The Updates card: one action, two opt-in switches, and the badge for a found release. */
@Composable
private fun UpdatesCard(
    autoCheck: Boolean,
    isPreRelease: Boolean,
    isUpdating: Boolean,
    updateProgress: Float,
    availableUpdateName: String?,
    onAutoCheckToggled: (Boolean) -> Unit,
    onPreReleaseToggled: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onCancelDownload: () -> Unit
) {
    SectionCard(contentPadding = 0.dp) {
        // animateContentSize keeps the progress/cancel swap from jumping the sections below.
        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsIconBadge(
                    icon = Icons.Default.SystemUpdate,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sys_section_updates),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (availableUpdateName != null) {
                            stringResource(R.string.sys_updates_available, availableUpdateName)
                        } else {
                            stringResource(R.string.sys_updates_summary, BuildConfig.VERSION_NAME)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (availableUpdateName != null) {
                    SettingsBadge(stringResource(R.string.sys_updates_badge))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                if (isUpdating) {
                    Text(
                        text = stringResource(R.string.sys_updating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { updateProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sys_cancel))
                    }
                } else {
                    CatsmokerButton(
                        onClick = onCheckUpdates,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sys_check_updates))
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                InlineToggleRow(
                    title = stringResource(R.string.sys_auto_check),
                    checked = autoCheck,
                    onCheckedChange = onAutoCheckToggled
                )
                InlineToggleRow(
                    title = stringResource(R.string.sys_prerelease),
                    checked = isPreRelease,
                    onCheckedChange = onPreReleaseToggled
                )
            }
        }
    }
}

/** A switch row inside a larger card, with the same whole-row target as the standalone cards. */
@Composable
private fun InlineToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(it)
                }
            )
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Play variant: the one-time purchase row, with Play's localized price as the value. */
@Composable
private fun RemoveAdsCard(
    price: String?,
    isWorking: Boolean,
    onRemoveAdsClicked: () -> Unit
) {
    SettingsRowCard(
        icon = Icons.Default.WorkspacePremium,
        title = stringResource(R.string.sys_remove_ads_title),
        summary = price ?: stringResource(R.string.sys_remove_ads_summary),
        onClick = onRemoveAdsClicked,
        trailing = if (isWorking && price == null) {
            {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        } else {
            null
        }
    )
}

/** Play variant: the verified purchase is on record — this card confirms it, no control needed. */
@Composable
private fun AdFreeCard() {
    SectionCard(contentPadding = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsIconBadge(
                icon = Icons.Default.Check,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sys_ads_removed_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.sys_ads_removed_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** The rounded icon tile shared by every settings row. The icon itself is decorative. */
@Composable
private fun SettingsIconBadge(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Small state chip ("New"), read by TalkBack as its own text. */
@Composable
private fun SettingsBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun themeModeLabel(mode: AppearanceStore.ThemeMode): String = when (mode) {
    AppearanceStore.ThemeMode.SYSTEM -> stringResource(R.string.sys_theme_system)
    AppearanceStore.ThemeMode.DARK -> stringResource(R.string.sys_theme_dark)
    AppearanceStore.ThemeMode.LIGHT -> stringResource(R.string.sys_theme_light)
    AppearanceStore.ThemeMode.DYNAMIC -> stringResource(R.string.sys_theme_dynamic)
}

/**
 * Opens Android's notification page for this app. The card shows the resulting state on resume.
 * Falls back to the app's detail page when an OEM omits the notification-settings screen.
 */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    val opened = runCatching { context.startActivity(intent) }.isSuccess
    if (!opened) openAppSystemSettings(context)
}

/** Opens Android's app-info page, where the remaining permission toggles live. */
private fun openAppSystemSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    runCatching { context.startActivity(intent) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SettingsPreview() {
    CatsmokerTheme {
        SettingsScreen(
            adsEnabled = true,
            isAdFree = false,
            removeAdsPrice = null,
            isBillingWorking = false,
            autoCheck = false,
            isPreRelease = false,
            isUpdating = false,
            updateProgress = 0f,
            availableUpdateName = "2.1.0",
            isSharingApk = false,
            themeMode = AppearanceStore.ThemeMode.SYSTEM,
            languageTag = AppearanceStore.LANGUAGE_SYSTEM,
            notificationsEnabled = true,
            onAdsToggled = {},
            onRemoveAdsClicked = {},
            onAutoCheckToggled = {},
            onPreReleaseToggled = {},
            onThemeModeChanged = {},
            onLanguageChanged = {},
            onOpenPermissions = {},
            onOpenLogs = {},
            onOpenAbout = {},
            onShareApp = {},
            onBack = {},
            onCheckUpdates = {},
            onCancelDownload = {},
            analyticsEnabled = true,
            onAnalyticsToggled = {}
        )
    }
}
