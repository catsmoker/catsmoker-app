package com.catsmoker.app.features.permissions

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.catsmoker.app.R
import com.catsmoker.app.features.about.LegalDialog
import com.catsmoker.app.features.about.legalPrivacySections
import com.catsmoker.app.features.about.legalTermsHighlights
import com.catsmoker.app.features.about.legalTermsSections
import com.catsmoker.app.shared.ui.components.CatsmokerOutlinedButton
import com.catsmoker.app.shared.ui.components.InfoButton
import com.catsmoker.app.shared.ui.components.InfoSection
import com.catsmoker.app.shared.ui.components.InfoText
import com.catsmoker.app.shared.ui.components.InfoTopic
import com.catsmoker.app.shared.ui.theme.CatsmokerTheme
import com.catsmoker.app.system.VariantCapabilities
import com.catsmoker.app.system.config.AppearanceStore
import com.catsmoker.app.system.config.LocaleHelper

@Composable
fun PermissionRoute(onDone: () -> Unit) {
    val viewModel: PermissionViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // A newly picked language restarts the process — the ViewModel asks, the screen
    // obeys. The chosen flag survives it (committed prefs + init resume). A refused root
    // request is named instead of leaving the row off with no explanation.
    val rootDeniedMessage = stringResource(R.string.core_perm_root_denied)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PermissionViewModel.UiEvent.RestartApp -> LocaleHelper.restartApp(context)
                PermissionViewModel.UiEvent.RootRequestFailed ->
                    Toast.makeText(context, rootDeniedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (uiState.isAppearanceStep) {
        AppearanceStepScreen(
            themeMode = uiState.themeMode,
            languageTag = uiState.languageTag,
            onThemeChange = viewModel::onThemeModeChanged,
            onLanguageChange = viewModel::onLanguageChanged,
            onContinue = viewModel::onAppearanceContinue
        )
    } else if (uiState.isAgreementStep) {
        AgreementStepScreen(
            agreed = uiState.isAgreed,
            onAgreedChange = viewModel::onAgreedChange,
            onContinue = { viewModel.onAgreementContinue() }
        )
    } else {
        PermissionsListScreen(
            uiState = uiState,
            onRefresh = viewModel::refreshStates,
            onRequestRoot = viewModel::requestRootPermission,
            onRequestShizuku = viewModel::requestShizukuPermission,
            onDone = { viewModel.onDone(onDone) }
        )
    }
}

/**
 * Step one: theme + language.
 *
 * The screen is a two-zone composition rather than a settings card: "01 Theme" is a row of
 * live miniatures, "02 Language" is a wrapping pill field. Both use the same numbered
 * section heading and the same brand accent, so they read as one setup surface. The
 * Continue action owns the bottom edge, and the whole step is sized to fit without
 * scrolling — the sections spread into the available height instead of stacking past it.
 */
@Composable
fun AppearanceStepScreen(
    themeMode: AppearanceStore.ThemeMode,
    languageTag: String,
    onThemeChange: (AppearanceStore.ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    OnboardingScaffold(
        step = 0,
        title = stringResource(R.string.sys_first_run_title),
        subtitle = stringResource(R.string.sys_first_run_subtitle),
        // On a 640 dp-tall phone the two-line subtitle is what pushes the second language
        // row under the footer; the section hints already explain both choices.
        hideSubtitleWhenCompact = true,
        footer = {
            OnboardingFooterButton(
                label = stringResource(R.string.sys_continue),
                onClick = onContinue
            )
        }
    ) { compact ->
        Column(modifier = Modifier.fillMaxSize()) {
            // The theme board takes every spare dp; the language pills keep their natural
            // height at the bottom. That keeps the step balanced on tall phones without
            // turning the gap between the two sections into a dead band.
            Column(modifier = Modifier.weight(1f)) {
                OnboardingSectionHeader(index = "01", label = stringResource(R.string.sys_theme_title))
                Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                ThemeModeCards(
                    selected = themeMode,
                    onSelect = onThemeChange,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.onb_theme_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(if (compact) 10.dp else 16.dp))

            Column {
                OnboardingSectionHeader(index = "02", label = stringResource(R.string.sys_language_title))
                Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                LanguagePills(
                    selectedTag = languageTag,
                    onSelect = onLanguageChange,
                    compact = compact
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.onb_language_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Step two: the agreement page.
 *
 * The license sentence, a compact index of the terms' key sections, and the ways to read
 * the full documents live in one document surface that grows to fill the available height.
 * Acceptance is a separate, visually distinct control directly above Continue: checking it
 * tints the card with the brand accent and enables the button, so the relationship between
 * the two is visible without a label.
 */
@Composable
fun AgreementStepScreen(
    agreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    onContinue: () -> Unit
) {
    // The linked license text names the open-source license (not the app terms): the full
    // Privacy Policy and Terms & Conditions open as dialogs below, from the same string
    // resources the About/Settings legal screens render.
    var showPrivacy by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val legalUrl = stringResource(R.string.url_legal)

    val annotatedLinkString = buildAnnotatedString {
        append(stringResource(R.string.core_terms_prefix))
        withLink(
            LinkAnnotation.Url(
                url = "https://creativecommons.org/licenses/by-nc-sa/4.0/",
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                )
            )
        ) {
            append(stringResource(R.string.core_terms_link))
        }
        append(stringResource(R.string.core_terms_suffix))
    }
    val keySections = legalTermsHighlights()

    OnboardingScaffold(
        step = 1,
        title = stringResource(R.string.core_terms_title),
        subtitle = stringResource(R.string.onb_terms_subtitle),
        footer = {
            OnboardingFooterButton(
                label = stringResource(R.string.core_continue),
                onClick = onContinue,
                enabled = agreed
            )
        }
    ) { compact ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // A tall step can carry the real opening paragraph of the Terms; a short one
            // keeps only the license note and the section index so nothing is clipped.
            val intro = if (maxHeight >= 560.dp) stringResource(R.string.legal_t_intro) else null
            if (maxHeight < 420.dp) {
                // Landscape cannot fit both cards: the acceptance row must stay visible or
                // CONTINUE is permanently disabled with no way to enable it, so it is
                // pinned above the footer and the document scrolls in the space left.
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        TermsDocumentCard(
                            licenseText = annotatedLinkString,
                            intro = intro,
                            keySections = keySections,
                            compact = compact,
                            onReadTerms = { showTerms = true },
                            onReadPrivacy = { showPrivacy = true },
                            onOpenWebsite = { uriHandler.openUri(legalUrl) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    AgreementAcceptanceCard(
                        agreed = agreed,
                        onAgreedChange = onAgreedChange
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    TermsDocumentCard(
                        licenseText = annotatedLinkString,
                        intro = intro,
                        keySections = keySections,
                        compact = compact,
                        onReadTerms = { showTerms = true },
                        onReadPrivacy = { showPrivacy = true },
                        onOpenWebsite = { uriHandler.openUri(legalUrl) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
                    AgreementAcceptanceCard(
                        agreed = agreed,
                        onAgreedChange = onAgreedChange
                    )
                }
            }
        }
    }

    if (showPrivacy) {
        LegalDialog(
            title = stringResource(R.string.legal_privacy_title),
            intro = stringResource(R.string.legal_p_intro),
            sections = legalPrivacySections(),
            dismissLabel = stringResource(R.string.sys_later),
            onDismiss = { showPrivacy = false }
        )
    }

    if (showTerms) {
        LegalDialog(
            title = stringResource(R.string.legal_terms_title),
            intro = stringResource(R.string.legal_t_intro),
            sections = legalTermsSections(),
            dismissLabel = stringResource(R.string.sys_later),
            onDismiss = { showTerms = false }
        )
    }
}

@Composable
private fun TermsDocumentCard(
    licenseText: AnnotatedString,
    intro: String?,
    keySections: List<String>,
    compact: Boolean,
    onReadTerms: () -> Unit,
    onReadPrivacy: () -> Unit,
    onOpenWebsite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 14.dp else 18.dp)
        ) {
            Column {
                Text(
                    text = licenseText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (intro != null) {
                    Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                    Text(
                        text = intro,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                Text(
                    text = stringResource(R.string.onb_terms_key_sections),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
                KeySectionGrid(titles = keySections, compact = compact)
            }

            Column {
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                CatsmokerOutlinedButton(
                    onClick = onReadTerms,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 40.dp else 44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.core_terms_read_terms),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = onReadPrivacy,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.core_terms_read_privacy),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = onOpenWebsite,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.legal_website_title),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeySectionGrid(titles: List<String>, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)) {
        titles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { title ->
                    Row(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .padding(top = 5.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = title,
                            fontSize = if (compact) 10.5.sp else 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AgreementAcceptanceCard(
    agreed: Boolean,
    onAgreedChange: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .toggleable(value = agreed, role = Role.Checkbox, onValueChange = onAgreedChange),
        shape = shape,
        color = if (agreed) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (agreed) 1.5.dp else 1.dp,
            color = if (agreed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = agreed,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.core_terms_agree),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.onb_terms_required),
                    fontSize = 11.sp,
                    color = if (agreed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (agreed) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Step three: every permission in one compact, grouped checklist.
 *
 * Each permission is a tile with its name and current status visible; the explanation
 * (what the feature uses it for, why a row is unavailable, how to get past restricted
 * settings) lives behind the tile's info button and opens in the app's standard info
 * sheet. That keeps the default screen scannable while the guidance stays one tap away.
 * The grid is two columns and sized to fit the shortest supported viewport without a
 * scroll; the root and Shizuku checks lead in the Essential group because they gate the
 * most powerful features.
 */
// Pending Play declaration for REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (PLAYSTORE.md REVIEW) — kept intentionally.
@Composable
fun PermissionsListScreen(
    uiState: PermissionViewModel.UiState,
    onRefresh: () -> Unit,
    onRequestRoot: () -> Unit,
    onRequestShizuku: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current

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
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = "package:${context.packageName}".toUri()
                                    }
                                )
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                } catch (_: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
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
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    } catch (_: Exception) {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
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
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )
                    )
                }
            )
        )
        add(
            PermissionSpec(
                titleRes = R.string.core_perm_usage_title,
                subtitleRes = R.string.core_perm_usage_sub,
                granted = uiState.usageGranted,
                actionable = !uiState.usageGranted,
                statusRes = if (uiState.usageGranted) R.string.onb_perm_granted
                else R.string.onb_perm_not_granted,
                extraRes = if (uiState.usageGranted) null else R.string.core_perm_restricted_hint,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
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
                label = stringResource(R.string.core_done),
                onClick = onDone
            )
        }
    ) { _ ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Five tile rows (1 + 2 + 2) share the leftover height: short phones keep a
            // compact 58-62 dp tile, tall phones grow them instead of leaving gaps between
            // the three groups. 78 dp covers the group labels and the two inter-row gaps.
            //
            // Landscape (and any viewport shorter than ~420 dp) cannot fit three groups
            // at the 58 dp floor, so content becomes scrollable instead of clipping the
            // ADDITIONAL group behind the footer.
            val scrollable = maxHeight < 420.dp
            val tileHeight = if (scrollable) 58.dp
            else ((maxHeight - 78.dp) / 5).coerceIn(58.dp, 112.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (scrollable) Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp)
                        else Modifier
                    ),
                verticalArrangement = if (scrollable) Arrangement.spacedBy(10.dp)
                else Arrangement.SpaceBetween
            ) {
                PermissionGroup(label = stringResource(R.string.onb_perm_group_essential)) {
                    PermissionGrid(specs = essential, tileHeight = tileHeight)
                }
                PermissionGroup(label = stringResource(R.string.onb_perm_group_system)) {
                    PermissionGrid(specs = system, tileHeight = tileHeight)
                }
                PermissionGroup(label = stringResource(R.string.onb_perm_group_additional)) {
                    PermissionGrid(specs = additional, tileHeight = tileHeight)
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
private class PermissionSpec(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val granted: Boolean,
    val actionable: Boolean,
    @StringRes val statusRes: Int,
    @StringRes val extraRes: Int? = null,
    val onClick: () -> Unit
)

@Composable
private fun PermissionGroup(
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
private fun PermissionGrid(specs: List<PermissionSpec>, tileHeight: Dp) {
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
private fun PermissionTile(spec: PermissionSpec, modifier: Modifier = Modifier) {
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
private fun permissionStatusColor(spec: PermissionSpec): Color = when {
    spec.granted -> PermissionGrantedGreen
    spec.actionable -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
}

/** One tap opens the shared info sheet; the explanation is never printed in the tile. */
@Composable
private fun PermissionInfoButton(spec: PermissionSpec) {
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
private val PermissionGrantedGreen = Color(0xFF43A047)

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AppearanceStepPreview() {
    CatsmokerTheme {
        AppearanceStepScreen(
            themeMode = AppearanceStore.ThemeMode.SYSTEM,
            languageTag = AppearanceStore.LANGUAGE_SYSTEM,
            onThemeChange = {},
            onLanguageChange = {},
            onContinue = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AgreementStepPreview() {
    CatsmokerTheme {
        AgreementStepScreen(agreed = true, onAgreedChange = {}, onContinue = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PermissionsPreview() {
    CatsmokerTheme {
        PermissionsListScreen(
            uiState = PermissionViewModel.UiState(
                isAppearanceStep = false,
                isAgreementStep = false,
                rootGranted = true,
                notifGranted = false,
                overlayGranted = true
            ),
            onRefresh = {},
            onRequestRoot = {},
            onRequestShizuku = {},
            onDone = {}
        )
    }
}
