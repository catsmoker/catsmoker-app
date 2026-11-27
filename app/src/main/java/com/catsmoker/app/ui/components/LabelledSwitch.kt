package com.catsmoker.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A [Switch] that tells a screen reader which setting it belongs to.
 *
 * Compose binds no label to a switch: the words live in a sibling `Text`, and TalkBack reads the
 * switch's own semantics only, so a bare `Switch` announces "on, switch" with no idea what it
 * controls. Every toggle in this app is drawn that way — icon, title, subtitle, switch — which is
 * right visually and silent with a screen reader. This wrapper attaches the title.
 *
 * The label parameter is required on purpose: the fix for this defect is naming the setting, and an
 * optional parameter is one that gets omitted.
 */
@Composable
fun LabelledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors()
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = colors,
        modifier = modifier.semantics { contentDescription = label }
    )
}

/**
 * The standard "title, subtitle, switch" row used by the Gaming Tools sections and both editors.
 *
 * One composable instead of six copies: the copies had already drifted (some showed the caption in
 * a second line, some inline) and none of them named the switch for a screen reader.
 */
@Composable
fun LabelledSwitchRow(
    label: String,
    caption: String?,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f)
        ) {
            androidx.compose.material3.Text(
                text = label,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
            if (caption != null) {
                androidx.compose.material3.Text(
                    text = caption,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LabelledSwitch(
            label = label,
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
