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
