package com.catsmoker.app.screens.gaming.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.catsmoker.app.R

/** Opt-in display of a feature's explicitly safe commands, not a global shell transcript. */
@Composable
internal fun CommandInspector(detail: String, groups: List<Pair<String, String>>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(if (expanded) R.string.gt_advanced_hide else R.string.gt_view_commands))
    }
    if (expanded) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(detail, style = MaterialTheme.typography.bodySmall)
            groups.forEach { (title, command) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText(title, command))
                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.gt_copy_commands))
                    }
                }
                SelectionContainer {
                    Text(command, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
