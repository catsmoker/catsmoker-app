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
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
    analytics: Boolean = true,
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
                label = stringResource(
                    if (analytics) R.string.onb_enable_analytics_continue
                    else R.string.onb_without_analytics_continue
                ),
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

/**
 * Scale bounds for every line of copy the agreement card prints: never below the theme scale, and
 * never so large that the card turns into a poster even when it is handed a tablet-height slot.
 */
private const val MIN_TERMS_CARD_SCALE = 1f
private const val MAX_TERMS_CARD_SCALE = 1.6f

/** Key-section index geometry, shared by the grid and by the height reserved for it. */
private val TermsGridColumnGap = 10.dp
private val TermsGridRowGap = 7.dp
private val TermsGridRowGapCompact = 5.dp
private val TermsGridBulletSize = 4.dp
private val TermsGridBulletSpacer = 6.dp
private val TermsGridBulletExtent = TermsGridBulletSize + TermsGridBulletSpacer

/** Text-button row height, pinned so the card's height budget stays exact at every text scale. */
private val TermsActionLinkRowHeight = 40.dp

/**
 * The agreement step's document surface: the license note, the opening paragraph, the index of
 * key sections and the routes into the full documents.
 *
 * One factor is chosen for everything the card prints — the license note, the opening paragraph,
 * the section index and the action labels — and applied to the lot, so the card reads as one
 * document instead of one loud line above a footnote. The factor is measured against the wrapped
 * text rather than assumed: the same sentence runs two lines in English and four in Japanese, a
 * system font scale changes it again, and the step hands the card a different height on every
 * device, so a constant bump either clips or leaves the card half empty.
 */
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
    val keySectionsLabel = stringResource(R.string.onb_terms_key_sections)
    val readTermsLabel = stringResource(R.string.core_terms_read_terms)
    val readPrivacyLabel = stringResource(R.string.core_terms_read_privacy)
    val legalWebsiteLabel = stringResource(R.string.legal_website_title)
    val licenseStyle = MaterialTheme.typography.bodyMedium
    val introStyle = MaterialTheme.typography.bodySmall
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp
    )
    val gridTitleStyle = keySectionTitleStyle(compact)
    val actionStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
    val linkStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val padding = if (compact) 14.dp else 18.dp
    val proseGap = if (compact) 8.dp else 10.dp
    val blockGap = if (compact) 8.dp else 12.dp
    val labelGap = if (compact) 6.dp else 8.dp
    val actionGap = if (compact) 8.dp else 12.dp
    val actionHeight = if (compact) 40.dp else 44.dp
    val gridRowGap = if (compact) TermsGridRowGapCompact else TermsGridRowGap

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // The index and the actions are laid out before the factor can be known, so their real
            // height is read back from layout instead of re-derived from the spacing constants:
            // font metrics, minimum touch heights and the system font scale all contribute, and a
            // constant sum drifts away from what Compose reserves. Only the text inside them
            // scales, so taking away the text at the scale it was laid out at leaves the fixed
            // part, which then holds for every candidate factor.
            var indexBlockHeightPx by remember { mutableStateOf(0) }
            var actionsHeightPx by remember { mutableStateOf(0) }
            var chromeScale by remember { mutableStateOf(MIN_TERMS_CARD_SCALE) }
            val contentWidthPx = with(density) {
                (maxWidth - padding * 2).roundToPx().coerceAtLeast(1)
            }
            val contentHeightPx = with(density) {
                if (maxHeight.value.isFinite()) {
                    (maxHeight - padding * 2).roundToPx().coerceAtLeast(0)
                } else {
                    0
                }
            }
            val copyScale = remember(
                maxWidth, contentHeightPx, indexBlockHeightPx, actionsHeightPx, chromeScale, density,
                licenseText, intro, keySections, keySectionsLabel,
                readTermsLabel, readPrivacyLabel, legalWebsiteLabel,
                licenseStyle, introStyle, labelStyle, gridTitleStyle, actionStyle, linkStyle,
                proseGap, gridRowGap
            ) {
                // An unbounded height means the step scrolls (short-viewport fallback), so there
                // is no leftover space to fill and the theme scale stays untouched.
                if (contentHeightPx <= 0 || indexBlockHeightPx <= 0 || actionsHeightPx <= 0) {
                    MIN_TERMS_CARD_SCALE
                } else {
                    with(density) {
                        val indexWidthPx = ((contentWidthPx - TermsGridColumnGap.roundToPx()) / 2 -
                            TermsGridBulletExtent.roundToPx()).coerceAtLeast(1)
                        val proseGapPx = proseGap.roundToPx()
                        val indexGapPx = gridRowGap.roundToPx()
                        val actionWidthPx = contentWidthPx - 2 * 10.dp.roundToPx()
                        val linkWidthPx = (contentWidthPx - 4.dp.roundToPx()) / 2 -
                            2 * 4.dp.roundToPx()

                        fun singleLineWidth(text: String, style: TextStyle): Int = measurer.measure(
                            text = text,
                            style = style,
                            softWrap = false,
                            constraints = Constraints()
                        ).size.width

                        // The action labels never wrap, so their widths at scale 1 bound the
                        // factor directly: advances grow with the font size.
                        val actionWidthAt1 = singleLineWidth(readTermsLabel, actionStyle)
                        val linkWidthAt1 = maxOf(
                            singleLineWidth(readPrivacyLabel, linkStyle),
                            singleLineWidth(legalWebsiteLabel, linkStyle)
                        )

                        // Null when an index title would need a third line, i.e. when the scale truncates it.
                        fun indexHeight(scale: Float): Int? {
                            val style = gridTitleStyle.scaledCopy(scale)
                            val rows = keySections.chunked(2)
                            var height = 0
                            rows.forEachIndexed { row, titles ->
                                var rowHeight = 0
                                titles.forEach { title ->
                                    val result = measurer.measure(
                                        text = title,
                                        style = style,
                                        maxLines = 2,
                                        constraints = Constraints(maxWidth = indexWidthPx)
                                    )
                                    if (result.didOverflowHeight) return null
                                    rowHeight = maxOf(rowHeight, result.size.height)
                                }
                                height += rowHeight
                                if (row < rows.lastIndex) height += indexGapPx
                            }
                            return height
                        }

                        fun labelHeight(scale: Float): Int = measurer.measure(
                            text = keySectionsLabel,
                            style = labelStyle.scaledCopy(scale),
                            maxLines = 1,
                            constraints = Constraints(maxWidth = contentWidthPx)
                        ).size.height

                        fun proseHeight(scale: Float): Int {
                            var height = measurer.measure(
                                text = licenseText,
                                style = licenseStyle.scaledCopy(scale),
                                constraints = Constraints(maxWidth = contentWidthPx)
                            ).size.height
                            if (intro != null) {
                                height += proseGapPx + measurer.measure(
                                    text = intro,
                                    style = introStyle.scaledCopy(scale),
                                    constraints = Constraints(maxWidth = contentWidthPx)
                                ).size.height
                            }
                            return height
                        }

                        val fixedChromePx = indexHeight(chromeScale)?.let { index ->
                            indexBlockHeightPx - labelHeight(chromeScale) - index + actionsHeightPx
                        }

                        fun fits(scale: Float): Boolean {
                            if (fixedChromePx == null) return false
                            if (actionWidthAt1 * scale > actionWidthPx) return false
                            if (linkWidthAt1 * scale > linkWidthPx) return false
                            val index = indexHeight(scale) ?: return false
                            val height = fixedChromePx + labelHeight(scale) + index + proseHeight(scale)
                            return height <= contentHeightPx
                        }

                        if (fits(MAX_TERMS_CARD_SCALE)) {
                            MAX_TERMS_CARD_SCALE
                        } else {
                            var low = MIN_TERMS_CARD_SCALE
                            var high = MAX_TERMS_CARD_SCALE
                            repeat(6) {
                                val mid = (low + high) / 2
                                if (fits(mid)) low = mid else high = mid
                            }
                            low
                        }
                    }
                }
            }
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    text = licenseText,
                    style = licenseStyle.scaledCopy(copyScale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (intro != null) {
                    Spacer(Modifier.height(proseGap))
                    Text(
                        text = intro,
                        style = introStyle.scaledCopy(copyScale),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.onGloballyPositioned {
                        indexBlockHeightPx = it.size.height
                        chromeScale = copyScale
                    }
                ) {
                    Spacer(Modifier.height(blockGap))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(blockGap))
                    Text(
                        text = keySectionsLabel,
                        style = labelStyle.scaledCopy(copyScale),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(labelGap))
                    KeySectionGrid(
                        titles = keySections,
                        titleStyle = gridTitleStyle.scaledCopy(copyScale),
                        rowGap = gridRowGap
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.onGloballyPositioned {
                        actionsHeightPx = it.size.height
                    }
                ) {
                    Spacer(Modifier.height(actionGap))
                    CatsmokerOutlinedButton(
                        onClick = onReadTerms,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(actionHeight),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text(
                            text = readTermsLabel,
                            style = actionStyle.scaledCopy(copyScale),
                            maxLines = 1
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TermsActionLinkRowHeight),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = onReadPrivacy,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(
                                text = readPrivacyLabel,
                                style = linkStyle.scaledCopy(copyScale),
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
                                text = legalWebsiteLabel,
                                style = linkStyle.scaledCopy(copyScale),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The key-section index. Titles are capped at two lines, so the card can reserve their height
 * exactly — and refuse a text scale that would push a title onto a third line — before it decides
 * how large the card's copy may become.
 */
@Composable
internal fun KeySectionGrid(
    titles: List<String>,
    titleStyle: TextStyle,
    rowGap: Dp
) {
    Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
        titles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TermsGridColumnGap)) {
                row.forEach { title ->
                    Row(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .padding(top = 5.dp)
                                .size(TermsGridBulletSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(TermsGridBulletSpacer))
                        Text(
                            text = title,
                            style = titleStyle,
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

/** Index-title style, shared by the grid and by the height the card reserves for it. */
@Composable
private fun keySectionTitleStyle(compact: Boolean): TextStyle {
    val base = LocalTextStyle.current
    return remember(compact, base) {
        base.copy(
            fontSize = if (compact) 10.5.sp else 11.sp,
            lineHeight = 14.sp
        )
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

/** Font size and line height multiplied together so hierarchy and leading survive the scale. */
private fun TextStyle.scaledCopy(scale: Float): TextStyle {
    if (scale == MIN_TERMS_CARD_SCALE) return this
    return copy(
        fontSize = if (fontSize == TextUnit.Unspecified) fontSize else fontSize * scale,
        lineHeight = if (lineHeight == TextUnit.Unspecified) lineHeight else lineHeight * scale
    )
}
