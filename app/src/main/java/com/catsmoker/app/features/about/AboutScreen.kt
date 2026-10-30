package com.catsmoker.app.features.about

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.catsmoker.app.BuildConfig
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.components.CardGroup
import com.catsmoker.app.shared.ui.components.ScreenScaffold
import com.catsmoker.app.shared.ui.components.SectionCard
import com.catsmoker.app.shared.ui.components.SectionLabel
import com.catsmoker.app.shared.ui.theme.CatsmokerSpacing
import com.catsmoker.app.shared.ui.theme.CatsmokerTheme
import kotlinx.coroutines.launch

/**
 * About is the app's identity page: header (logo, name, version, one-line description), then
 * Developer/Contact, Links and Legal groups. System Logs deliberately has no entry here — it
 * lives in Settings → Help & Info, and a second copy was the duplication this rewrite removed.
 *
 * The screen is capped and centred on wide windows (tablets, landscape) exactly like Settings,
 * so card rows do not stretch to 1000 dp on a tablet. A long-press (or a TalkBack custom action)
 * on the version line copies it.
 */
@Composable
fun AboutRoute(
    onBack: () -> Unit,
    onDonate: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onOpenLicenses: () -> Unit
) {
    AboutScreen(
        onBack = onBack,
        onDonate = onDonate,
        onPrivacy = onPrivacy,
        onTerms = onTerms,
        onOpenLicenses = onOpenLicenses
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onDonate: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onOpenLicenses: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val githubUrl = stringResource(R.string.url_github)
    val webUrl = stringResource(R.string.url_webpage)
    val discordUrl = stringResource(R.string.url_discord)
    val telegramUrl = stringResource(R.string.url_telegram)
    // Both URLs are read here so the legal links stay the canonical ones asserted by
    // LegalLinksPlacementTest — About is the single place they are offered from.
    val legalUrl = stringResource(R.string.url_legal)
    val licenseUrl = stringResource(R.string.url_license)

    // Resolved at composition in the activity locale (never cached in UI state): the toast
    // fires immediately on tap, so it always matches the visible language.
    val copiedText = stringResource(R.string.about_copied)
    val copyVersionLabel = stringResource(R.string.about_copy_version)
    val versionText = stringResource(
        R.string.about_version,
        BuildConfig.VERSION_NAME,
        BuildConfig.VERSION_CODE
    )

    var readMore by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        runCatching { uriHandler.openUri(url) }
    }

    fun copyVersion() {
        scope.launch {
            clipboard.setClipEntry(
                ClipEntry(ClipData.newPlainText("catsmoker", versionText))
            )
            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.about_header_title),
        subtitle = stringResource(R.string.core_about_subtitle),
        onBack = onBack
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ---------------------------------------------------------------------------
                // Header — the logo is decorative: the app name right below is what TalkBack
                // reads, so describing the image too would announce "CatSmoker" twice.
                // ---------------------------------------------------------------------------
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(88.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            // Long-press copies; the label and custom action make the same
                            // gesture reachable for TalkBack, which cannot hold a press.
                            // The gesture lives on this Box, not on the Text itself, so a
                            // long press cannot start a system text selection instead.
                            .combinedClickable(
                                onClickLabel = copyVersionLabel,
                                onLongClickLabel = copyVersionLabel,
                                onClick = ::copyVersion,
                                onLongClick = ::copyVersion
                            )
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(copyVersionLabel) {
                                        copyVersion()
                                        true
                                    }
                                )
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = versionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.core_about_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = if (readMore) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (readMore) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.about_read_more_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    TextButton(onClick = { readMore = !readMore }) {
                        Text(
                            text = stringResource(
                                if (readMore) R.string.about_read_less else R.string.about_read_more
                            )
                        )
                    }
                }

                // ---------------------------------------------------------------------------
                // Developer / Contact.
                // ---------------------------------------------------------------------------
                Spacer(modifier = Modifier.height(8.dp))
                SectionLabel(stringResource(R.string.about_section_contact), accent = true)
                CardGroup {
                    AboutRow(
                        title = stringResource(R.string.about_social_discord),
                        painter = painterResource(R.drawable.ic_discord),
                        external = true,
                        onClick = { openUrl(discordUrl) }
                    )
                    AboutRow(
                        title = stringResource(R.string.about_social_telegram),
                        painter = painterResource(R.drawable.ic_telegram),
                        external = true,
                        onClick = { openUrl(telegramUrl) }
                    )
                    AboutRow(
                        title = stringResource(R.string.donate_title),
                        imageVector = Icons.Default.Favorite,
                        onClick = onDonate
                    )
                }

                // ---------------------------------------------------------------------------
                // Links.
                // ---------------------------------------------------------------------------
                Spacer(modifier = Modifier.height(CatsmokerSpacing.SectionGap))
                SectionLabel(stringResource(R.string.about_section_links), accent = true)
                CardGroup {
                    AboutRow(
                        title = stringResource(R.string.about_social_github),
                        painter = painterResource(R.drawable.ic_github),
                        external = true,
                        onClick = { openUrl(githubUrl) }
                    )
                    AboutRow(
                        title = stringResource(R.string.about_social_website),
                        imageVector = Icons.Default.Language,
                        external = true,
                        onClick = { openUrl(webUrl) }
                    )
                    AboutRow(
                        title = stringResource(R.string.about_report_issue),
                        imageVector = Icons.Default.BugReport,
                        external = true,
                        onClick = { openUrl("$githubUrl/issues") }
                    )
                }

                // ---------------------------------------------------------------------------
                // Legal — licenses moved here from Settings (Help & Info) so every legal
                // document has one home. The copyright line is a caption, not a card: it
                // carries no action, and informational-only cards are not allowed.
                // ---------------------------------------------------------------------------
                Spacer(modifier = Modifier.height(CatsmokerSpacing.SectionGap))
                SectionLabel(stringResource(R.string.legal_section_title), accent = true)
                CardGroup {
                    AboutRow(
                        title = stringResource(R.string.about_licenses_title),
                        summary = stringResource(R.string.about_licenses_summary),
                        imageVector = Icons.Default.Description,
                        onClick = onOpenLicenses
                    )
                    AboutRow(
                        title = stringResource(R.string.legal_privacy_title),
                        imageVector = Icons.Default.Policy,
                        onClick = onPrivacy
                    )
                    AboutRow(
                        title = stringResource(R.string.legal_terms_title),
                        imageVector = Icons.Default.Gavel,
                        onClick = onTerms
                    )
                    AboutRow(
                        title = stringResource(R.string.legal_website_title),
                        imageVector = Icons.Default.OpenInBrowser,
                        external = true,
                        onClick = { openUrl(legalUrl) }
                    )
                    AboutRow(
                        title = stringResource(R.string.about_license_cc),
                        imageVector = Icons.Default.Copyright,
                        external = true,
                        onClick = { openUrl(licenseUrl) }
                    )
                }

                Spacer(modifier = Modifier.height(CatsmokerSpacing.CardGap))
                Text(
                    text = stringResource(R.string.about_copyright),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * One About row: icon badge, title, optional summary and a trailing chevron (in-app) or
 * open-in-new (browser). The whole card is the touch target (56 dp minimum, ripple from
 * [clickable]); the icon is decorative, so the title is the only thing announced.
 */
@Composable
private fun AboutRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    imageVector: ImageVector? = null,
    painter: Painter? = null,
    external: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    SectionCard(modifier = modifier, contentPadding = 0.dp) {
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                if (painter != null) {
                    Icon(
                        painter = painter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (imageVector != null) {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!summary.isNullOrBlank()) {
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
            Icon(
                imageVector = if (external) {
                    Icons.AutoMirrored.Filled.OpenInNew
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AboutPreview() {
    CatsmokerTheme {
        AboutScreen(
            onBack = {},
            onDonate = {},
            onPrivacy = {},
            onTerms = {},
            onOpenLicenses = {}
        )
    }
}
