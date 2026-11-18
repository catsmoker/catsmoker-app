package com.catsmoker.app.screens.gaming

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catsmoker.app.R

@Composable
fun GamingContextBar(gameName: String?, onGlobal: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (gameName == null) Icons.Default.Public else Icons.Default.SportsEsports,
                contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(gameName ?: stringResource(R.string.gt_context_global),
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (gameName != null) TextButton(onClick = onGlobal) {
                Text(stringResource(R.string.gt_context_global))
            }
        }
        Text(stringResource(if (gameName == null) R.string.gt_context_global_help else R.string.gt_context_game_help),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
