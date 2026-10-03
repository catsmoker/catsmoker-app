package com.catsmoker.app.features.spoofdevice

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.catsmoker.app.R
import com.catsmoker.app.shared.data.model.DevicePreset
import com.catsmoker.app.shared.ui.components.CatsmokerButton
import androidx.compose.material3.TextButton

/**
 * One shared preset selector, used by the creation dialog and the profile editor.
 *
 * Hierarchy is strict and identical everywhere: the two special built-in actions
 * first ([Current Device][Custom Configuration]), then a divider, then the saved
 * presets (author-curated built-ins in their own order, user presets last).
 * Specials are never sorted with presets and never deletable; only `user:` presets
 * carry a trash icon, and it always asks first.
 */
object PresetSelector {

    sealed interface Entry {
        data class Special(val preset: DevicePreset) : Entry
        data object SavedHeader : Entry
        data class Saved(val preset: DevicePreset) : Entry
    }

    /**
     * Pure ordering for the selector: `current_device` + `custom` on top (in that
     * order, when present), everything else below in input order. Unknown ids fall
     * through to the saved section rather than vanishing. Unit-tested.
     */
    fun order(presets: List<DevicePreset>): List<Entry> {
        val (specials, rest) = presets.partition { it.id == "current_device" || it.id == "custom" }
        val orderedSpecials = listOfNotNull(
            specials.firstOrNull { it.id == "current_device" },
            specials.firstOrNull { it.id == "custom" }
        ) + specials.filter { it.id != "current_device" && it.id != "custom" }
        if (rest.isEmpty()) return orderedSpecials.map { Entry.Special(it) }
        return orderedSpecials.map { Entry.Special(it) } +
            Entry.SavedHeader +
            rest.map { Entry.Saved(it) }
    }

    /** A preset id this selector allows deleting: user presets only, never specials. */
    fun isRemovable(preset: DevicePreset): Boolean = preset.id.startsWith("user:")
}

/**
 * The menu body: specials on top, divider + saved header, trash on user presets.
 * Trash only requests deletion — the caller confirms via [PresetDeleteDialog].
 */
@Composable
fun PresetSelectorMenu(
    presets: List<DevicePreset>,
    hint: String,
    onPick: (DevicePreset) -> Unit,
    onDeleteRequest: (DevicePreset) -> Unit
) {
    PresetSelector.order(presets).forEach { entry ->
        when (entry) {
            is PresetSelector.Entry.Special -> SelectorItem(
                preset = entry.preset,
                hint = hint,
                onPick = onPick,
                onDeleteRequest = null
            )
            is PresetSelector.Entry.SavedHeader -> {
                HorizontalDivider()
                Text(
                    stringResource(R.string.spoof_selector_saved),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is PresetSelector.Entry.Saved -> SelectorItem(
                preset = entry.preset,
                hint = hint,
                onPick = onPick,
                onDeleteRequest = if (PresetSelector.isRemovable(entry.preset)) onDeleteRequest else null
            )
        }
    }
}

@Composable
private fun SelectorItem(
    preset: DevicePreset,
    hint: String,
    onPick: (DevicePreset) -> Unit,
    onDeleteRequest: ((DevicePreset) -> Unit)?
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preset.displayName.trim().ifBlank { hint },
                    modifier = Modifier.weight(1f)
                )
                if (onDeleteRequest != null) {
                    IconButton(onClick = { onDeleteRequest(preset) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.spoof_action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        onClick = { onPick(preset) }
    )
}

/**
 * Shared delete confirmation: names the preset and states that profiles made
 * from it are unaffected (they hold their own copied values).
 */
@Composable
fun PresetDeleteDialog(
    preset: DevicePreset,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spoof_dialog_delete_preset_title, preset.displayName.trim())) },
        text = { Text(stringResource(R.string.spoof_dialog_delete_preset_text)) },
        confirmButton = {
            CatsmokerButton(onClick = onConfirm) {
                Text(stringResource(R.string.spoof_action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.spoof_action_cancel))
            }
        }
    )
}
