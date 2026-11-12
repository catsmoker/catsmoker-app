package com.catsmoker.app.screens.legal

import com.catsmoker.app.R

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catsmoker.app.screens.legal.LegalSection
import com.catsmoker.app.screens.legal.WebsiteLegalFooter
import com.catsmoker.app.ui.components.ScreenScaffold

/** Legal content is shared with onboarding; consent behavior is unchanged. */
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
 * A short index of the Terms sections a first-time user should see before accepting.
 *
 * Only the already-translated section titles are reused — no new legal copy is written and
 * no clause is reworded. The full text stays one tap away through [legalTermsSections] and
 * the onboarding dialog, so this list is a table of contents, not a substitute.
 */
@Composable
fun legalTermsHighlights(): List<String> = listOf(
    stringResource(R.string.legal_t_license_title),
    stringResource(R.string.legal_t_use_title),
    stringResource(R.string.legal_t_games_title),
    stringResource(R.string.legal_t_risk_title)
)
