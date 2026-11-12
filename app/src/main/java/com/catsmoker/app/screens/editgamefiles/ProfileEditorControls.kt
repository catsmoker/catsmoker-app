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
import com.catsmoker.app.screens.editgamefiles.games.pubg.PubgSaveEditorSection
import com.catsmoker.app.ui.components.CardHeading
import com.catsmoker.app.ui.components.CatsmokerButton
import com.catsmoker.app.ui.components.CatsmokerOutlinedButton
import com.catsmoker.app.ui.components.ChipFlowRow
import com.catsmoker.app.ui.components.SectionCard
import com.catsmoker.app.ui.components.SquigglyProgressBar
import kotlinx.coroutines.flow.collectLatest

/**
 * The profile-game editor: PROFILE card, CUSTOM FILE UPLOAD card (PUBG only), and the
 * inline SAVE editor (verified GVAS layout only). One implementation shared by the
 * installed path and the "Show anyway" path — the screen never keeps two copies of these
 * sections, so a fix to any of them applies to both.
 */
@Composable
internal fun ProfileGameEditor(
    uiState: EditGameFilesViewModel.UiState,
    onProfileSelected: (Int) -> Unit,
    onApplyProfile: () -> Unit,
    onResetSave: () -> Unit,
    onRestoreBackup: () -> Unit,
    onSelectFile: () -> Unit,
    onUploadFile: () -> Unit,
    onUploadTargetSelected: (Int) -> Unit = {},
    onClearSelection: () -> Unit,
    onReadSave: () -> Unit,
    onSaveEditSelected: (String, Int?) -> Unit,
    onApplySaveEdits: () -> Unit
) {
    Column {
        SectionCard {
            Column {
                CardHeading(stringResource(R.string.gf_profile_section))
                // Only this card's own jobs light the bar: push / reset / restore /
                // backup-delete. Other cards stay dark while their buttons disable.
                val profileBusy = uiState.busyArea == EditGameFilesViewModel.BusyArea.PROFILE_PUSH ||
                    uiState.busyArea == EditGameFilesViewModel.BusyArea.RESET ||
                    uiState.busyArea == EditGameFilesViewModel.BusyArea.RESTORE ||
                    uiState.busyArea == EditGameFilesViewModel.BusyArea.BACKUP_DELETE
                if (profileBusy) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SquigglyProgressBar(progress = null, animate = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(10.dp))
                // Per-game label IDs from the view model — PUBG has three profiles, Genshin
                // one, New State one — resolved here (not in the ViewModel) so they follow the
                // current language.
                uiState.profileLabelResIds.forEachIndexed { index, labelResId ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onProfileSelected(index) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = uiState.selectedProfile == index, onClick = { onProfileSelected(index) })
                        Text(stringResource(labelResId), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                // A root-only game (New State) carries the fact inside this actionable card
                // rather than a card of its own: APPLY writes through root and nothing else
                // can reach the file.
                if (uiState.rootOnlyIni) {
                    Text(
                        stringResource(R.string.gf_newstate_need_root),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                CatsmokerButton(
                    onClick = onApplyProfile,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.busyArea == null,
                    isLoading = uiState.busyArea == EditGameFilesViewModel.BusyArea.PROFILE_PUSH,
                    loadingLabel = stringResource(R.string.gf_working)
                ) {
                    Text(stringResource(R.string.gf_apply_profile))
                }
                // Games with a resettable file only (config's resetFilePath): deleting it lets
                // the game regenerate from defaults — the revert for every push above. The
                // label is the game's own file name; Genshin has no reset and hides the button.
                if (uiState.canReset && uiState.configFileLabel != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CatsmokerOutlinedButton(
                        onClick = onResetSave,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.busyArea == null,
                        isLoading = uiState.busyArea == EditGameFilesViewModel.BusyArea.RESET,
                        loadingLabel = stringResource(R.string.gf_working),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.gf_reset_file, uiState.configFileLabel).uppercase())
                    }
                }
                // Every overwrite above takes a timestamped backup first; this is the way
                // back to any of them. Available for every game, unlike the reset.
                Spacer(modifier = Modifier.height(8.dp))
                CatsmokerOutlinedButton(
                    onClick = onRestoreBackup,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.busyArea == null,
                    isLoading = uiState.busyArea == EditGameFilesViewModel.BusyArea.RESTORE ||
                        uiState.busyArea == EditGameFilesViewModel.BusyArea.BACKUP_DELETE,
                    loadingLabel = stringResource(R.string.gf_working)
                ) {
                    Text(stringResource(R.string.gf_restore_backup))
                }

                // One card now owns both ways to change a file: choose a bundled profile,
                // or import a file this editor actually understands. The separate upload card
                // made the two look unrelated and hid the selected destination.
                if (uiState.canUploadCustom) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    Spacer(modifier = Modifier.height(14.dp))
                    CardHeading(stringResource(R.string.gf_custom_upload))
                    if (uiState.busyArea == EditGameFilesViewModel.BusyArea.CUSTOM_UPLOAD) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SquigglyProgressBar(progress = null, animate = true, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.gf_custom_file_type),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ChipFlowRow {
                        uiState.uploadTargets.forEachIndexed { index, target ->
                            FilterChip(
                                selected = uiState.selectedUploadTarget == index,
                                onClick = { onUploadTargetSelected(index) },
                                enabled = !uiState.isLoading,
                                label = { Text(stringResource(target.labelRes)) }
                            )
                        }
                    }
                    uiState.uploadDestination?.let { destination ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.gf_custom_destination, destination),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        uiState.selectedFileName?.let { name ->
                            stringResource(R.string.selected_item, name)
                        } ?: stringResource(R.string.selected_item_placeholder),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatsmokerOutlinedButton(
                            onClick = onSelectFile,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isLoading
                        ) { Text(stringResource(R.string.gf_select)) }
                        CatsmokerButton(
                            onClick = onUploadFile,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isLoading
                        ) {
                            if (uiState.busyArea == EditGameFilesViewModel.BusyArea.CUSTOM_UPLOAD) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.gf_upload))
                            }
                        }
                    }
                    if (uiState.selectedFileName != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = onClearSelection,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                stringResource(R.string.gf_clear_selection),
                                color = Color.Red.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // The inline save editor: read-modify-write of the ints the game's own save
        // carries — the closed-source references' core mechanism, without replacing
        // the file. Only for the game whose layout is verified ([canPatchSave]).
        if (uiState.canPatchSave) {
            Spacer(modifier = Modifier.height(12.dp))
            PubgSaveEditorSection(
                uiState = uiState,
                onReadSave = onReadSave,
                onSaveEditSelected = onSaveEditSelected,
                onApplySaveEdits = onApplySaveEdits
            )
        }
    }
}

/**
 * The GAME NOT FOUND card, shown in place of every working section when the probe confirms
 * the game's package is absent. Names the exact package the probe checked (a one-size-fits-all
 * "no PUBG-family package" line once claimed this under Genshin, which never looks at a PUBG
 * package), re-checks on tap for the just-installed case, and offers the store listing.
 * The small secondary "Show anyway" [TextButton] (offered only when [onShowAnyway] is
 * non-null, i.e. the editor is still hidden) reveals the profile editor below this card
 * without pretending the game is installed — the card stays visible either way.
 */
@Composable
internal fun GameNotFoundCard(
    gameName: String,
    packageName: String?,
    onRecheck: () -> Unit,
    onOpenStore: (String) -> Unit,
    onShowAnyway: (() -> Unit)? = null
) {
    SectionCard {
        Column {
            CardHeading(
                text = stringResource(R.string.gf_game_not_found),
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.gf_game_not_installed, gameName) +
                    (if (packageName != null) stringResource(R.string.gf_package_not_found, packageName) else "") +
                    stringResource(R.string.gf_install_run_once),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatsmokerOutlinedButton(onClick = onRecheck, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.gf_recheck))
                }
                if (packageName != null) {
                    CatsmokerButton(onClick = { onOpenStore(packageName) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.gf_open_play_store))
                    }
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

