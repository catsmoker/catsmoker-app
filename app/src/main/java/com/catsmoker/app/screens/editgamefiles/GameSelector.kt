package com.catsmoker.app.screens.editgamefiles

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.Role
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
import com.catsmoker.app.screens.editgamefiles.storage.installSortRank
import com.catsmoker.app.screens.editgamefiles.storage.pubgGroupDot
import com.catsmoker.app.ui.components.ChipFlowRow
import com.catsmoker.app.ui.components.SectionLabel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GameSelector(
    selectedGame: GameType,
    installedGames: Map<GameType, Boolean>,
    onGameSelected: (GameType) -> Unit,
    onLaunchGame: () -> Unit,
    isLoading: Boolean,
    /**
     * False when the host already shows the game list itself (the desktop rail). The
     * trigger is then replaced by a read-only summary, so the same game is never offered
     * twice on one screen.
     */
    showTrigger: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }
    val isPubg = selectedGame in PUBG_VARIANTS
    // The list behind this is one entry per *game*, not per variant: five PUBG rows were
    // five near-identical lines, and nine chips needed swiping. PUBG Mobile is a single
    // entry; picking it (or already having it selected) reveals the variant selector below,
    // which is where the store-variant choice belongs — after the game, before the profile.
    if (showTrigger) {
        GameSelectorTrigger(
            selectedGame = selectedGame,
            installedGames = installedGames,
            isLoading = isLoading,
            onOpenPicker = { showPicker = true },
            onLaunchGame = onLaunchGame
        )
    } else {
        // The rail owns selection here; the card still names the current target.
        SelectedGameSummary(
            selectedGame = selectedGame,
            installedGames = installedGames,
            isLoading = isLoading,
            onLaunchGame = onLaunchGame
        )
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
 * The trigger for the game list. It used to be a disabled `OutlinedTextField` labelled
 * "Game", which read as a text input that refused typing; it is now a bordered row with a
 * chevron, the current target and the install dot, plus the launch button beside it — an
 * action that looks like one.
 */
@Composable
private fun GameSelectorTrigger(
    selectedGame: GameType,
    installedGames: Map<GameType, Boolean>,
    isLoading: Boolean,
    onOpenPicker: () -> Unit,
    onLaunchGame: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable(role = Role.Button, onClick = onOpenPicker)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedGame != GameType.NONE) {
                GameBadge(
                    game = selectedGame,
                    installed = installedGames[selectedGame],
                    installedGames = installedGames
                )
            } else {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectedGame == GameType.NONE) {
                        stringResource(R.string.gf_tap_to_choose_game)
                    } else {
                        selectedGameLabel(selectedGame)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selectedGame != GameType.NONE) {
                    Text(
                        text = stringResource(R.string.gf_change_game),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selectedGame != GameType.NONE) {
                InstallDot(installed = installedGames[selectedGame])
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
}

/**
 * Read-only target line for hosts that select from their own list (the desktop rail).
 * It keeps the launch button: selection moved to the rail, launching did not, and dropping
 * the button here silently removed the only way to start the game from this screen.
 */
@Composable
private fun SelectedGameSummary(
    selectedGame: GameType,
    installedGames: Map<GameType, Boolean>,
    isLoading: Boolean,
    onLaunchGame: () -> Unit
) {
    val installed = installedGames[selectedGame]
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectedGame != GameType.NONE) {
            GameBadge(game = selectedGame, installed = installed, installedGames = installedGames)
            Text(
                text = selectedGameLabel(selectedGame),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            InstallDot(installed = installed)
            FilledIconButton(
                onClick = onLaunchGame,
                enabled = !isLoading,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.gf_launch_game_desc)
                )
            }
        } else {
            Text(
                text = stringResource(R.string.gf_pick_from_list),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One display name for both the trigger and the summary: PUBG is named by its group. */
private fun selectedGameLabel(game: GameType): String =
    if (game in PUBG_VARIANTS) GameType.PUBG_GLOBAL.displayName.removeSuffix(" (Global)")
    else game.displayName

/**
 * The game list as a side rail, for wide windows. Same rows and sort order as the picker
 * dialog (installed first, PUBG collapsed to one group entry), so the two surfaces can
 * never disagree about what is installed or what is selected.
 */
@Composable
internal fun GameLibraryRail(
    selectedGame: GameType,
    installedGames: Map<GameType, Boolean>,
    onGameSelected: (GameType) -> Unit,
    modifier: Modifier = Modifier
) {
    // 33 entries in a 278dp column is a long scroll; the filter is what keeps the rail
    // cheaper than the dialog it replaced. Matches the displayed label, so "pubg" finds the
    // collapsed group entry and "rail" finds both Railroads and Star Rail.
    var query by remember { mutableStateOf("") }
    val otherGroup = GameType.entries.filter { it != GameType.NONE && it !in PUBG_VARIANTS }
    val pubgSelected = selectedGame in PUBG_VARIANTS
    val pubgDot = pubgGroupDot(installedGames)
    val rows = (listOf(Triple(GameType.PUBG_GLOBAL, pubgDot, true)) +
        otherGroup.map { Triple(it, installedGames[it], false) })
        .map { Triple(it.first, it.second, it.third) to railLabel(it.first, it.third) }
        .filter { (_, label) ->
            query.isBlank() || label.contains(query.trim(), ignoreCase = true)
        }
        .sortedBy { installSortRank(it.first.second) }
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.gf_games_rail))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text(stringResource(R.string.gf_filter_games)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.gf_filter_clear)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.gf_filter_no_matches),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
        rows.forEach { (row, label) ->
            val (game, installed, isPubgGroup) = row
            if (isPubgGroup) {
                PickerRow(
                    game = game,
                    label = label,
                    installed = installed,
                    selected = pubgSelected,
                    installedGames = installedGames,
                    onPick = { onGameSelected(GameType.PUBG_GLOBAL) }
                )
            } else {
                PickerRow(game, installed, selectedGame == game, onGameSelected, label = label)
            }
        }
    }
}

/** The label the rail and the dialog actually show, so filtering matches what is on screen. */
private fun railLabel(game: GameType, isPubgGroup: Boolean): String =
    if (isPubgGroup) "PUBG Mobile" else game.displayName

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
    val otherGroup = GameType.entries.filter { it != GameType.NONE && it !in PUBG_VARIANTS }
    val pubgSelected = selectedGame in PUBG_VARIANTS
    // The group dot's three-state rule (any yes → green; all probed and all no → red; a
    // missing probe → unknown) is pure logic, tested in InstallProbesTest.
    val pubgDot = pubgGroupDot(installedGames)
    // Installed games the user can actually edit come first; unknown probes stay between
    // yes and no so an unanswered query is never sorted into "absent". `sortedBy` is
    // stable, so within a band the enum order (and PUBG first) is unchanged.
    val rows = (listOf(Triple(GameType.PUBG_GLOBAL, pubgDot, true)) +
        otherGroup.map { Triple(it, installedGames[it], false) })
        .sortedBy { installSortRank(it.second) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gf_choose_game_title)) },
        text = {
            // Bounded like the Gaming Tools app picker, and scrollable on short/landscape screens.
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rows.forEach { (game, installed, isPubgGroup) ->
                    if (isPubgGroup) {
                        PickerRow(
                            game = game,
                            label = "PUBG Mobile",
                            installed = installed,
                            selected = pubgSelected,
                            installedGames = installedGames,
                            onPick = { onPick(GameType.PUBG_GLOBAL) } // opens the VERSION chips, not a silent Global pick
                        )
                    } else {
                        PickerRow(game, installed, selectedGame == game, onPick)
                    }
                }
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
    label: String = game.displayName,
    installedGames: Map<GameType, Boolean> = emptyMap()
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(game) })
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GameBadge(game = game, installed = installed, installedGames = installedGames)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        InstallDot(installed = installed)
        // A fixed trailing slot keeps every status dot aligned, selected or not.
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
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
