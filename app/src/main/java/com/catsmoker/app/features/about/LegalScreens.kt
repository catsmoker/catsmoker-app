package com.catsmoker.app.features.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.components.ScreenScaffold

/**
 * In-app legal pages.
 *
 * Both screens render only string resources (translated in every shipped locale) and describe
 * verified behavior only: local-only storage, variant-specific ads (Start.io with a free off
 * switch on full, AdMob with a one-time remove-ads purchase on Play), Firebase Analytics that
 * starts automatically in both builds but can be disabled from Settings, opt-in GitHub update
 * checks, user-initiated log sharing, and external donation processors. The Terms are adapted to the app's real surface: no accounts,
 * no hosted user content and no moderation queue, so no clause promises machinery that does not
 * exist. Section lists are shared with the onboarding dialogs so both surfaces cannot drift.
 */

@Composable
fun PrivacyPolicyRoute(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val legalUrl = stringResource(R.string.url_legal)
    ScreenScaffold(
        title = stringResource(R.string.legal_privacy_title),
        subtitle = stringResource(R.string.legal_privacy_subtitle),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.legal_privacy_updated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.legal_p_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            legalPrivacySections().forEach { (sectionTitle, sectionBody) ->
                LegalSection(title = sectionTitle, body = sectionBody)
            }
            WebsiteLegalFooter(uriHandler = uriHandler, legalUrl = legalUrl)
        }
    }
}

@Composable
fun TermsRoute(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val legalUrl = stringResource(R.string.url_legal)
    ScreenScaffold(
        title = stringResource(R.string.legal_terms_title),
        subtitle = stringResource(R.string.legal_terms_subtitle),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.legal_terms_updated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.legal_t_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            legalTermsSections().forEach { (sectionTitle, sectionBody) ->
                LegalSection(title = sectionTitle, body = sectionBody)
            }
            WebsiteLegalFooter(uriHandler = uriHandler, legalUrl = legalUrl)
        }
    }
}

/**
 * Privacy Policy sections, resolved at composition time. Shared by the full screen and the
 * onboarding dialog so a section added to one surface cannot be missing from the other.
 */
@Composable
fun legalPrivacySections(): List<Pair<String, String>> = listOf(
    stringResource(R.string.legal_p_local_title) to stringResource(R.string.legal_p_local_body),
    stringResource(R.string.legal_p_analytics_title) to stringResource(R.string.legal_p_analytics_body),
    stringResource(R.string.legal_p_ads_title) to stringResource(R.string.legal_p_ads_body),
    stringResource(R.string.legal_p_updates_title) to stringResource(R.string.legal_p_updates_body),
    stringResource(R.string.legal_p_sharing_title) to stringResource(R.string.legal_p_sharing_body),
    stringResource(R.string.legal_p_permissions_title) to stringResource(R.string.legal_p_permissions_body),
    stringResource(R.string.legal_p_vpn_title) to stringResource(R.string.legal_p_vpn_body),
    stringResource(R.string.legal_p_external_title) to stringResource(R.string.legal_p_external_body),
    stringResource(R.string.legal_p_rights_title) to stringResource(R.string.legal_p_rights_body),
    stringResource(R.string.legal_p_disclosure_title) to stringResource(R.string.legal_p_disclosure_body),
    stringResource(R.string.legal_p_transfers_title) to stringResource(R.string.legal_p_transfers_body),
    stringResource(R.string.legal_p_retention_title) to stringResource(R.string.legal_p_retention_body),
    stringResource(R.string.legal_p_children_title) to stringResource(R.string.legal_p_children_body),
    stringResource(R.string.legal_p_security_title) to stringResource(R.string.legal_p_security_body),
    stringResource(R.string.legal_p_consent_title) to stringResource(R.string.legal_p_consent_body),
    stringResource(R.string.legal_p_changes_title) to stringResource(R.string.legal_p_changes_body),
    stringResource(R.string.legal_p_contact_title) to stringResource(R.string.legal_p_contact_body)
)

/**
 * Terms & Conditions sections, resolved at composition time. The order here is the order the
 * user reads; section numbers live in the translated titles, so inserting a section means
 * renumbering every locale in the same change (LocaleParityTest pins the key set, not the text).
 */
@Composable
fun legalTermsSections(): List<Pair<String, String>> = listOf(
    stringResource(R.string.legal_t_license_title) to stringResource(R.string.legal_t_license_body),
    stringResource(R.string.legal_t_ip_title) to stringResource(R.string.legal_t_ip_body),
    stringResource(R.string.legal_t_eligibility_title) to stringResource(R.string.legal_t_eligibility_body),
    stringResource(R.string.legal_t_use_title) to stringResource(R.string.legal_t_use_body),
    stringResource(R.string.legal_t_games_title) to stringResource(R.string.legal_t_games_body),
    stringResource(R.string.legal_t_risk_title) to stringResource(R.string.legal_t_risk_body),
    stringResource(R.string.legal_t_content_title) to stringResource(R.string.legal_t_content_body),
    stringResource(R.string.legal_t_thirdparty_title) to stringResource(R.string.legal_t_thirdparty_body),
    stringResource(R.string.legal_t_payments_title) to stringResource(R.string.legal_t_payments_body),
    stringResource(R.string.legal_t_privacy_title) to stringResource(R.string.legal_t_privacy_body),
    stringResource(R.string.legal_t_updates_title) to stringResource(R.string.legal_t_updates_body),
    stringResource(R.string.legal_t_termination_title) to stringResource(R.string.legal_t_termination_body),
    stringResource(R.string.legal_t_liability_title) to stringResource(R.string.legal_t_liability_body),
    stringResource(R.string.legal_t_indemnity_title) to stringResource(R.string.legal_t_indemnity_body),
    stringResource(R.string.legal_t_law_title) to stringResource(R.string.legal_t_law_body),
    stringResource(R.string.legal_t_misc_title) to stringResource(R.string.legal_t_misc_body),
    stringResource(R.string.legal_t_changes_title) to stringResource(R.string.legal_t_changes_body),
    stringResource(R.string.legal_t_contact_title) to stringResource(R.string.legal_t_contact_body)
)

/**
 * Shared footer linking the website legal page from both in-app legal
 * screens. Opening it is a plain browser intent — it never touches consent.
 */
@Composable
private fun WebsiteLegalFooter(uriHandler: UriHandler, legalUrl: String) {
    Spacer(modifier = Modifier.height(20.dp))
    TextButton(onClick = { uriHandler.openUri(legalUrl) }) {
        Text(
            stringResource(R.string.legal_website_title),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * One headed section of a legal page. Headings use [MaterialTheme.typography.titleSmall]
 * so screen readers and large-text scaling treat them as structure, not decoration.
 */
@Composable
private fun LegalSection(title: String, body: String) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Scrollable dialog rendering the same string resources as the full screens, for surfaces
 * without navigation (the first-run agreement step). Dismisses via its button or tap-outside.
 */
@Composable
fun LegalDialog(
    title: String,
    intro: String,
    sections: List<Pair<String, String>>,
    dismissLabel: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                sections.forEach { (sectionTitle, sectionBody) ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = sectionBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
