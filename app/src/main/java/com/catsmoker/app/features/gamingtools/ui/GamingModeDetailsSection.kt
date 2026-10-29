package com.catsmoker.app.features.gamingtools.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.app.R
import com.catsmoker.app.features.gamingtools.engine.GamingModeReport

/**
 * One resolved line of the Gaming Mode result list.
 *
 * Labels and values are already resolved in the current language by the caller, so a language
 * switch recomposes the whole list instead of leaving a stale translation behind (the same rule
 * [com.catsmoker.app.features.gamingtools.engine.GamingModeNotice] follows for refusals).
 */
data class GamingModeResultLine(
    val label: String,
    val value: String,
    val applied: Boolean,
    val reason: String? = null
)

/** How many of the visible result lines the device confirmed. */
data class GamingModeDetailsSummary(
    val confirmed: Int,
    val total: Int
)

/**
 * The result list's fold rule and summary, kept pure so both can be tested without a device.
 *
 * The section starts collapsed on purpose: after a successful activation the card already says
 * "Active", and the twenty-odd read-back rows were the tallest thing on the screen. Folding
 * them never changes the report — the list is rendered from [GamingModeReport] every time the
 * section is composed.
 */
object GamingModeDetails {
    /** Collapsed by default; opening it is a review action, not part of an activation. */
    const val DEFAULT_EXPANDED = false

    /** Flips only the disclosure state. Nothing here can start, stop or repeat an optimization. */
    fun toggle(expanded: Boolean): Boolean = !expanded

    fun summarize(rows: List<GamingModeResultLine>): GamingModeDetailsSummary =
        GamingModeDetailsSummary(confirmed = rows.count { it.applied }, total = rows.size)
}

/**
 * The Gaming Mode read-back list, folded behind a compact disclosure.
 *
 * Every line is a value the engine read back from the device after writing it, so a change the ROM
 * refused still reads as refused. Errors and refusals are deliberately not rendered here: they stay
 * on the card above this section so a failed activation can never hide behind a collapsed row.
 */
@Composable
fun GamingModeDetailsSection(
    report: GamingModeReport,
    modifier: Modifier = Modifier
) {
    val rows = gamingModeResultLines(report)
    val summary = GamingModeDetails.summarize(rows)
    var expanded by rememberSaveable { mutableStateOf(GamingModeDetails.DEFAULT_EXPANDED) }
    val accent = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.08f))
                .clickable { expanded = GamingModeDetails.toggle(expanded) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.gt_gm_details_hide else R.string.gt_gm_details_show
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = if (summary.total == 0) {
                        stringResource(R.string.gt_gm_details_empty)
                    } else {
                        stringResource(
                            R.string.gt_gm_details_summary,
                            summary.confirmed,
                            summary.total
                        )
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rows.forEach { row ->
                    GamingModeResultRow(
                        label = row.label,
                        value = row.value,
                        applied = row.applied,
                        reason = row.reason
                    )
                }
            }
        }
    }
}

/**
 * Builds the same read-back rows the card always showed, in the current language.
 *
 * Nullable report fields keep their existing meaning: null means "not applicable on this device",
 * which stays visible as a greyed row with the reason instead of disappearing. The summary counts
 * only [GamingModeResultLine.applied], so it can never claim more than the rows below it.
 */
@Composable
private fun gamingModeResultLines(report: GamingModeReport): List<GamingModeResultLine> = buildList {
    add(
        GamingModeResultLine(
            label = "PowerHAL",
            value = if (report.fixedPerformance) stringResource(R.string.gt_gm_fixed) else stringResource(R.string.gt_gm_not_applied),
            applied = report.fixedPerformance
        )
    )
    // Null means the device carries no vendor GPU mode property at all — every non-Qualcomm SoC —
    // so the row stays visible but grayed out with the reason, instead of disappearing entirely.
    if (report.gpuPerformanceMode != null) {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_gpu),
                value = if (report.gpuPerformanceMode) stringResource(R.string.gt_gm_qc_perf) else stringResource(R.string.gt_gm_not_applied),
                applied = report.gpuPerformanceMode
            )
        )
    } else {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_gpu),
                value = stringResource(R.string.gt_gm_na_value),
                applied = false,
                reason = stringResource(R.string.gt_gm_na_qualcomm)
            )
        )
    }
    // Same null rule — grayed out on non-Qualcomm silicon. The value shown is the measured panel
    // peak the hint carries, read back from the property.
    if (report.qtiGameFps != null) {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_fps_hint),
                value = if (report.qtiGameFps) stringResource(R.string.gt_gm_qc_peak) else stringResource(R.string.gt_gm_not_applied),
                applied = report.qtiGameFps
            )
        )
    } else {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_fps_hint),
                value = stringResource(R.string.gt_gm_na_value),
                applied = false,
                reason = stringResource(R.string.gt_gm_na_qualcomm)
            )
        )
    }
    // Same null rule — grayed out where the GED/PPM nodes do not exist. The row reports the
    // read-back verdict, never the write request.
    if (report.mediaTekGameMode != null) {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_mtk),
                value = if (report.mediaTekGameMode) stringResource(R.string.gt_gm_mtk_on) else stringResource(R.string.gt_gm_not_applied),
                applied = report.mediaTekGameMode
            )
        )
    } else {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_mtk),
                value = stringResource(R.string.gt_gm_na_value),
                applied = false,
                reason = stringResource(R.string.gt_gm_na_mediatek)
            )
        )
    }
    // Same null rule — grayed out where no Snapdragon policy has headroom and no game cpuset is
    // narrow. The row reports the read-back verdict, never the write request.
    if (report.snapdragonCpuMode != null) {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_snap),
                value = if (report.snapdragonCpuMode) stringResource(R.string.gt_gm_snap_on) else stringResource(R.string.gt_gm_not_applied),
                applied = report.snapdragonCpuMode
            )
        )
    } else {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_snap),
                value = stringResource(R.string.gt_gm_na_value),
                applied = false,
                reason = stringResource(R.string.gt_gm_na_snapdragon)
            )
        )
    }
    // The five SmartPack-derived kernel levers. Same nullable contract: a kernel without the
    // interface gets a grayed row with the reason, a refused write reads "Not applied" (with the
    // refusal also in the unavailable list), and an applied lever reports the value the device read
    // back — never the request.
    add(
        GamingModeResultLine(
            label = stringResource(R.string.gt_gm_label_input_boost),
            value = when (report.cpuInputBoost) {
                true -> stringResource(R.string.gt_gm_touch_boost)
                false -> stringResource(R.string.gt_gm_not_applied)
                null -> stringResource(R.string.gt_gm_na_value)
            },
            applied = report.cpuInputBoost == true,
            reason = if (report.cpuInputBoost == null) stringResource(R.string.gt_gm_na_kernel) else null
        )
    )
    add(
        GamingModeResultLine(
            label = stringResource(R.string.gt_gm_label_governor),
            value = when (report.cpuGovernor) {
                true -> stringResource(R.string.gt_gm_governor_perf)
                false -> stringResource(R.string.gt_gm_not_applied)
                null -> stringResource(R.string.gt_gm_na_value)
            },
            applied = report.cpuGovernor == true,
            reason = if (report.cpuGovernor == null) stringResource(R.string.gt_gm_na_kernel) else null
        )
    )
    add(
        GamingModeResultLine(
            label = stringResource(R.string.gt_gm_label_spectrum),
            value = when (report.spectrumProfile) {
                3 -> stringResource(R.string.gt_gm_spectrum_gaming)
                2 -> stringResource(R.string.gt_gm_spectrum_perf)
                else -> stringResource(R.string.gt_gm_na_value)
            },
            applied = report.spectrumProfile != null,
            reason = if (report.spectrumProfile == null) stringResource(R.string.gt_gm_na_kernel) else null
        )
    )
    add(
        GamingModeResultLine(
            label = stringResource(R.string.gt_gm_label_readahead),
            value = when {
                report.storageTuning == true && report.storageReadAheadKb != null ->
                    stringResource(R.string.gt_gm_kb, report.storageReadAheadKb)
                report.storageTuning == false -> stringResource(R.string.gt_gm_not_applied)
                else -> stringResource(R.string.gt_gm_na_value)
            },
            applied = report.storageTuning == true,
            reason = if (report.storageTuning == null) stringResource(R.string.gt_gm_na_kernel) else null
        )
    )
    add(
        GamingModeResultLine(
            label = stringResource(R.string.gt_gm_label_mem),
            value = when {
                report.memoryTuning == true && report.memoryMinFreeKb != null ->
                    stringResource(R.string.gt_gm_kb, report.memoryMinFreeKb)
                report.memoryTuning == false -> stringResource(R.string.gt_gm_not_applied)
                else -> stringResource(R.string.gt_gm_na_value)
            },
            applied = report.memoryTuning == true,
            reason = if (report.memoryTuning == null) stringResource(R.string.gt_gm_na_kernel) else null
        )
    )
    add(
        GamingModeResultLine(
            label = stringResource(R.string.gt_gm_label_display),
            value = report.lockedRefreshHz?.let { stringResource(R.string.gt_gm_locked_hz, it) } ?: stringResource(R.string.gt_gm_rate_unlocked),
            applied = report.lockedRefreshHz != null
        )
    )
    add(
        GamingModeResultLine(
            label = stringResource(R.string.gt_gm_label_touch),
            value = (if (report.touchResponseBoost) stringResource(R.string.gt_gm_touch_boost) else stringResource(R.string.gt_gm_touch_na)) +
                (report.pointerSpeed?.let { " · ${formatPointerSpeed(it)}" } ?: ""),
            applied = report.touchResponseBoost
        )
    )
    add(
        GamingModeResultLine(
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
    )
    add(
        GamingModeResultLine(
            label = stringResource(R.string.gt_gm_label_dnd),
            value = if (report.dndEngaged) stringResource(R.string.gt_gm_dnd_on) else stringResource(R.string.gt_gm_dnd_off),
            applied = report.dndEngaged
        )
    )
    // Null means notification access was never granted, so the second layer was never switchable
    // on this run — grayed out with the reason instead of omitted.
    if (report.notificationSuppression != null) {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_notif),
                value = if (report.notificationSuppression) stringResource(R.string.gt_gm_notif_on) else stringResource(R.string.gt_gm_notif_off),
                applied = report.notificationSuppression
            )
        )
    } else {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_notif),
                value = stringResource(R.string.gt_gm_na_value),
                applied = false,
                reason = stringResource(R.string.gt_gm_na_notif)
            )
        )
    }
    if (report.networkWhitelisted != null) {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_net),
                value = if (report.networkWhitelisted) stringResource(R.string.gt_gm_net_ok) else stringResource(R.string.gt_gm_net_no),
                applied = report.networkWhitelisted
            )
        )
    } else {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_net),
                value = stringResource(R.string.gt_gm_na_value),
                applied = false,
                reason = stringResource(R.string.gt_gm_na_nogame)
            )
        )
    }
    // Null means no game was targeted or the device predates game interventions (Android 12) —
    // not applicable, so the row stays visible but grayed out with the reason, exactly like the
    // chipset rows above.
    if (report.gameInterventionApplied != null) {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_cap),
                value = if (report.gameInterventionApplied) {
                    stringResource(R.string.gt_gm_cap_raised) +
                        (report.gameInterventionDownscale?.let { " · ${formatDownscale(it)}" } ?: "")
                } else {
                    stringResource(R.string.gt_gm_cap_no)
                },
                applied = report.gameInterventionApplied
            )
        )
    } else {
        add(
            GamingModeResultLine(
                label = stringResource(R.string.gt_gm_label_cap),
                value = stringResource(R.string.gt_gm_na_value),
                applied = false,
                reason = stringResource(R.string.gt_gm_na_intervention)
            )
        )
    }
    // Reported from the read-back of always_finish_activities, so "Applied" means the setting holds
    // 1 right now rather than that the command was sent.
    add(
        GamingModeResultLine(
            label = stringResource(R.string.gt_gm_label_discard),
            value = if (report.discardActivities) stringResource(R.string.gt_gm_discard_on) else stringResource(R.string.gt_gm_not_applied),
            applied = report.discardActivities
        )
    )
    add(
        GamingModeResultLine(
            label = stringResource(R.string.gt_gm_label_limit),
            value = if (report.processLimit) stringResource(R.string.gt_gm_limit) else stringResource(R.string.gt_gm_not_applied),
            applied = report.processLimit
        )
    )
    add(
        GamingModeResultLine(
            label = stringResource(R.string.gt_gm_label_other_data),
            value = when (report.backgroundDataRestricted) {
                // null means the user's own switch was already on, so this run left it alone —
                // saying "not applied" would misreport a deliberate decision.
                null -> stringResource(R.string.gt_gm_left_as_set)
                true -> stringResource(R.string.gt_gm_blocked_metered)
                false -> stringResource(R.string.gt_gm_not_blocked)
            },
            applied = report.backgroundDataRestricted != false
        )
    )
}
