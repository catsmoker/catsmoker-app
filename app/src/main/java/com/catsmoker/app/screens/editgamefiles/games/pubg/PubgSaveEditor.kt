package com.catsmoker.app.screens.editgamefiles.games.pubg

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
import com.catsmoker.app.screens.editgamefiles.games.pubg.PubgSavePatcher
import com.catsmoker.app.screens.editgamefiles.games.pubg.PubgSaveSelection
import com.catsmoker.app.ui.components.CardHeading
import com.catsmoker.app.ui.components.CatsmokerButton
import com.catsmoker.app.ui.components.CatsmokerOutlinedButton
import com.catsmoker.app.ui.components.ChipFlowRow
import com.catsmoker.app.ui.components.SectionCard
import com.catsmoker.app.ui.components.SquigglyProgressBar
import kotlinx.coroutines.flow.collectLatest

/**
 * The PUBG save editor: one READ step, then frame-rate and render-quality chip rows cloned from
 * the GRID editor's ladder rows. The chips stay disabled until a read has succeeded — the
 * editor edits values the file carries, it never assumes defaults — and the APPLY writes through
 * the same pull/backup/patch/push/read-back pipeline as everything else here.
 */
@Composable
internal fun PubgSaveEditorSection(
    uiState: EditGameFilesViewModel.UiState,
    onReadSave: () -> Unit,
    onSaveEditSelected: (String, Int?) -> Unit,
    onApplySaveEdits: () -> Unit
) {
    val read = uiState.saveRead

    SectionCard {
        Column {
            CardHeading(stringResource(R.string.gf_save_editor))
            // Same card for read + apply: the bar shows for either, the spinner only on the tapped button.
            val saveBusy = uiState.busyArea == EditGameFilesViewModel.BusyArea.SAVE_READ ||
                uiState.busyArea == EditGameFilesViewModel.BusyArea.SAVE_APPLY
            if (saveBusy) {
                Spacer(modifier = Modifier.height(8.dp))
                SquigglyProgressBar(progress = null, animate = true, modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                stringResource(R.string.gf_save_editor_desc, uiState.configFileLabel ?: stringResource(R.string.gf_save_fallback)),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (read == null) {
                CatsmokerButton(
                    onClick = onReadSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.busyArea == null,
                    isLoading = uiState.busyArea == EditGameFilesViewModel.BusyArea.SAVE_READ,
                    loadingLabel = stringResource(R.string.gf_reading)
                ) {
                    Text(stringResource(R.string.gf_read_save))
                }
            } else {
                Text(
                    stringResource(R.string.gf_file_holds, read.summary()),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                val observed = read.observedSummary()
                if (observed != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.gf_also_holds, observed),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            uiState.savePatchReport?.let { report ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.gf_last_apply, report),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            CardHeading(stringResource(R.string.gf_frame_rate))
            Spacer(modifier = Modifier.height(6.dp))
            val fpsEditable = PubgSaveSelection.fpsEditable(read)
            if (fpsEditable) {
                ChipFlowRow {
                    PubgSavePatcher.FPS_TIERS.forEach { tier ->
                        val selected = PubgSaveSelection.selectedFps(read, uiState.saveEdits) == tier.fps
                        FilterChip(
                            selected = selected,
                            enabled = !uiState.isLoading,
                            onClick = { onSaveEditSelected(PubgSavePatcher.FIELD_BATTLE_FPS, tier.fps) },
                            label = { Text("${tier.fps} FPS") }
                        )
                    }
                }
            } else if (read != null) {
                Text(
                    stringResource(R.string.gf_save_field_unsupported),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            CardHeading(stringResource(R.string.gf_render_quality))
            Spacer(modifier = Modifier.height(6.dp))
            val renderEditable = PubgSaveSelection.renderEditable(read)
            if (renderEditable) {
                ChipFlowRow {
                    PubgSavePatcher.RENDER_TIERS.forEach { tier ->
                        val selected =
                            PubgSaveSelection.selectedRender(read, uiState.saveEdits) == tier.value
                        FilterChip(
                            selected = selected,
                            enabled = !uiState.isLoading,
                            onClick = { onSaveEditSelected(PubgSavePatcher.FIELD_BATTLE_RENDER, tier.value) },
                            label = { Text(tier.label) }
                        )
                    }
                }
            } else if (read != null) {
                Text(
                    stringResource(R.string.gf_save_field_unsupported),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            CardHeading(stringResource(R.string.gf_camera_view))
            Spacer(modifier = Modifier.height(6.dp))
            // The old group rendered two chips that could never be clicked on saves lacking
            // one of the pair. Explain the omission instead of showing dead controls.
            if (PubgSaveSelection.viewEditable(read)) {
                ChipFlowRow {
                    PubgSavePatcher.VIEW_PRESETS.forEach { preset ->
                        val selected =
                            PubgSaveSelection.selectedView(read, uiState.saveEdits) == preset
                        FilterChip(
                            selected = selected,
                            enabled = !uiState.isLoading,
                            onClick = { onSaveEditSelected(PubgSavePatcher.FIELD_TP_VIEW, preset.tpView) },
                            label = { Text(preset.label) }
                        )
                    }
                }
            } else if (read != null) {
                Text(
                    stringResource(R.string.gf_save_field_unsupported),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                )
            }

            if (uiState.saveEdits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.gf_unsaved),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatsmokerOutlinedButton(
                    onClick = onReadSave,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.busyArea == null,
                    isLoading = uiState.busyArea == EditGameFilesViewModel.BusyArea.SAVE_READ,
                    loadingLabel = stringResource(R.string.gf_reading)
                ) {
                    Text(stringResource(R.string.gf_reread))
                }
                CatsmokerButton(
                    onClick = onApplySaveEdits,
                    modifier = Modifier.weight(1f),
                    enabled = read != null && uiState.saveEdits.isNotEmpty() &&
                        uiState.busyArea == null,
                    isLoading = uiState.busyArea == EditGameFilesViewModel.BusyArea.SAVE_APPLY,
                    loadingLabel = stringResource(R.string.gf_applying)
                ) {
                    Text(stringResource(R.string.gf_apply))
                }
            }
        }
    }
}

