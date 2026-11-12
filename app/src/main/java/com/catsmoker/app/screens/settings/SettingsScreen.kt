package com.catsmoker.app.screens.settings

import com.catsmoker.app.BuildConfig

import com.catsmoker.app.R

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.application.appearance.AppearanceStore
import com.catsmoker.app.application.appearance.LocaleHelper
import com.catsmoker.app.ui.components.CardGroup
import com.catsmoker.app.ui.components.InfoButton
import com.catsmoker.app.ui.components.InfoTopic
import com.catsmoker.app.ui.components.LanguageOptions
import com.catsmoker.app.ui.components.ScreenScaffold
import com.catsmoker.app.ui.components.SectionCard
import com.catsmoker.app.ui.components.SectionLabel
import com.catsmoker.app.ui.components.ThemeModeOptions
import com.catsmoker.app.ui.components.languageDisplayName
import com.catsmoker.app.ui.theme.CatsmokerSpacing
import com.catsmoker.app.ui.theme.CatsmokerTheme

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenDonate: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAnalyticsConsent by remember { mutableStateOf(false) }
    if (showAnalyticsConsent) {
        AlertDialog(
            onDismissRequest = { showAnalyticsConsent = false },
            title = { Text(stringResource(R.string.sys_analytics_title)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.legal_p_analytics_body))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAnalyticsConsent = false
                    viewModel.onAnalyticsToggled(true)
                }) { Text(stringResource(R.string.sys_analytics_title)) }
            },
            dismissButton = {
                TextButton(onClick = { showAnalyticsConsent = false }) {
                    Text(stringResource(R.string.sys_cancel))
                }
            }
        )
    }

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

    // Common permission state is refreshed independently of distribution controls.
    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNotifications()
            }
        }
        if (owner != null) owner.lifecycle.addObserver(observer)
        onDispose {
            if (owner != null) owner.lifecycle.removeObserver(observer)
        }
    }

    SettingsScreen(
        isSharingApk = uiState.isSharingApk,
        themeMode = uiState.themeMode,
        languageTag = uiState.languageTag,
        notificationsEnabled = uiState.notificationsEnabled,
        onThemeModeChanged = viewModel::onThemeModeChanged,
        onLanguageChanged = viewModel::onLanguageChanged,
        onOpenPermissions = onOpenPermissions,
        onOpenLogs = onOpenLogs,
        onOpenAbout = onOpenAbout,
        onShareApp = viewModel::onShareApp,
        onBack = onBack,
        analyticsEnabled = uiState.analyticsEnabled,
        onAnalyticsToggled = { enabled ->
            if (enabled) showAnalyticsConsent = true else viewModel.onAnalyticsToggled(false)
        },
        updateControls = { DistributionUpdateSettings() },
        adsControls = {
            DistributionAdsSettings(onOpenDonate)
        },
        storeControls = { DistributionStoreSettings() }
    )
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
 * Common state comes from [SettingsViewModel]. Flavor-selected slots own distribution controls;
 * previews can omit them without constructing billing or updater state.
 */
@Composable
fun SettingsScreen(
    isSharingApk: Boolean,
    themeMode: AppearanceStore.ThemeMode,
    languageTag: String,
    notificationsEnabled: Boolean,
    onThemeModeChanged: (AppearanceStore.ThemeMode) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    onShareApp: () -> Unit,
    onBack: () -> Unit,
    analyticsEnabled: Boolean,
    onAnalyticsToggled: (Boolean) -> Unit,
    updateControls: @Composable () -> Unit = {},
    adsControls: @Composable () -> Unit = {},
    storeControls: @Composable () -> Unit = {}
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

                updateControls()

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
                    adsControls()
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
                    storeControls()
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
@SuppressLint("ModifierParameter")
@Composable
internal fun SettingsRowCard(
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
internal fun SettingsToggleCard(
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

/** The rounded icon tile shared by every settings row. The icon itself is decorative. */
@Composable
internal fun SettingsIconBadge(icon: ImageVector, tint: Color) {
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
        "package:${context.packageName}".toUri()
    )
    runCatching { context.startActivity(intent) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SettingsPreview() {
    CatsmokerTheme {
        SettingsScreen(
            isSharingApk = false,
            themeMode = AppearanceStore.ThemeMode.SYSTEM,
            languageTag = AppearanceStore.LANGUAGE_SYSTEM,
            notificationsEnabled = true,
            onThemeModeChanged = {},
            onLanguageChanged = {},
            onOpenPermissions = {},
            onOpenLogs = {},
            onOpenAbout = {},
            onShareApp = {},
            onBack = {},
            analyticsEnabled = true,
            onAnalyticsToggled = {}
        )
    }
}
