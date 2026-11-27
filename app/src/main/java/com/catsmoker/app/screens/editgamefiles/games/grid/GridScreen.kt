package com.catsmoker.app.screens.editgamefiles.games.grid

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.defaultMinSize
import com.catsmoker.app.ui.components.LabelledSwitch
import com.catsmoker.app.screens.editgamefiles.editor.rememberEditorTab
import com.catsmoker.app.screens.editgamefiles.editor.EditorTabSpec
import com.catsmoker.app.R
import com.catsmoker.app.screens.editgamefiles.editor.GameEditorLayout
import com.catsmoker.app.screens.editgamefiles.editor.DeployActions
import com.catsmoker.app.screens.editgamefiles.editor.DeployView
import com.catsmoker.app.screens.editgamefiles.editor.GameEditorSection
import com.catsmoker.app.screens.editgamefiles.editor.HostedDeploy

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.ui.components.CardHeading
import com.catsmoker.app.ui.components.CatsmokerButton
import com.catsmoker.app.ui.components.CatsmokerOutlinedButton
import com.catsmoker.app.ui.components.ChipFlowRow
import com.catsmoker.app.ui.components.ScreenScaffold
import com.catsmoker.app.ui.components.SectionCard
import kotlinx.coroutines.flow.collectLatest

/**
 * GRID Autosport graphics editor. Cloned from the HSR Graphics screen's shape, per the house
 * convention, with one structural difference: the SAF folder picker is launched from here
 * (not from a parked pending-action), because the pick is only ever a *grant* — the apply
 * that needs it re-runs from the button like any other apply.
 *
 * Hosted two ways: as a destination of its own (non-null [onBack]) or embedded inside File
 * Engineering's game selector (null — no second header below that screen's).
 *
 * Card rhythm is the host's: cards carry no vertical padding of their own and the content
 * column spaces its children 12 dp apart, so an embedded editor reads as more sections of
 * the File Engineering screen rather than a different screen pasted below the selector —
 * the per-card `vertical = 8.dp` padding used to read exactly that way. Action buttons are
 * uppercase to match the host's APPLY PROFILE / RESTORE BACKUP idiom.
 */
@Composable
fun GridRoute(onBack: (() -> Unit)? = null) {
    val viewModel: GridViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel, onBack == null) {
        if (onBack == null) viewModel.onShowEditorAnyway()
        if (!viewModel.uiState.value.loading) viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is GridViewModel.GridEvent.Toast ->
                    Toast.makeText(context, event.message, if (event.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                is GridViewModel.GridEvent.LaunchFolderPicker -> Unit // handled by the launcher below
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onSafFolderPicked(uri)
    }
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            if (event is GridViewModel.GridEvent.LaunchFolderPicker) {
                runCatching { folderPicker.launch(null) }
            }
        }
    }

    GridScreen(
        uiState = uiState,
        onSetResolution = viewModel::setResolution,
        onSetFps = viewModel::setFps,
        onSetFxaa = viewModel::setFxaa,
        onSetWindowScale = viewModel::setWindowScale,
        onSetLadder = viewModel::setLadder,
        onSetAnisotropic = viewModel::setAnisotropic,
        onSetSwitch = viewModel::setSwitch,
        onApply = viewModel::onApply,
        onRestoreBackup = viewModel::onRestoreBackup,
        onPickFolder = viewModel::requestFolderPicker,
        onRefresh = viewModel::refresh,
        onShowAnyway = viewModel::onShowEditorAnyway,
        onBack = onBack
    )
}

/**
 * The three groups this editor's cards fall into. Order matters: DISPLAY is where a user tuning for
 * frame rate starts, QUALITY is the long ladder, SWITCHES is the per-file boolean list.
 */
private enum class GridEditorTab(@StringRes val label: Int, val icon: ImageVector) {
    DISPLAY(R.string.gf_tab_display, Icons.Default.Monitor),
    QUALITY(R.string.gf_tab_quality, Icons.Default.Star),
    SWITCHES(R.string.gf_tab_switches, Icons.Default.Layers)
}

@Composable
private fun gridEditorTabs(): List<EditorTabSpec> = GridEditorTab.entries.map {
    EditorTabSpec(it.name, stringResource(it.label), it.icon)
}

@Composable
fun GridScreen(
    uiState: GridUiState,
    onSetResolution: (Int) -> Unit,
    onSetFps: (Int) -> Unit,
    onSetFxaa: (Boolean) -> Unit,
    onSetWindowScale: (Int) -> Unit,
    onSetLadder: (String, String) -> Unit,
    onSetAnisotropic: (String) -> Unit,
    onSetSwitch: (String, Boolean) -> Unit,
    onApply: () -> Unit,
    onRestoreBackup: () -> Unit,
    onPickFolder: () -> Unit,
    onRefresh: () -> Unit,
    onShowAnyway: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    // Advanced keys (FXAA, window scale) stay available but out of the beginner's way.
    var showAdvanced by remember { mutableStateOf(false) }
    // Restore replaces the live file and clears the working copy; ask before discarding edits.
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val content: @Composable () -> Unit = {
        GameEditorLayout(standalone = onBack != null) {
            // The install gate, mirroring the File Engineering screen's own pattern: a
            // confirmed-missing install shows the GAME NOT FOUND failure card with a small
            // secondary "Show anyway" button, and tapping it reveals the existing editor
            // below the card (the ViewModel seeds an empty read — see onShowEditorAnyway).
            // Other failure stages keep their card with no button, and installed games
            // render the editor exactly as before. Missing is the probe's answer, not the
            // stage: a channel-less device reports NO_CHANNEL for a game that is not there.
            val missingInstall = uiState.gameMissing
            when {
                uiState.loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                uiState.loadFailure != null && onBack != null && (!missingInstall || !uiState.showEditorAnyway) ->
                    GridLoadFailureCard(
                        uiState.loadFailure,
                        onPickFolder,
                        onRefresh,
                        onShowAnyway = if (missingInstall) onShowAnyway else null
                    )

                uiState.read != null -> {
                    val read = uiState.read

                    if (uiState.loadFailure != null && (!missingInstall || onBack != null)) {
                        GridLoadFailureCard(uiState.loadFailure, onPickFolder, onRefresh, onShowAnyway = null)
                    }

                    // One group of cards at a time. The form put a preference changed at the top five
                    // swipes away from APPLY, and the pinned bar alone cannot fix reach inside the form.
                    val gridTab = rememberEditorTab(gridEditorTabs(), GridEditorTab.DISPLAY.name)
                    gridTab.row()
                    when (gridTab.selected) {
                        GridEditorTab.DISPLAY.name -> {
                    SectionCard {
                        CardHeading(stringResource(R.string.gf_frame_rate))
                        Spacer(modifier = Modifier.height(8.dp))
                        // Only the game's highest tier: a 30/60/90/120 choice would write a
                        // lower ceiling, which the no-cap rule forbids.
                        ChipFlowRow {
                            FilterChip(
                                selected = uiState.edits.fps == GridPreferences.MAX_FPS,
                                onClick = { onSetFps(GridPreferences.MAX_FPS) },
                                label = { Text("${GridPreferences.MAX_FPS}") }
                            )
                        }
                        if (read.maxFps != null && uiState.edits.fps == null) {
                            Text(
                                stringResource(
                                    R.string.gf_grid_device_holds_fps,
                                    read.maxFps,
                                    read.highMaxFps ?: read.maxFps
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            stringResource(R.string.gf_grid_fps_caption),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }


                    SectionCard {
                        CardHeading(stringResource(R.string.gf_resolution_section))
                        Spacer(modifier = Modifier.height(8.dp))
                        ChipFlowRow {
                            listOf(480, 600, 720, 1080, 1440).forEach { height ->
                                FilterChip(
                                    selected = uiState.edits.screenHeight == height,
                                    onClick = { onSetResolution(height) },
                                    label = { Text("${height}p") }
                                )
                            }
                        }
                        Text(
                            currentResolutionLine(read, uiState),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                        }
                        GridEditorTab.QUALITY.name -> {


                    SectionCard {
                        CardHeading(stringResource(R.string.gf_quality))
                        Spacer(modifier = Modifier.height(4.dp))
                        read.ladder.forEach { (name, current) ->
                            val enabled = current != null
                            LadderRow(
                                label = ladderLabel(name),
                                enabled = enabled,
                                options = GridPreferences.QUALITY_TIERS,
                                selected = uiState.edits.ladder[name],
                                current = current,
                                onSelect = { onSetLadder(name, it) }
                            )
                        }
                        LadderRow(
                            label = stringResource(R.string.gf_grid_aniso),
                            enabled = read.anisotropic != null,
                            options = listOf("off") + GridPreferences.QUALITY_TIERS,
                            selected = uiState.edits.anisotropic,
                            current = read.anisotropic,
                            onSelect = onSetAnisotropic
                        )
                        Text(
                            stringResource(R.string.gf_grid_greyed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                        }
                        else -> {


                    SectionCard {
                        CardHeading(stringResource(R.string.gf_perf_switches))
                        Spacer(modifier = Modifier.height(4.dp))
                        GridPreferences.SWITCH_TARGETS.forEach { (name, _) ->
                            val current = read.switches[name]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(switchLabel(name), color = if (current == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                    Text(
                                        when {
                                            current == null -> stringResource(R.string.gf_grid_not_in_file)
                                            else -> stringResource(R.string.gf_grid_device_holds, if (current) "on" else "off")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LabelledSwitch(
                                    label = switchLabel(name),
                                    checked = uiState.edits.switches[name] ?: current ?: false,
                                    enabled = current != null,
                                    onCheckedChange = { checked -> onSetSwitch(name, checked) }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // The shipped reference file's two non-gfxconfig_* keys, absent from its
                        // README list: FXAA and the high-preset window scale. FXAA is an integer
                        // 0/1 switch; the scale is the file's own 0-100 integer (the verified
                        // file ships 80). Both stay available, collapsed behind MORE OPTIONS so
                        // the first screen is the switches a beginner recognizes.
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.gf_grid_switch_caption),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { showAdvanced = !showAdvanced },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                stringResource(
                                    if (showAdvanced) R.string.gf_fewer_options
                                    else R.string.gf_more_options
                                )
                            )
                        }
                        if (showAdvanced) {
                            val fxaaCurrent = read.fxaa
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(switchLabel("fxaa"), color = if (fxaaCurrent == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                    Text(
                                        when {
                                            fxaaCurrent == null -> stringResource(R.string.gf_grid_not_in_file)
                                            else -> stringResource(R.string.gf_grid_device_holds, if (fxaaCurrent) "on" else "off")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LabelledSwitch(
                                    label = switchLabel("fxaa"),
                                    checked = uiState.edits.fxaa ?: fxaaCurrent ?: false,
                                    enabled = fxaaCurrent != null,
                                    onCheckedChange = { checked -> onSetFxaa(checked) }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.gf_grid_window_scale),
                                color = if (read.windowScale == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            ChipFlowRow {
                                listOf(50, 60, 70, 80, 90, 100).forEach { scale ->
                                    FilterChip(
                                        selected = uiState.edits.windowScale == scale,
                                        enabled = read.windowScale != null,
                                        onClick = { onSetWindowScale(scale) },
                                        label = { Text("$scale%") }
                                    )
                                }
                            }
                            Text(
                                when {
                                    read.windowScale == null -> stringResource(R.string.gf_grid_not_in_file)
                                    else -> stringResource(R.string.gf_grid_device_holds, "${read.windowScale}%")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.gf_grid_window_scale_caption),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                        }
                    }
                        }
                    }
                    // Whether Apply would write anything: the working copy differs from the file.
                    // Computed once here because the deploy block, the restore guard and the bar
                    // all ask the same question.
                    val hasPendingEdits = uiState.edits != GridPreferences.Edits()

                    // The primary write lives in the pinned DeployBar when this editor is
                    // embedded (editor/DeployBar.kt); the card used to sit mid-form, so a
                    // preference changed at the top had to be scrolled to the bottom to apply.
                    // Standalone, the same block still draws inline.
                    HostedDeploy(
                        view = DeployView(
                            applyLabel = stringResource(R.string.gf_apply_to_game),
                            dirty = hasPendingEdits,
                            busy = uiState.applying
                        ),
                        actions = DeployActions(apply = onApply),
                        inline = {
                            GameEditorSection(stringResource(R.string.gf_deploy)) {
                                CatsmokerButton(
                                    onClick = onApply,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.applying && hasPendingEdits,
                                    isLoading = uiState.applying,
                                    loadingLabel = stringResource(R.string.gf_applying)
                                ) {
                                    Text(stringResource(R.string.gf_apply_to_game))
                                }
                                if (!hasPendingEdits && !uiState.applying) {
                                    Text(
                                        stringResource(R.string.gf_no_pending_changes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )

                    // Restore and the last-apply report stay in the flow: one is the way back from
                    // a write, the other is the evidence of what the last one did.
                    GameEditorSection(stringResource(R.string.gf_restore_backup)) {
                        if (uiState.hasBackup) {
                            CatsmokerOutlinedButton(
                                onClick = {
                                    if (hasPendingEdits) showRestoreConfirm = true else onRestoreBackup()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.applying
                            ) {
                                Text(stringResource(R.string.gf_restore_last_backup))
                            }
                            Text(
                                stringResource(R.string.gf_restore_backup_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        uiState.lastApply?.let { report ->
                            Text(stringResource(R.string.gf_last_apply, report), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                }

                // Defensive: override active but no read (seeding is the ViewModel's job and
                // always runs first) — keep the indicator rather than showing nothing.
                uiState.loadFailure != null ->
                    GridLoadFailureCard(uiState.loadFailure, onPickFolder, onRefresh, onShowAnyway = null)
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.gf_restore_confirm_title)) },
            text = { Text(stringResource(R.string.gf_restore_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        onRestoreBackup()
                    }
                ) { Text(stringResource(R.string.gf_restore_last_backup)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.gf_cancel))
                }
            }
        )
    }

    if (onBack == null) {
        content()
    } else {
        ScreenScaffold(
            title = "GRID Autosport",
            subtitle = uiState.gameVersion ?: stringResource(R.string.gf_grid_graphics_editor),
            onBack = onBack
        ) {
            content()
        }
    }
}

@Composable
private fun currentResolutionLine(read: GridPreferences.ReadResult, uiState: GridUiState): String {
    val current = if (read.screenWidth != null && read.screenHeight != null) {
        stringResource(R.string.gf_grid_res_holds, read.screenWidth, read.screenHeight)
    } else {
        stringResource(R.string.gf_grid_res_unknown)
    }
    return if (uiState.edits.screenHeight != null) {
        stringResource(
            R.string.gf_grid_res_will_write,
            current,
            uiState.edits.screenWidth?.toString() ?: stringResource(R.string.gf_derived),
            uiState.edits.screenHeight
        )
    } else {
        stringResource(R.string.gf_grid_res_keys, current)
    }
}

@Composable
private fun ladderLabel(key: String): String = when (key) {
    "car_reflection" -> stringResource(R.string.gf_grid_ladder_car)
    "dynamic_ambient_occ" -> stringResource(R.string.gf_grid_ladder_ao)
    "night_lighting" -> stringResource(R.string.gf_grid_ladder_night)
    "voice_count" -> stringResource(R.string.gf_grid_ladder_voice)
    else -> key.replaceFirstChar { it.uppercase() }
}

@Composable
private fun switchLabel(key: String): String = when (key) {
    "advanced_fog" -> stringResource(R.string.gf_grid_sw_fog)
    "advanced_lighting" -> stringResource(R.string.gf_grid_sw_lighting)
    "dynamic_ambient_occ_soft" -> stringResource(R.string.gf_grid_sw_soft_ao)
    "fxaa" -> stringResource(R.string.gf_grid_sw_fxaa)
    "global_illumination" -> stringResource(R.string.gf_grid_sw_gi)
    "groundCover" -> stringResource(R.string.gf_grid_sw_ground)
    "multisampling" -> stringResource(R.string.gf_grid_sw_msaa)
    "skidmarks" -> stringResource(R.string.gf_grid_sw_skid)
    else -> key.replaceFirstChar { it.uppercase() }
}

/**
 * What a closed ladder row reports: the pending choice when there is one, otherwise what the file
 * currently holds. The row must never show the old tier while a new one is queued for writing —
 * that is the difference between "this is set to High" and "this will be High".
 */
internal fun ladderRowValue(selected: String?, current: String?): String? = selected ?: current

@Composable
private fun LadderRow(
    label: String,
    enabled: Boolean,
    options: List<String>,
    selected: String?,
    current: String?,
    onSelect: (String) -> Unit
) {
    // Sixteen quality axes times five tiers is eighty chips: as a list of open rows this card was
    // four screens of scrolling, and the one thing a tuning screen must answer first — "what is it
    // set to now?" — was the thing you had to scroll to find. Closed, each row shows the label and
    // the tier the file holds, so all sixteen read at a glance; opening one swaps in the tiers.
    var open by rememberSaveable(label) { mutableStateOf(false) }
    val value = ladderRowValue(selected, current)
    val expandLabel = stringResource(R.string.gf_grid_row_expand)
    val collapseLabel = stringResource(R.string.gf_grid_row_collapse)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) collapseLabel else expandLabel) { open = if (open) false else true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value ?: stringResource(R.string.gf_grid_not_in_file),
                style = MaterialTheme.typography.bodySmall,
                // Same ink either way: the text itself says the key is missing, and `outline` on
                // this surface sat below the contrast floor for small text.
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(18.dp)
            )
        }
        if (open) {
            // Every tier visible at once — a wrapping flow, never sideways scroll.
            ChipFlowRow {
                options.forEach { tier ->
                    FilterChip(
                        selected = selected == tier,
                        enabled = enabled,
                        onClick = { onSelect(tier) },
                        label = { Text(tier) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun GridLoadFailureCard(
    failure: GridPreferencesManager.ReadResult.Failure,
    onPickFolder: () -> Unit,
    onRefresh: () -> Unit,
    onShowAnyway: (() -> Unit)? = null
) {
    // Host-styled failure surface: the File Engineering screen's GAME NOT FOUND card is a
    // SectionCard with a CardHeading (error red for a missing game, primary otherwise),
    // so a load failure here reads as the same kind of thing, not a different component.
    // The small secondary "Show anyway" button is offered only when the caller passes it —
    // callers do so solely for a confirmed-missing install, so other stages keep no button.
    SectionCard {
        Column {
            when (failure.stage) {
                GridPreferencesManager.ReadResult.Stage.NO_CHANNEL -> {
                    CardHeading(stringResource(R.string.gf_grid_no_channel_title))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.gf_grid_no_channel_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CatsmokerButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.gf_grid_pick_folder))
                    }
                }
                GridPreferencesManager.ReadResult.Stage.GAME_NOT_INSTALLED -> {
                    CardHeading(
                        text = stringResource(R.string.gf_game_not_found),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.gf_grid_not_installed_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
                GridPreferencesManager.ReadResult.Stage.FILE_NOT_FOUND -> {
                    CardHeading(stringResource(R.string.gf_grid_prefs_title))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.gf_grid_prefs_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
                GridPreferencesManager.ReadResult.Stage.READ_FAILED -> {
                    CardHeading(stringResource(R.string.gf_could_not_read_title))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.gf_read_failed_detail, failure.detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
            if (failure.stage != GridPreferencesManager.ReadResult.Stage.NO_CHANNEL) {
                Spacer(modifier = Modifier.height(12.dp))
                CatsmokerOutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gf_retry))
                }
            }
            if (onShowAnyway != null) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onShowAnyway,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.gf_show_anyway))
                }
            }
        }
    }
}
