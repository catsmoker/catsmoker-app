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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.app.R
import com.catsmoker.app.features.gamingtools.engine.GamingModeNotice
import com.catsmoker.app.features.gamingtools.engine.GamingModeRecovery
import com.catsmoker.app.features.gamingtools.engine.GamingModeReport
import com.catsmoker.app.features.gamingtools.engine.GamingModeRestoreResult
import com.catsmoker.app.features.gamingtools.engine.GamingModeState
import com.catsmoker.app.shared.data.model.GameInfo
import com.catsmoker.app.shared.ui.components.CatsmokerButton
import com.catsmoker.app.shared.ui.components.InfoButton
import com.catsmoker.app.shared.ui.components.SectionCard

/**
 * The Gaming Mode card: activation, automatic mode, the keep list and the read-back report.
 *
 * The card is deliberately one surface rather than four. Automatic Gaming Mode arms the same engine
 * as the power button, the keep list is the exception the same engine's suspend sweep honors, and
 * the result rows are that engine's read-back. Splitting them into separate cards made three parts
 * of one lifecycle look like unrelated features.
 *
 * @param canActivate whether root or Shizuku is available. Every optimization Gaming Mode applies is a
 *   privileged command, so without one of those channels the whole feature can do nothing at all. The
 *   power button is disabled and the reason is stated on the card rather than letting the user press a
 *   live button and watch it fail. It goes live on its own as soon as either channel appears, because
 *   the state comes from the privilege flow the screen re-reads on every sync.
 * @param keptPackages packages the suspend sweep must leave running. This is a list, not a switch:
 *   the store keeps a set and the next activation reads it.
 * @param keepCandidateApps the picker's name source for [keptPackages]. A package missing from it
 *   still renders as its package name.
 * @param onAddKeptApp opens the app picker. The picker dialog stays screen-level because it needs
 *   the full app list.
 * @param onRemoveKeptApp removes one pick. It only edits this app's own list, so it needs no
 *   privilege.
 */
@Composable
fun GamingModeCard(
    gamingState: GamingModeState,
    report: GamingModeReport,
    canActivate: Boolean,
    isActive: Boolean,
    isBusy: Boolean,
    /** Whether a session record is still open, so the power button must revert rather than start. */
    isSessionOpen: Boolean,
    /** Leftovers from a previous session that are waiting to be restored. */
    recovery: GamingModeRecovery,
    isRestoringApps: Boolean,
    restoreResult: GamingModeRestoreResult?,
    onRestoreApps: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    // Automatic Gaming Mode lives inside this card: it arms the same engine, so a separate
    // row above the library made the relationship look like two unrelated features.
    sessionActive: Boolean,
    sessionEnabled: Boolean,
    onToggleSession: (Boolean) -> Unit,
    // The keep list is the suspend sweep's user exception list. It used to sit under Background
    // Apps, away from the switch that decides whether the sweep runs at all.
    keptPackages: Set<String>,
    keepCandidateApps: List<GameInfo>,
    onAddKeptApp: () -> Unit,
    onRemoveKeptApp: (String) -> Unit,
    // The extra freeze list is the same sweep's second input, so it lives here too rather than
    // under Background Apps where it looked like an unrelated feature.
    extraSuspendPackages: Set<String>,
    suspendedPackages: Set<String>,
    suspendCandidateApps: List<GameInfo>,
    onAddExtraSuspend: () -> Unit,
    onRemoveExtraSuspend: (String) -> Unit
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
                        color = when {
                            isActive -> MaterialTheme.colorScheme.primary
                            isSessionOpen -> Color(0xFFFFB300)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
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
                            // Reconciliation is reading the persisted record and the device; saying
                            // "ready" here would invite a second session before the first is closed.
                            is GamingModeState.Checking -> stringResource(R.string.gt_gm_checking)
                            is GamingModeState.Interrupted -> stringResource(R.string.gt_gm_interrupted)
                            is GamingModeState.Error -> stringResource(R.string.gt_gm_failed)
                            is GamingModeState.Idle ->
                                if (canActivate) stringResource(R.string.gt_gm_ready) else stringResource(R.string.gt_gm_locked)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = when {
                            gamingState is GamingModeState.Error -> MaterialTheme.colorScheme.error
                            gamingState is GamingModeState.Interrupted -> Color(0xFFFFB300)
                            // Item 8: a feature that cannot run must look like it cannot run.
                            !canActivate -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                InfoButton(InfoTopics.gamingMode)

                IconButton(
                    onClick = { if (isSessionOpen) onDeactivate() else onActivate() },
                    enabled = !isBusy && canActivate,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                isSessionOpen -> Color(0xFFFFB300).copy(alpha = 0.16f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                isSessionOpen -> Color(0xFFFFB300).copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            },
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = stringResource(
                            if (isSessionOpen) R.string.gt_gm_toggle_off else R.string.gt_gm_toggle_on
                        ),
                        tint = when {
                            isActive -> MaterialTheme.colorScheme.primary
                            isSessionOpen -> Color(0xFFFFB300)
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
            }

            // The progress indicator belongs to the action above it. It is composed only while the
            // engine is actually enabling or disabling, so an idle card has no decorative line and
            // no reserved empty space for one.
            if (GamingModeProgress.isVisible(gamingState)) {
                Spacer(modifier = Modifier.height(16.dp))
                GamingModeProgressBar(state = gamingState)
            }

            // Activation failures stay outside every disclosure. A collapsed details section can
            // never hide the reason the operation failed.
            if (gamingState is GamingModeState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                NoticeBlock(text = gamingState.message, tint = MaterialTheme.colorScheme.error)
                if (isSessionOpen) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.gt_gm_error_session_open),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Color(0xFFFFB300)
                    )
                }
            }

            // Recovery is a second, narrower action than the power button: after a reboot the
            // settings are already at stock, but the package manager keeps suspensions. Making the
            // user run a full revert just to wake apps would write an old snapshot over settings
            // the system has since changed. Shown only while no session is running — an active
            // session's own suspends must never be undone from under it.
            if (!isActive && (gamingState is GamingModeState.Interrupted || recovery.isNeeded)) {
                Spacer(modifier = Modifier.height(16.dp))
                val tint = Color(0xFFFFB300)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.10f))
                        .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.gt_gm_recovery_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = tint
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (recovery.isNeeded) {
                            stringResource(R.string.gt_gm_recovery_body, recovery.pendingCount)
                        } else {
                            // The app record is clear but the settings snapshot is not: the power
                            // button is the only action that can close this session, so say so.
                            stringResource(R.string.gt_gm_revert_needed)
                        },
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                    if (recovery.isNeeded && !recovery.verified) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.gt_gm_recovery_unverified),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (recovery.isNeeded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CatsmokerButton(
                            onClick = onRestoreApps,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isRestoringApps && !isBusy && !isActive
                        ) {
                            if (isRestoringApps) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.gt_gm_restore))
                            }
                        }
                    }
                    val result = restoreResult
                    if (result != null && !result.refusedWhileActive && !isRestoringApps) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val complete = result.isComplete
                        Text(
                            text = restoreSummary(result),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (complete) Color(0xFF4CAF50) else tint
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.gt_session_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (sessionEnabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            if (sessionEnabled) R.string.gt_session_sub
                            else R.string.gt_session_need_games
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = sessionActive,
                    onCheckedChange = onToggleSession,
                    enabled = sessionEnabled
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(12.dp))
            GamingModeKeepRunningSection(
                picked = keptPackages,
                apps = keepCandidateApps,
                canEdit = canActivate,
                onAdd = onAddKeptApp,
                onRemove = onRemoveKeptApp
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(12.dp))
            GamingModeSuspensionSection(
                picked = extraSuspendPackages,
                apps = suspendCandidateApps,
                // Only this session's confirmed suspends count as frozen. An app the device froze
                // through another tool is not claimed by this app's badge.
                suspended = suspendedPackages,
                canEdit = canActivate,
                onAdd = onAddExtraSuspend,
                onRemove = onRemoveExtraSuspend
            )

            if (isActive) {
                // Refused optimizations stay visible above the folded list: the summary can say
                // how many landed, but the reasons must be readable without opening anything.
                if (report.unavailable.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val reasons = report.unavailable.map { resolveNotice(it) }
                    NoticeBlock(
                        text = stringResource(R.string.gt_gm_unavailable_title) + "\n" +
                            reasons.joinToString("\n") { "• $it" },
                        tint = Color(0xFFFFB300)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                // Collapsed by default. Opening it is review only: the section receives the report
                // and has no activation callback, so expanding or collapsing can never run one.
                GamingModeDetailsSection(report = report)
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

/**
 * One line for a Restore Apps attempt, in counts rather than a bare success flag.
 *
 * Without package-manager evidence the counts are what this app recorded, not what the device
 * confirmed, so the unverified wording is used instead of claiming a partial failure.
 */
@Composable
private fun restoreSummary(result: GamingModeRestoreResult): String {
    val outstanding = result.stillSuspended + result.failedAppOps
    return when {
        !result.verified -> stringResource(R.string.gt_gm_restore_unverified, outstanding)
        outstanding > 0 -> stringResource(
            R.string.gt_gm_restore_partial,
            result.restoredPackages,
            result.restoredAppOps,
            outstanding
        )
        else -> stringResource(
            R.string.gt_gm_restore_done,
            result.restoredPackages,
            result.restoredAppOps
        )
    }
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
