package com.catsmoker.app.screens.legal

import com.catsmoker.app.R

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared footer linking the website legal page from both in-app legal
 * screens. Opening it is a plain browser intent — it never touches consent.
 */
@Composable
internal fun WebsiteLegalFooter(uriHandler: UriHandler, legalUrl: String) {
    Spacer(modifier = Modifier.height(20.dp))
    TextButton(onClick = { uriHandler.openUri(legalUrl) }) {
        Text(
            stringResource(R.string.legal_website_title),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * One headed section of a legal page. Headings use `MaterialTheme.typography.titleSmall`
 * so screen readers and large-text scaling treat them as structure, not decoration.
 */
@Composable
internal fun LegalSection(title: String, body: String) {
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
