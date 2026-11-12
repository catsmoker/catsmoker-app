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
import androidx.compose.material3.Switch
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
import com.catsmoker.app.screens.legal.LegalDialog
import com.catsmoker.app.screens.legal.legalPrivacySections
import com.catsmoker.app.screens.legal.legalTermsHighlights
import com.catsmoker.app.screens.legal.legalTermsSections
import com.catsmoker.app.screens.permissions.OnboardingFooterButton
import com.catsmoker.app.screens.permissions.OnboardingScaffold
import com.catsmoker.app.ui.components.CatsmokerOutlinedButton
import com.catsmoker.app.ui.components.InfoButton
import com.catsmoker.app.ui.components.InfoSection
import com.catsmoker.app.ui.components.InfoText
import com.catsmoker.app.ui.components.InfoTopic

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
    onContinue: () -> Unit,
    analytics: Boolean = false,
    onAnalyticsChange: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
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
        onBack = onBack,
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
                // Short-viewport fallback (landscape / very small screen): scroll only the
                // body column so the footer's Continue button stays reachable.
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    AnalyticsChoiceCard(analytics, onAnalyticsChange)
                    AgreementAcceptanceCard(
                        agreed = agreed,
                        onAgreedChange = onAgreedChange
                    )
                }
            } else {
                // Normal layout: all cards fit without scrolling.
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TermsDocumentCard(
                        licenseText = annotatedLinkString,
                        intro = intro,
                        keySections = keySections,
                        compact = compact,
                        onReadTerms = { showTerms = true },
                        onReadPrivacy = { showPrivacy = true },
                        onOpenWebsite = { uriHandler.openUri(legalUrl) },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                    AnalyticsChoiceCard(analytics, onAnalyticsChange)
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
private fun AnalyticsChoiceCard(selected: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.toggleable(value = selected, role = Role.Switch, onValueChange = onChange)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.sys_analytics_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    InfoButton(
                        topic = InfoTopic(
                            name = InfoText.Res(R.string.sys_analytics_title),
                            sections = listOf(InfoSection(
                                title = InfoText.Res(R.string.sys_info_what_title),
                                paragraphs = listOf(InfoText.Res(R.string.onb_analytics_details)),
                            )),
                        ),
                        compact = true,
                    )
                }
                Text(stringResource(R.string.onb_analytics_explanation), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = selected, onCheckedChange = null)
        }
    }
}
@Composable
internal fun TermsDocumentCard(
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
internal fun KeySectionGrid(titles: List<String>, compact: Boolean) {
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
internal fun AgreementAcceptanceCard(
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
