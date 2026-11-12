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
import com.catsmoker.app.screens.editgamefiles.EditGameFilesViewModel
import com.catsmoker.app.screens.editgamefiles.games.grid.GridRoute
import com.catsmoker.app.screens.editgamefiles.games.pubg.PubgSavePatcher
import com.catsmoker.app.screens.editgamefiles.games.starrail.HsrGraphicsRoute
import com.catsmoker.app.screens.editgamefiles.games.wutheringwaves.WuwaConfigRoute
import com.catsmoker.app.screens.editgamefiles.model.GameType
import com.catsmoker.app.screens.editgamefiles.storage.ConfigBackupStore
import com.catsmoker.app.ui.components.CardHeading
import com.catsmoker.app.ui.components.ScreenScaffold
import com.catsmoker.app.ui.components.SectionCard
import com.catsmoker.app.ui.theme.CatsmokerTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditGameFilesRoute(onBack: () -> Unit) {
    val viewModel: EditGameFilesViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val zarchiverCopied = stringResource(R.string.gf_zarchiver_copied)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onCustomFilePicked(it) }
    }
    val safPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onSafPicked(it) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onCustomFolderPicked(it) }
    }
    // Install state is probed when the view model is built, but the user installs and
    // uninstalls games while this screen sits open. Re-probing on every resume keeps the
    // dots honest without a refresh button the user must think about (PermissionScreen's
    // observer, same reason it re-checks its grants there).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.probeInstallStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is EditGameFilesViewModel.EditEvent.Toast -> Toast.makeText(context, event.message, if (event.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                EditGameFilesViewModel.EditEvent.LaunchFilePicker -> filePicker.launch("*/*")
                EditGameFilesViewModel.EditEvent.LaunchSafPicker -> safPicker.launch(null)
                is EditGameFilesViewModel.EditEvent.LaunchFolderPicker -> folderPicker.launch(event.initialUri)
                EditGameFilesViewModel.EditEvent.ShowZArchiverDialog -> {
                    Toast.makeText(context, zarchiverCopied, Toast.LENGTH_LONG).show()
                    viewModel.launchZArchiver()
                }
            }
        }
    }
    EditGameFilesScreen(
        uiState = uiState,
        onGameSelected = viewModel::onGameSelected,
        onProfileSelected = viewModel::onProfileSelected,
        onApplyProfile = viewModel::onApplyProfile,
        onMethodSelected = viewModel::onMethodSelected,
        onDismissChooser = viewModel::dismissMethodChooser,
        onResetSave = viewModel::onResetSave,
        onResetMethodSelected = viewModel::onResetMethodSelected,
        onDismissResetChooser = viewModel::dismissResetChooser,
        onRestoreBackup = viewModel::onRestoreBackup,
        onDismissBackupDialog = viewModel::dismissBackupDialog,
        onBackupChosen = viewModel::onBackupChosen,
        onDismissRestoreChooser = viewModel::dismissRestoreChooser,
        onRestoreMethodSelected = viewModel::onRestoreMethodSelected,
        onDeleteBackup = viewModel::onDeleteBackup,
        onSelectFile = viewModel::onSelectFile,
        onUploadFile = viewModel::onUploadFile,
        onUploadTargetSelected = viewModel::onUploadTargetSelected,
        onClearSelection = viewModel::onClearSelection,
        onReadSave = viewModel::onReadSave,
        onSaveEditSelected = viewModel::onSaveEditSelected,
        onApplySaveEdits = viewModel::onApplySaveEdits,
        onRecheckInstalls = viewModel::probeInstallStates,
        onOpenStore = viewModel::openPlayStore,
        onLaunchGame = viewModel::onLaunchGame,
        onShowAnyway = viewModel::onShowEditorAnyway,
        onBack = onBack
    )
}
@Composable
fun EditGameFilesScreen(
    uiState: EditGameFilesViewModel.UiState,
    onGameSelected: (GameType) -> Unit,
    onProfileSelected: (Int) -> Unit,
    onApplyProfile: () -> Unit,
    onMethodSelected: (Int) -> Unit,
    onDismissChooser: () -> Unit,
    onResetSave: () -> Unit,
    onResetMethodSelected: (Int) -> Unit,
    onDismissResetChooser: () -> Unit,
    onRestoreBackup: () -> Unit,
    onDismissBackupDialog: () -> Unit,
    onBackupChosen: (ConfigBackupStore.Entry) -> Unit,
    onDismissRestoreChooser: () -> Unit,
    onRestoreMethodSelected: (Int) -> Unit,
    onDeleteBackup: (ConfigBackupStore.Entry) -> Unit,
    onSelectFile: () -> Unit,
    onUploadFile: () -> Unit,
    onUploadTargetSelected: (Int) -> Unit = {},
    onClearSelection: () -> Unit,
    onReadSave: () -> Unit = {},
    onSaveEditSelected: (String, Int?) -> Unit = { _, _ -> },
    onApplySaveEdits: () -> Unit = {},
    onRecheckInstalls: () -> Unit = {},
    onOpenStore: (String) -> Unit = {},
    onLaunchGame: () -> Unit,
    onShowAnyway: () -> Unit = {},
    onBack: () -> Unit
) {
    ScreenScaffold(
        title = stringResource(R.string.dash_edit_files_title),
        subtitle = stringResource(R.string.gf_edit_files_subtitle),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)
        ) {
            // The warning as one line, not a card: it must be seen, but it does not need a
            // whole surface plus 24dp of spacing above the picker to say two sentences.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    stringResource(R.string.gf_edit_files_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                )
            }
            SectionCard {
                Column {
                    CardHeading(stringResource(R.string.gf_game_section))
                    Spacer(modifier = Modifier.height(10.dp))
                    GameSelector(
                        selectedGame = uiState.selectedGame,
                        installedGames = uiState.installedGames,
                        onGameSelected = onGameSelected,
                        onLaunchGame = onLaunchGame,
                        isLoading = uiState.isLoading
                    )
                    if (uiState.selectedGame != GameType.NONE && !uiState.selectedGame.embeddedEditor &&
                        uiState.installedGames[uiState.selectedGame] != false
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            uiState.selectedGame.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // The three read-modify-write editors (HSR / WuWa / GRID) render right here, below
            // the selector — the screen swaps its body instead of navigating away, so every
            // game is edited from one place. Each brings its own Route (view model, event
            // toasts, pickers) and gets no back arrow of its own: this screen's header is the
            // only header. Their editors own their games' files, so none of the profile-push
            // UI below is shown for them.
            if (uiState.selectedGame.embeddedEditor) {
                Spacer(modifier = Modifier.height(12.dp))
                when (uiState.selectedGame) {
                    GameType.HSR -> HsrGraphicsEmbedded()
                    GameType.WUWA -> WuwaConfigEmbedded()
                    GameType.GRID -> GridEmbedded()
                    else -> {}
                }
            } else if (uiState.selectedGame != GameType.NONE) {
                Spacer(modifier = Modifier.height(12.dp))
                // The same gate the embedded editors apply at load: when the probe says the
                // game is absent, a GAME NOT FOUND card replaces every working section — the
                // "preferences file not found" those editors reported for an uninstalled game
                // was exactly this fact discovered one step later than it needed to be. Unknown
                // (unprobed) shows the sections; only a confirmed "no" hides them. The small
                // secondary "Show anyway" button on the card reveals the same profile editor
                // the installed path shows ([ProfileGameEditor]) below the card — the card
                // itself stays visible, so the screen never pretends the game is installed.
                // EditGameFilesViewModel.UiState.showEditorAnyway is per-selection (reset on
                // every game pick) and survives re-probes, recomposition, and navigating away
                // and back with the screen's ViewModel.
                val gameMissing = uiState.installedGames[uiState.selectedGame] == false
                if (gameMissing && !uiState.showEditorAnyway) {
                    GameNotFoundCard(
                        gameName = uiState.selectedGame.displayName,
                        packageName = uiState.gamePackageName,
                        onRecheck = onRecheckInstalls,
                        onOpenStore = onOpenStore,
                        onShowAnyway = onShowAnyway
                    )
                } else {
                    if (gameMissing) {
                        GameNotFoundCard(
                            gameName = uiState.selectedGame.displayName,
                            packageName = uiState.gamePackageName,
                            onRecheck = onRecheckInstalls,
                            onOpenStore = onOpenStore,
                            onShowAnyway = null
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    ProfileGameEditor(
                        uiState = uiState,
                        onProfileSelected = onProfileSelected,
                        onApplyProfile = onApplyProfile,
                        onResetSave = onResetSave,
                        onRestoreBackup = onRestoreBackup,
                        onSelectFile = onSelectFile,
                        onUploadFile = onUploadFile,
                        onUploadTargetSelected = onUploadTargetSelected,
                        onClearSelection = onClearSelection,
                        onReadSave = onReadSave,
                        onSaveEditSelected = onSaveEditSelected,
                        onApplySaveEdits = onApplySaveEdits
                    )
                }
            }
        }
    }
    if (uiState.showMethodChooser) {
        AlertDialog(
            onDismissRequest = onDismissChooser,
            title = { Text(stringResource(R.string.gf_choose_method_title)) },
            text = { Text(stringResource(R.string.gf_choose_method_body)) },
            confirmButton = {
                Column {
                    TextButton(onClick = { onMethodSelected(0) }) { Text(stringResource(R.string.gf_method_root)) }
                    TextButton(onClick = { onMethodSelected(1) }) { Text(stringResource(R.string.gf_method_shizuku)) }
                    TextButton(onClick = { onMethodSelected(2) }) { Text(stringResource(R.string.gf_method_saf)) }
                    // Hidden when the push must read the file first: a manual copy cannot patch
                    // the save's values or keep the game's own [BackUp DeviceProfile] section.
                    if (!uiState.selectedProfileNeedsExistingFile) {
                        TextButton(onClick = { onMethodSelected(3) }) { Text(stringResource(R.string.gf_method_zarchiver)) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissChooser) { Text(stringResource(R.string.gf_cancel)) }
            }
        )
    }
    if (uiState.showResetChooser) {
        AlertDialog(
            onDismissRequest = onDismissResetChooser,
            title = { Text(stringResource(R.string.gf_reset_save_title)) },
            text = { Text(stringResource(R.string.gf_reset_save_body, uiState.configFileLabel ?: stringResource(R.string.gf_the_save_file))) },
            confirmButton = {
                Column {
                    TextButton(onClick = { onResetMethodSelected(0) }) { Text(stringResource(R.string.gf_method_root)) }
                    TextButton(onClick = { onResetMethodSelected(1) }) { Text(stringResource(R.string.gf_method_shizuku)) }
                    TextButton(onClick = { onResetMethodSelected(2) }) { Text(stringResource(R.string.gf_method_saf)) }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissResetChooser) { Text(stringResource(R.string.gf_cancel)) }
            }
        )
    }
    if (uiState.showBackupDialog) {
        AlertDialog(
            onDismissRequest = onDismissBackupDialog,
            title = { Text(stringResource(R.string.gf_restore_backup_title)) },
            text = {
                // Newest first — the top row is the state immediately before the last overwrite,
                // which is the one "undo" means. Scrolls: the list grows with every write and
                // can exceed the dialog body in landscape.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    uiState.backups.forEach { backup ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBackupChosen(backup) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(backup.formattedTimestamp(), color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    backup.formattedSize(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteBackup(backup) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.gf_delete_backup_desc),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissBackupDialog) { Text(stringResource(R.string.gf_cancel)) }
            }
        )
    }
    if (uiState.showRestoreChooser) {
        AlertDialog(
            onDismissRequest = onDismissRestoreChooser,
            title = { Text(stringResource(R.string.gf_restore_via_title)) },
            text = { Text(stringResource(R.string.gf_restore_via_body)) },
            confirmButton = {
                Column {
                    TextButton(onClick = { onRestoreMethodSelected(0) }) { Text(stringResource(R.string.gf_method_root)) }
                    // Root-only games (New State) offer just the root path — the other two
                    // channels cannot reach a private data file, and listing them would be a
                    // promise they cannot keep.
                    if (!uiState.rootOnlyIni) {
                        TextButton(onClick = { onRestoreMethodSelected(1) }) { Text(stringResource(R.string.gf_method_shizuku)) }
                        TextButton(onClick = { onRestoreMethodSelected(2) }) { Text(stringResource(R.string.gf_method_saf)) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestoreChooser) { Text(stringResource(R.string.gf_cancel)) }
            }
        )
    }
}
@Composable
fun EditGameFilesPreview() {
    CatsmokerTheme {
        EditGameFilesScreen(
            uiState = EditGameFilesViewModel.UiState(
                selectedGame = GameType.PUBG_GLOBAL,
                installedGames = mapOf(GameType.PUBG_GLOBAL to true),
                profileLabelResIds = listOf(R.string.gf_profile_unlock_120, R.string.gf_profile_tablet),
                configFileLabel = "Active.sav",
                canReset = true,
                selectedFileName = "Active.sav",
                canUploadCustom = true,
                canPatchSave = true,
                saveRead = PubgSavePatcher.ReadResult(
                    fields = mapOf(
                        PubgSavePatcher.FIELD_BATTLE_FPS to PubgSavePatcher.FieldOutcome.Value(6),
                        PubgSavePatcher.FIELD_LOBBY_FPS to PubgSavePatcher.FieldOutcome.Value(6),
                        PubgSavePatcher.FIELD_FPS_LEVEL to PubgSavePatcher.FieldOutcome.Value(6),
                        PubgSavePatcher.FIELD_BATTLE_RENDER to PubgSavePatcher.FieldOutcome.Value(1),
                        PubgSavePatcher.FIELD_LOBBY_RENDER to PubgSavePatcher.FieldOutcome.Value(1)
                    ),
                    sizeBytes = 7693,
                    isGvas = true
                ),
                savePatchReport = null
            ),
            onGameSelected = {},
            onProfileSelected = {},
            onApplyProfile = {},
            onMethodSelected = {},
            onDismissChooser = {},
            onResetSave = {},
            onResetMethodSelected = {},
            onDismissResetChooser = {},
            onRestoreBackup = {},
            onDismissBackupDialog = {},
            onBackupChosen = {},
            onDismissRestoreChooser = {},
            onRestoreMethodSelected = {},
            onDeleteBackup = {},
            onSelectFile = {},
            onUploadFile = {},
            onClearSelection = {},
            onReadSave = {},
            onSaveEditSelected = { _, _ -> },
            onApplySaveEdits = {},
            onRecheckInstalls = {},
            onOpenStore = {},
            onLaunchGame = {},
            onBack = {}
        )
    }
}
/**
 * The three editor Routes, hosted inside File Engineering instead of reached as screens of
 * their own — one screen per job was the structure the user pushed back on ("too many
 * screens"). Each wrapper is the feature's own Route with `onBack = null`, so the editor
 * renders its content without a second header/back-arrow below this screen's header; every
 * other behaviour (its view model, its event toasts, its pickers) is exactly what the old
 * dedicated screen ran, unchanged.
 *
 * These live in this file rather than the editor packages because the hosting choice is the
 * File Engineering screen's own decision — the editor features stay agnostic about where they
 * are embedded.
 */
/** Honkai: Star Rail's editor, embedded in File Engineering. */
@Composable
internal fun HsrGraphicsEmbedded() {
    HsrGraphicsRoute(onBack = null)
}
/** Wuthering Waves' config generator, embedded in File Engineering. */
@Composable
internal fun WuwaConfigEmbedded() {
    WuwaConfigRoute(onBack = null)
}
/** GRID Autosport's editor, embedded in File Engineering. */
@Composable
internal fun GridEmbedded() {
    GridRoute(onBack = null)
}
