package com.catsmoker.app.features.gamingtools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.catsmoker.app.R
import com.catsmoker.app.features.gamingtools.engine.GamingModeNotice
import com.catsmoker.app.features.gamingtools.engine.GamingModeReport
import com.catsmoker.app.features.gamingtools.engine.GamingModeState
import com.catsmoker.app.features.gamingtools.engine.PointerSpeed
import com.catsmoker.app.features.gamingtools.tools.interventions.GameInterventions
import com.catsmoker.app.shared.ui.components.ChipFlowRow
import com.catsmoker.app.shared.ui.components.SectionCard
import com.catsmoker.app.shared.ui.components.SquigglyProgressBar
import java.util.Locale

/**
 * The Gaming Mode card.
 *
 * @param canActivate whether root or Shizuku is available. Every optimization Gaming Mode applies is a
 *   privileged command, so without one of those channels the whole feature can do nothing at all. The
 *   power button is disabled and the reason is stated on the card rather than letting the user press a
 *   live button and watch it fail. It goes live on its own as soon as either channel appears, because
 *   the state comes from the privilege flow the screen re-reads on every sync.
 */
@Composable
fun GamingModeCard(
    gamingState: GamingModeState,
    report: GamingModeReport,
    animatedProgress: Float,
    canActivate: Boolean,
    isActive: Boolean,
    isBusy: Boolean,
    /** Render scale for the next activation, or null for full resolution. */
    downscale: Float?,
    /** Chooses the render scale; takes effect on the next activation, never mid-session. */
    onDownscaleChange: (Float?) -> Unit,
    /** Touch speed for the next activation, or null for stock (untouched). */
    pointerSpeed: Int?,
    /** Chooses the touch speed; takes effect on the next activation, never mid-session. */
    onPointerSpeedChange: (Int?) -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit
) {
    SectionCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.gt_gm_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (gamingState) {
                            is GamingModeState.Active -> stringResource(R.string.gt_gm_active)
                            // The engine already reports which step it is on, so show that rather
                            // than a generic word.
                            is GamingModeState.Enabling -> gamingState.statusText
                            is GamingModeState.Disabling -> stringResource(R.string.gt_gm_reverting)
                            is GamingModeState.Error -> stringResource(R.string.gt_gm_failed)
                            is GamingModeState.Idle ->
                                if (canActivate) stringResource(R.string.gt_gm_ready) else stringResource(R.string.gt_gm_locked)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = when {
                            gamingState is GamingModeState.Error -> MaterialTheme.colorScheme.error
                            // Item 8: a feature that cannot run must look like it cannot run.
                            !canActivate -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                IconButton(
                    onClick = { if (isActive) onDeactivate() else onActivate() },
                    enabled = !isBusy && canActivate,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            1.dp,
                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = when {
                            isActive -> MaterialTheme.colorScheme.primary
                            !canActivate -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Item 2: the requirement is stated on the card, above everything else, so the greyed-out
            // button is never a mystery. It disappears by itself the moment either channel appears.
            if (!canActivate) {
                Spacer(modifier = Modifier.height(16.dp))
                NoticeBlock(
                    text = stringResource(R.string.gt_gm_need),
                    tint = Color(0xFFFFB300)
                )
                Spacer(modifier = Modifier.height(8.dp))
                CollapsibleExplainer(
                    title = stringResource(R.string.gt_gm_what_title),
                    lines = listOf(
                        stringResource(R.string.gt_gm_what_1),
                        stringResource(R.string.gt_gm_what_2),
                        stringResource(R.string.gt_gm_what_3)
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Render scale for the next activation. Changing the table under a running game
            // would need a restart to take hold anyway, so the chips lock while active — the
            // choice waits for the next run instead of pretending to apply right now.
            if (!isActive) {
                DownscaleRow(
                    current = downscale,
                    enabled = !isBusy && canActivate,
                    onSelect = onDownscaleChange
                )
                Spacer(modifier = Modifier.height(8.dp))
                PointerSpeedRow(
                    current = pointerSpeed,
                    enabled = !isBusy && canActivate,
                    onSelect = onPointerSpeedChange
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Android 13 media-player squiggle: the played stretch waves while the
            // engine is working and relaxes to a line when idle, like a paused track.
            SquigglyProgressBar(
                progress = animatedProgress.coerceIn(0f, 1f),
                animate = isBusy,
                modifier = Modifier.fillMaxWidth()
            )

            if (gamingState is GamingModeState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                NoticeBlock(text = gamingState.message, tint = MaterialTheme.colorScheme.error)
            }

            if (isActive) {
                Spacer(modifier = Modifier.height(24.dp))
                // Every row below is a value the engine read back from the device after writing it,
                // so a change the ROM refused shows as refused instead of as a success.
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GamingModeResultRow(
                        label = "PowerHAL",
                        value = if (report.fixedPerformance) stringResource(R.string.gt_gm_fixed) else stringResource(R.string.gt_gm_not_applied),
                        applied = report.fixedPerformance
                    )
                    // Null means the device carries no vendor GPU mode property at all — every
                    // non-Qualcomm SoC — so the row is omitted rather than shown as refused, the
                    // same rule as the game frame cap row below.
                    report.gpuPerformanceMode?.let { applied ->
                        GamingModeResultRow(
                            label = stringResource(R.string.gt_gm_label_gpu),
                            value = if (applied) stringResource(R.string.gt_gm_qc_perf) else stringResource(R.string.gt_gm_not_applied),
                            applied = applied
                        )
                    }
                    // Same null rule — omitted on non-Qualcomm silicon. The value shown is the
                    // measured panel peak the hint carries, read back from the property.
                    report.qtiGameFps?.let { applied ->
                        GamingModeResultRow(
                            label = stringResource(R.string.gt_gm_label_fps_hint),
                            value = if (applied) stringResource(R.string.gt_gm_qc_peak) else stringResource(R.string.gt_gm_not_applied),
                            applied = applied
                        )
                    }
                    GamingModeResultRow(
                        label = stringResource(R.string.gt_gm_label_display),
                        value = report.lockedRefreshHz?.let { stringResource(R.string.gt_gm_locked_hz, it) } ?: stringResource(R.string.gt_gm_rate_unlocked),
                        applied = report.lockedRefreshHz != null
                    )
                    GamingModeResultRow(
                        label = stringResource(R.string.gt_gm_label_touch),
                        value = (if (report.touchResponseBoost) stringResource(R.string.gt_gm_touch_boost) else stringResource(R.string.gt_gm_touch_na)) +
                            (report.pointerSpeed?.let { " · ${formatPointerSpeed(it)}" } ?: ""),
                        applied = report.touchResponseBoost
                    )
                    GamingModeResultRow(
                        label = stringResource(R.string.gt_gm_label_bg_apps),
                        value = when {
                            report.suspendedPackages > 0 && report.suspendFailures > 0 ->
                                stringResource(R.string.gt_gm_susp_both, report.suspendedPackages, report.suspendFailures)
                            report.suspendedPackages > 0 -> stringResource(R.string.gt_gm_susp_some, report.suspendedPackages)
                            report.suspendFailures > 0 -> stringResource(R.string.gt_gm_susp_refused, report.suspendFailures)
                            else -> stringResource(R.string.gt_gm_susp_none)
                        },
                        applied = report.suspendedPackages > 0
                    )
                    GamingModeResultRow(
                        label = stringResource(R.string.gt_gm_label_dnd),
                        value = if (report.dndEngaged) stringResource(R.string.gt_gm_dnd_on) else stringResource(R.string.gt_gm_dnd_off),
                        applied = report.dndEngaged
                    )
                    // Null means notification access was never granted, so the second layer was
                    // never switchable on this run — omitted rather than shown as refused, the
                    // same rule as the background-data row below.
                    report.notificationSuppression?.let {
                        GamingModeResultRow(
                            label = stringResource(R.string.gt_gm_label_notif),
                            value = if (it) stringResource(R.string.gt_gm_notif_on) else stringResource(R.string.gt_gm_notif_off),
                            applied = it
                        )
                    }
                    report.networkWhitelisted?.let { whitelisted ->
                        GamingModeResultRow(
                            label = stringResource(R.string.gt_gm_label_net),
                            value = if (whitelisted) stringResource(R.string.gt_gm_net_ok) else stringResource(R.string.gt_gm_net_no),
                            applied = whitelisted
                        )
                    }
                    // Null means no game was targeted or the device predates game interventions
                    // (Android 12) — not applicable, so the row is omitted rather than shown as
                    // refused, exactly like the background-data row above.
                    report.gameInterventionApplied?.let { applied ->
                        GamingModeResultRow(
                            label = stringResource(R.string.gt_gm_label_cap),
                            value = if (applied) {
                                stringResource(R.string.gt_gm_cap_raised) +
                                    (report.gameInterventionDownscale?.let { " · ${formatDownscale(it)}" } ?: "")
                            } else {
                                stringResource(R.string.gt_gm_cap_no)
                            },
                            applied = applied
                        )
                    }
                    // Reported from the read-back of always_finish_activities, so "Applied" means the
                    // setting holds 1 right now rather than that the command was sent.
                    GamingModeResultRow(
                        label = stringResource(R.string.gt_gm_label_discard),
                        value = if (report.discardActivities) stringResource(R.string.gt_gm_discard_on) else stringResource(R.string.gt_gm_not_applied),
                        applied = report.discardActivities
                    )
                    GamingModeResultRow(
                        label = stringResource(R.string.gt_gm_label_limit),
                        value = if (report.processLimit) stringResource(R.string.gt_gm_limit) else stringResource(R.string.gt_gm_not_applied),
                        applied = report.processLimit
                    )
                    GamingModeResultRow(
                        label = stringResource(R.string.gt_gm_label_other_data),
                        value = when (report.backgroundDataRestricted) {
                            // null means the user's own switch was already on, so this run left it
                            // alone — saying "not applied" would misreport a deliberate decision.
                            null -> stringResource(R.string.gt_gm_left_as_set)
                            true -> stringResource(R.string.gt_gm_blocked_metered)
                            false -> stringResource(R.string.gt_gm_not_blocked)
                        },
                        applied = report.backgroundDataRestricted != false
                    )
                }

                if (report.unavailable.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val reasons = report.unavailable.map { resolveNotice(it) }
                    NoticeBlock(
                        text = stringResource(R.string.gt_gm_unavailable_title) + "\n" +
                            reasons.joinToString("\n") { "• $it" },
                        tint = Color(0xFFFFB300)
                    )
                }
            }

            // Revert problems live in this same list, so they must not hide behind isActive:
            // deactivation reports refused wake-ups and leftover blocks here, and a half-reverted
            // device that looks clean is exactly how apps stay stopped with no explanation.
            if (!isActive && report.unavailable.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                val leftovers = report.unavailable.map { resolveNotice(it) }
                NoticeBlock(
                    text = stringResource(R.string.gt_gm_revert_leftovers) + "\n" +
                        leftovers.joinToString("\n") { "• $it" },
                    tint = Color(0xFFFFB300)
                )
            }
        }
    }
}

/**
 * Render-scale picker for the next Gaming Mode activation: full resolution plus the official
 * downscale steps (0.9 near-lossless down to the 0.7 floor the platform docs recommend).
 *
 * A stored scale that is not one of the chips is shown as it is rather than snapped — the
 * chips would otherwise misreport what the next activation will write.
 */
@Composable
private fun DownscaleRow(
    current: Float?,
    enabled: Boolean,
    onSelect: (Float?) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.gt_downscale_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.gt_downscale_sub),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (current != null && GameInterventions.DOWNSCALE_CHOICES.none { kotlin.math.abs(it - current) < 0.005f }) {
            Text(formatDownscale(current), fontSize = 10.sp, color = Color(0xFFFFB74D))
        }
        // Seven chips wrap onto two lines on a phone instead of overflowing the row.
        ChipFlowRow {
            FilterChip(
                selected = current == null,
                onClick = { onSelect(null) },
                enabled = enabled,
                label = { Text(stringResource(R.string.gt_downscale_off), fontSize = 11.sp) }
            )
            GameInterventions.DOWNSCALE_CHOICES.forEach { value ->
                FilterChip(
                    selected = current != null && kotlin.math.abs(current - value) < 0.005f,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    label = { Text(formatDownscale(value), fontSize = 11.sp) }
                )
            }
        }
    }
}

/** `0.85x` — the same spelling the intervention entry carries. */
private fun formatDownscale(value: Float): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.') + "x"

/** `+2`, `-3` — the sign always shows, so slower and faster never look alike. */
private fun formatPointerSpeed(speed: Int): String =
    if (speed > 0) "+$speed" else "$speed"

/**
 * Touch-speed picker for the next Gaming Mode activation: stock (untouched) plus symmetric
 * steps each way. Stock is the default because "don't touch it" already is stock.
 */
@Composable
private fun PointerSpeedRow(
    current: Int?,
    enabled: Boolean,
    onSelect: (Int?) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.gt_pointer_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.gt_pointer_sub),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        // A stored speed outside the offered steps is shown as it is rather than snapped —
        // the chips would otherwise misreport what the next activation writes.
        if (current != null && !PointerSpeed.CHOICES.contains(current)) {
            Text(formatPointerSpeed(current), fontSize = 10.sp, color = Color(0xFFFFB74D))
        }
        // Seven chips wrap onto two lines on a phone instead of overflowing the row.
        ChipFlowRow {
            FilterChip(
                selected = current == null,
                onClick = { onSelect(null) },
                enabled = enabled,
                label = { Text(stringResource(R.string.gt_pointer_stock), fontSize = 11.sp) }
            )
            PointerSpeed.CHOICES.forEach { value ->
                FilterChip(
                    selected = current == value,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    label = { Text(formatPointerSpeed(value), fontSize = 11.sp) }
                )
            }
        }
    }
}

/**
 * Resolves a refusal notice in the current language, at composition time — never earlier. An
 * argument that is itself a notice (the frame-cap refusal nests its detail) resolves
 * recursively; device-text arguments (counts, shell words) pass through verbatim.
 */
@Composable
private fun resolveNotice(notice: GamingModeNotice): String = when (notice) {
    is GamingModeNotice.Raw -> notice.text
    is GamingModeNotice.Res -> stringResource(
        notice.resId,
        *notice.args.map { if (it is GamingModeNotice) resolveNotice(it) else it }.toTypedArray()
    )
}

/** Small tinted panel used for an activation error or the list of refused optimizations. */
@Composable
private fun NoticeBlock(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text = text, color = tint, fontSize = 12.sp, lineHeight = 18.sp)
    }
}
