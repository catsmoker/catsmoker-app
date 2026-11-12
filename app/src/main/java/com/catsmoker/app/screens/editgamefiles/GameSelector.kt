package com.catsmoker.app.screens.editgamefiles

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.R
import com.catsmoker.app.screens.editgamefiles.model.GameType
import com.catsmoker.app.screens.editgamefiles.storage.PUBG_VARIANTS
import com.catsmoker.app.screens.editgamefiles.storage.pubgGroupDot
import com.catsmoker.app.ui.components.ChipFlowRow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GameSelector(
    selectedGame: GameType,
    installedGames: Map<GameType, Boolean>,
    onGameSelected: (GameType) -> Unit,
    onLaunchGame: () -> Unit,
    isLoading: Boolean
) {
    var showPicker by remember { mutableStateOf(false) }
    val isPubg = selectedGame in PUBG_VARIANTS
    // A tappable field opening the full game list — one entry per *game*, not per variant:
    // five PUBG rows were five near-identical lines, and nine chips needed swiping. PUBG
    // Mobile is a single entry here; picking it (or already having it selected) reveals the
    // variant selector below, which is where the store-variant choice belongs — after the
    // game is chosen, before the profile rows.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable { showPicker = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = when {
                    selectedGame == GameType.NONE -> stringResource(R.string.gf_tap_to_choose_game)
                    isPubg -> GameType.PUBG_GLOBAL.displayName.removeSuffix(" (Global)")
                    else -> selectedGame.displayName
                },
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.gf_game_label)) },
                placeholder = { Text(stringResource(R.string.gf_tap_to_choose_game)) },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                leadingIcon = if (selectedGame != GameType.NONE) {
                    // The selected game's own dot, so the field itself carries the install
                    // state the dialog rows show — no need to reopen the dialog to see it.
                    { InstallDot(installed = installedGames[selectedGame]) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
        }
        FilledIconButton(
            onClick = onLaunchGame,
            enabled = selectedGame != GameType.NONE && !isLoading,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.gf_launch_game_desc))
        }
    }
    // The second selector: which PUBG store-variant the profile flow targets. Visible only
    // once a PUBG entry is the selected game — the group entry in the dialog opens this,
    // it does not silently pick Global.
    if (isPubg) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(stringResource(R.string.gf_version_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        // All six variants at once: a wrapping flow, never a sideways-scrolling strip —
        // every store version stays visible and directly tappable on any width.
        ChipFlowRow {
            PUBG_VARIANTS.forEach { variant ->
                val variantInstalled = installedGames[variant]
                FilterChip(
                    selected = selectedGame == variant,
                    onClick = { onGameSelected(variant) },
                    label = { Text(variant.variantLabel ?: variant.shortLabel) },
                    leadingIcon = { InstallDot(installed = variantInstalled) },
                    // The dot already says absent; a chip you cannot select says it twice
                    // and blocks re-selecting to read the caption's explanation.
                    enabled = true
                )
            }
        }
    }
    if (showPicker) {
        GamePickerDialog(
            selectedGame = selectedGame,
            installedGames = installedGames,
            onDismiss = { showPicker = false },
            onPick = { game ->
                onGameSelected(game)
                showPicker = false
            }
        )
    }
}
/**
 * The full game list — one entry per game. The PUBG variants collapse into the single
 * "PUBG Mobile" row (their dot is green when *any* variant is installed, red only when none
 * of the six is, unknown when unprobed); choosing it reveals the variant chips under the
 * field, so the two decisions — which game, which store version — happen in two steps.
 */
@Composable
internal fun GamePickerDialog(
    selectedGame: GameType,
    installedGames: Map<GameType, Boolean>,
    onDismiss: () -> Unit,
    onPick: (GameType) -> Unit
) {
    val otherGroup = listOf(GameType.GENSHIN_IMPACT, GameType.PUBG_NEW_STATE, GameType.HSR, GameType.WUWA, GameType.GRID)
    val pubgSelected = selectedGame in PUBG_VARIANTS
    // The group dot's three-state rule (any yes → green; all probed and all no → red; a
    // missing probe → unknown) is pure logic, tested in InstallProbesTest.
    val pubgDot = pubgGroupDot(installedGames)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gf_choose_game_title)) },
        text = {
            // Six rows do not fit a landscape dialog; without this the last game is
            // clipped and unreachable because the dialog body itself does not scroll.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PickerRow(
                    game = GameType.PUBG_GLOBAL,
                    label = "PUBG Mobile",
                    installed = pubgDot,
                    selected = pubgSelected,
                    onPick = { onPick(GameType.PUBG_GLOBAL) } // opens the VERSION chips, not a silent Global pick
                )
                otherGroup.forEach { game -> PickerRow(game, installedGames[game], selectedGame == game, onPick) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.gf_cancel)) }
        }
    )
}
@Composable
internal fun PickerRow(
    game: GameType,
    installed: Boolean?,
    selected: Boolean,
    onPick: (GameType) -> Unit,
    label: String = game.displayName
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onPick(game) }
            .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InstallDot(installed = installed)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.gf_selected_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
/** Install-state dot beside a game chip: filled green, hollow unknown, red absent. */
@Composable
internal fun InstallDot(installed: Boolean?) {
    val color = when (installed) {
        true -> Color(0xFF81C784)
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}
