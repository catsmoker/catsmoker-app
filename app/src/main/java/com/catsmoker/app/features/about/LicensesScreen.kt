package com.catsmoker.app.features.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.components.ScreenScaffold
import com.catsmoker.app.system.VariantCapabilities

/**
 * Third-party license inventory, moved out of Settings (Help & Info) into About → Legal.
 *
 * This is a full screen rather than a dialog because the list scrolls: the old alert dialog
 * squeezed everything into a small box and offered a "Later" button, which made no sense for
 * a reference document (there is nothing to postpone). The header's "Close" action and the
 * system back gesture both return to About.
 *
 * License names and library names are proper nouns, so they live in untranslated string
 * resources; only the surrounding chrome is localized.
 */
@Composable
fun LicensesRoute(onBack: () -> Unit) {
    LicensesScreen(onBack = onBack)
}

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    ScreenScaffold(
        title = stringResource(R.string.about_licenses_title),
        subtitle = stringResource(R.string.about_licenses_subtitle),
        trailingContent = {
            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.info_close),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.about_licenses_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // A license inventory is a reference document, not a set of controls: it uses
                // the same plain heading + body typography as the Privacy/Terms screens instead
                // of informational-only cards (CODING_STYLE "no informational-only cards").
                licenseEntries().forEach { entry ->
                    Column {
                        Text(
                            text = entry.license,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = entry.components,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.about_copyright),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

/** One license and the libraries that ship under it. */
private data class LicenseEntry(val license: String, val components: String)

/**
 * Resolved at composition so a language switch re-reads every row. The ads entry names the
 * SDK this distribution actually bundles: Start.io in the full build, AdMob in the Play build.
 */
@Composable
private fun licenseEntries(): List<LicenseEntry> = listOf(
    LicenseEntry(
        license = stringResource(R.string.about_license_apache),
        components = stringResource(R.string.about_license_apache_libs)
    ),
    LicenseEntry(
        license = stringResource(R.string.about_license_google),
        components = stringResource(R.string.about_license_google_libs)
    ),
    LicenseEntry(
        license = stringResource(R.string.about_license_ads),
        components = stringResource(
            if (VariantCapabilities.HAS_PLAY_STORE) {
                R.string.about_license_ads_play
            } else {
                R.string.about_license_ads_full
            }
        )
    )
)
