package com.catsmoker.app.screens.gaming.compilation

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.R
import com.catsmoker.app.screens.gaming.GameInfo
import com.catsmoker.app.screens.gaming.compilation.BoosterOutcome
import com.catsmoker.app.screens.gaming.compilation.BoosterRun
import com.catsmoker.app.screens.gaming.compilation.BoosterState
import com.catsmoker.app.screens.gaming.compilation.DexoptScheduleStore
import com.catsmoker.app.screens.gaming.components.CollapsibleExplainer
import com.catsmoker.app.screens.gaming.components.InfoTopics
import com.catsmoker.app.screens.gaming.components.formatScheduleTime
import com.catsmoker.app.screens.gaming.components.intervalLabel
import com.catsmoker.app.ui.components.CatsmokerButton
import com.catsmoker.app.ui.components.ChipFlowRow
import com.catsmoker.app.ui.components.InfoButton
import com.catsmoker.app.ui.components.SquigglyProgressBar
import com.catsmoker.app.ui.components.buttonColors
import com.catsmoker.app.ui.theme.LogTerminalBackground
import com.catsmoker.app.ui.theme.logLineColor
import java.io.File

/**
 * The ART compilation card.
 *
 * ## Why there is only one profile now
 *
 * This card used to offer `speed-profile`, `speed` and `everything` as three buttons with no
 * explanation of any of them. Only one has a defensible reason to exist here:
 *
 * - `speed-profile` compiles just the methods ART has already recorded as hot in that app's local
 *   profile. It is what the platform runs by itself, on idle, every night. Choosing it manually
 *   mostly re-does work already done, and on an app you have never opened there is no profile to
 *   compile from, so it does almost nothing.
 * - `speed` compiles every method ahead of time, so nothing has to be JIT-compiled while the app is
 *   running. That removes the compile pauses that show up as stutter in the first minutes of play and
 *   after loading screens. It is the one mode with a real gaming rationale, and it is also the mode
 *   the reference project uses — `cmd package compile -m speed -f <pkg>` — as its only mode.
 * - `everything` compiles every method *including* ones ART deliberately excludes as never-executed
 *   debug and error paths. It takes substantially longer and produces a much larger odex for no
 *   in-game benefit, because those methods do not run.
 *
 * So the picker is gone and the sweep is fixed at `speed`. The force checkbox stays: without `-f` the
 * platform skips any app already in the requested filter, so a second run would report success and
 * compile nothing.
 *
 * Everything shown here comes from [BoosterState], which the engine only advances when a command
 * actually ran and answered: the status line, the counts and the bar all describe the real sweep,
 * and a run that could not start says why instead of showing an empty progress bar.
 */
@Composable
fun AppBoosterContent(
    state: BoosterState,
    log: List<String>,
    history: List<BoosterRun>,
    games: List<GameInfo>,
    /** Single-game scope, or null for the all-games sweep. Shared with the library button. */
    scopePkg: String?,
    /** Package with a single-game pre-compile in flight, or null. */
    optimizingSinglePkg: String?,
    onScopeChange: (String?) -> Unit,
    onOptimizeSingle: (String) -> Unit,
    scheduleEnabled: Boolean,
    scheduleIntervalHours: Int,
    scheduleNextRunAt: Long?,
    onScheduleEnabledChange: (Boolean) -> Unit,
    onScheduleIntervalChange: (Int) -> Unit,
    onRun: (String, Boolean) -> Unit,
    onStop: () -> Unit
) {
    var force by remember { mutableStateOf(false) }
    Column {
        // One backend, two scopes (F8): the all-games sweep (`speed`, minutes) and the
        // single-game pre-compile (`speed-profile`, seconds). The library PRE-COMPILE
        // button deep-links here through the same scope state, so both entries run one
        // system instead of looking like two features.
        Text(
            text = stringResource(R.string.gt_booster_scope_title),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = scopePkg == null,
                onClick = { onScopeChange(null) },
                label = { Text(stringResource(R.string.gt_booster_scope_all)) }
            )
            FilterChip(
                selected = scopePkg != null,
                onClick = {
                    if (games.isNotEmpty()) {
                        onScopeChange(
                            games.firstOrNull { it.packageName == scopePkg }?.packageName
                                ?: games.first().packageName
                        )
                    }
                },
                enabled = games.isNotEmpty(),
                label = { Text(stringResource(R.string.gt_booster_scope_single)) }
            )
        }
        Text(
            text = if (scopePkg == null) stringResource(R.string.gt_booster_scope_hint_all)
            else stringResource(R.string.gt_booster_scope_hint_single),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (scopePkg != null) {
            Spacer(modifier = Modifier.height(4.dp))
            if (games.isEmpty()) {
                Text(
                    text = stringResource(R.string.gt_booster_no_games_scope),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    games.forEach { game ->
                        FilterChip(
                            selected = game.packageName == scopePkg,
                            onClick = { onScopeChange(game.packageName) },
                            label = {
                                Text(
                                    game.appName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                CatsmokerButton(
                    onClick = { onOptimizeSingle(scopePkg) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isRunning && optimizingSinglePkg == null
                ) {
                    Text(stringResource(R.string.gt_booster_precompile))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Without -f the platform skips any app already in the requested filter, so a re-run would
        // report success having compiled nothing. Kept as the reference project's "force optimize".
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = force, onCheckedChange = { force = it }, enabled = !state.isRunning)
            Text(stringResource(R.string.booster_force_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (state.isRunning) {
            CatsmokerButton(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.booster_stop)) }
        } else {
            CatsmokerButton(onClick = { onRun("speed", force) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.booster_start))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = boosterStatusText(state),
                fontSize = 12.sp,
                color = if (state.outcome is BoosterOutcome.Unavailable || state.outcome is BoosterOutcome.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        if (state.isRunning) {
            Spacer(modifier = Modifier.height(8.dp))
            val progress = state.progress
            if (progress == null) {
                // Still querying the app list: there is no percentage yet, so a
                // travelling wave shows work without pretending a percentage.
                SquigglyProgressBar(progress = null, animate = true)
            } else {
                SquigglyProgressBar(progress = progress, animate = true)
            }
        }
        if (log.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(8.dp)).background(LogTerminalBackground).padding(8.dp)) {
                val scroll = rememberScrollState()
                LaunchedEffect(log.size) { scroll.scrollTo(scroll.maxValue) }
                Column(modifier = Modifier.verticalScroll(scroll)) {
                    log.forEach { line ->
                        Text(
                            text = line,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = logLineColor(line)
                        )
                    }
                }
            }
        }
        // The log above dies with the app; this is every finished sweep the device actually
        // reported, newest first, so "what did the last boost do" has an answer tomorrow too.
        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            CollapsibleExplainer(
                title = stringResource(R.string.gt_booster_history),
                lines = history.asReversed().take(10).map { run -> boosterHistoryLine(run) }
            )
        }
        // The recurring version of the same sweep, driven by WorkManager while the app is closed.
        // Its stated limits are in the section's info sheet — rough intervals, skipped (not
        // failed) runs without privilege, and WorkManager's estimate rather than a promised time.
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))
        DexoptScheduleSection(
            enabled = scheduleEnabled,
            intervalHours = scheduleIntervalHours,
            nextRunAt = scheduleNextRunAt,
            onEnabledChange = onScheduleEnabledChange,
            onIntervalChange = onScheduleIntervalChange
        )
    }
}
/**
 * The recurring dexopt sweep's controls. Every claim on the row is something the device or
 * WorkManager reported: the switch state is the persisted setting *and* the enrollment, and the
 * next-run line is WorkManager's own current estimate — "unknown" when it will not say, never a
 * time nobody promised.
 */
@Composable
internal fun DexoptScheduleSection(
    enabled: Boolean,
    intervalHours: Int,
    nextRunAt: Long?,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.gt_booster_schedule),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    when {
                        enabled && nextRunAt != null -> stringResource(R.string.gt_booster_next, formatScheduleTime(nextRunAt))
                        enabled -> stringResource(R.string.gt_booster_next_unknown)
                        else -> stringResource(R.string.gt_booster_sched_off)
                    },
                    fontSize = 11.sp,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            InfoButton(InfoTopics.dexoptSchedule)
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            // Changing the interval retunes the schedule in place (UPDATE, not REPLACE), so the
            // countdown is not restarted from zero on every tap.
            // Four chips wrap instead of scrolling: always fully visible.
            ChipFlowRow {
                DexoptScheduleStore.INTERVAL_CHOICES.forEach { hours ->
                    FilterChip(
                        selected = intervalHours == hours,
                        onClick = { onIntervalChange(hours) },
                        label = { Text(intervalLabel(hours)) }
                    )
                }
            }
        }
    }
}
@Composable
internal fun boosterHistoryLine(run: BoosterRun): String {
    val locale = LocalConfiguration.current.locales[0]
    val when_ = java.text.SimpleDateFormat("d MMM, HH:mm", locale)
        .format(java.util.Date(run.startedAt))
    val minutes = run.durationMs / 60000
    val seconds = (run.durationMs % 60000) / 1000
    val duration = stringResource(R.string.gt_booster_hist_dur, minutes, seconds)
    val tail = when (run.outcome) {
        "cancelled" -> stringResource(R.string.gt_booster_hist_cancelled)
        "failed" -> stringResource(R.string.gt_booster_hist_failed)
        else -> ""
    }
    return stringResource(
        R.string.gt_booster_hist,
        when_,
        run.optimized,
        run.skipped,
        run.failed,
        duration,
        tail
    )
}
/** One line saying what the sweep is doing, in counts the engine measured. */
@Composable
internal fun boosterStatusText(state: BoosterState): String = when (val outcome = state.outcome) {
    BoosterOutcome.Idle -> stringResource(R.string.booster_status_idle)
    BoosterOutcome.Running -> state.currentPackage?.let { pkg ->
        stringResource(R.string.booster_status_compiling, state.processedCount + 1, state.totalCount, pkg)
    } ?: stringResource(R.string.booster_status_preparing)
    BoosterOutcome.Completed -> stringResource(
        R.string.booster_status_done, state.optimizedCount, state.skippedCount, state.failedCount
    )
    BoosterOutcome.Cancelled -> stringResource(R.string.booster_status_cancelled, state.processedCount)
    // The engine's own words for what the device refused — not a generic failure message.
    is BoosterOutcome.Unavailable -> outcome.reason
    is BoosterOutcome.Failed -> stringResource(R.string.booster_status_failed, outcome.reason)
}
