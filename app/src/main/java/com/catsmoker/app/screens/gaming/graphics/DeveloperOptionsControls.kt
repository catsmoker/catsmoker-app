package com.catsmoker.app.screens.gaming.graphics

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
import com.catsmoker.app.screens.gaming.components.AppPickerDialog
import com.catsmoker.app.screens.gaming.components.InfoTopics
import com.catsmoker.app.screens.gaming.components.RequirementNotice
import com.catsmoker.app.screens.gaming.graphics.AngleDriverOptions
import com.catsmoker.app.screens.gaming.graphics.AnimationScaleKind
import com.catsmoker.app.screens.gaming.graphics.GameDeveloperOptions
import com.catsmoker.app.ui.components.CatsmokerOutlinedButton
import com.catsmoker.app.ui.components.ChipFlowRow
import com.catsmoker.app.ui.components.InfoButton
import com.catsmoker.app.ui.components.InfoTopic
import java.io.File

/**
 * Every setting that lives in Android's Developer Options, gathered into one card.
 *
 * These used to be three separate cards in two different sections — "Custom Animator" under
 * performance, "Discard Activities" and "Process Limit" under advanced tools, and a
 * "Developer Options: Games" card in between — which presented one screen's worth of platform
 * switches as unrelated features.
 *
 * Each row shows the state the device reported, the reason a control is unavailable when it is, and
 * what the switch actually does — the mechanism, not a marketing line. A [GameDeveloperOptions
 * .ToggleState] with a null `enabled` is a setting the device would not answer for; it renders as
 * unknown and its switch cannot be moved, because moving it would be guessing.
 */
@Composable
fun DeveloperOptionsContent(
    animationScales: Triple<Float, Float, Float>,
    canWriteGlobalSettings: Boolean,
    alwaysFinishActivities: Boolean,
    backgroundProcessLimit: Boolean,
    hasPrivilege: Boolean,
    gameDevOptions: GameDeveloperOptions.State,
    onSetAnimationScale: (AnimationScaleKind, Float) -> Unit,
    onToggleAlwaysFinish: (Boolean) -> Unit,
    onToggleBackgroundLimit: (Boolean) -> Unit,
    onSetForcePeakRefreshRate: (Boolean) -> Unit,
    onSetGameDefaultFrameRateDisabled: (Boolean) -> Unit,
    /** Games the ANGLE picker lists; the card picks the first once loaded. */
    games: List<GameInfo>,
    /** Package the ANGLE card acts on, or null before selection. */
    anglePkg: String?,
    /** Driver the table currently holds for [anglePkg], or null when it holds none. */
    angleDriver: AngleDriverOptions.Driver?,
    /** True while an ANGLE write is in flight. */
    isChangingAngleDriver: Boolean,
    onSelectAnglePackage: (String?) -> Unit,
    onSetAngleDriver: (AngleDriverOptions.Driver) -> Unit,
    /**
     * Opens Android's own Developer options screen.
     *
     * The route launches this with a result callback that re-reads every setting on this card, so a
     * change the user makes over there shows up here when they come back.
     */
    onOpenDeveloperOptions: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // --- Animation scales ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.gt_dev_anim),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            InfoButton(InfoTopics.animationScales)
        }
        if (!canWriteGlobalSettings) {
            RequirementNotice(
                stringResource(R.string.gt_dev_anim_need)
            )
        }
        AnimationScaleRow(stringResource(R.string.gt_dev_anim_windows), AnimationScaleKind.WINDOW, animationScales.first, canWriteGlobalSettings, onSetAnimationScale)
        AnimationScaleRow(stringResource(R.string.gt_dev_anim_transition), AnimationScaleKind.TRANSITION, animationScales.second, canWriteGlobalSettings, onSetAnimationScale)
        AnimationScaleRow(stringResource(R.string.gt_dev_anim_animator), AnimationScaleKind.ANIMATOR, animationScales.third, canWriteGlobalSettings, onSetAnimationScale)

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // --- Process limit and discard activities ---
        DevSwitchRow(
            label = stringResource(R.string.gt_dev_keep_title),
            checked = backgroundProcessLimit,
            enabled = hasPrivilege,
            onCheckedChange = onToggleBackgroundLimit,
            info = InfoTopics.keepFewerApps
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        DevSwitchRow(
            label = stringResource(R.string.gt_dev_close_title),
            checked = alwaysFinishActivities,
            enabled = hasPrivilege,
            onCheckedChange = onToggleAlwaysFinish,
            info = InfoTopics.closeOnLeave
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        DevOptionSwitchRow(
            label = stringResource(R.string.gt_dev_peak),
            state = gameDevOptions.forcePeakRefreshRate,
            onCheckedChange = onSetForcePeakRefreshRate,
            onOpenDeveloperOptions = onOpenDeveloperOptions,
            info = InfoTopics.peakRefresh
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        DevOptionSwitchRow(
            label = stringResource(R.string.gt_dev_game_speed),
            state = gameDevOptions.gameDefaultFrameRateDisabled,
            onCheckedChange = onSetGameDefaultFrameRateDisabled,
            onOpenDeveloperOptions = onOpenDeveloperOptions,
            info = InfoTopics.gameDefaultFrameRate
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        AngleDriverSection(
            games = games,
            anglePkg = anglePkg,
            angleDriver = angleDriver,
            isChanging = isChangingAngleDriver,
            canChange = hasPrivilege,
            onSelectPackage = onSelectAnglePackage,
            onSetDriver = onSetAngleDriver
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Always offered, not only when a switch has failed: several of these live on Android's own
        // Developer options screen, and a user who wants to check or change one directly should not
        // have to hunt for it.
        CatsmokerOutlinedButton(onClick = onOpenDeveloperOptions, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.gt_dev_open_developer))
        }
    }
}

/**
 * Per-game graphics driver choice, on the same table Developer Options edits.
 *
 * The game picker reuses the library's app dialog; the driver chips write through
 * [AngleDriverOptions] with a read-back, so a refused write leaves the chips where they were.
 * The current choice reads through the provider and needs no privilege, which is why the
 * picker stays live while only the chips lock without root/Shizuku.
 */
@Composable
internal fun AngleDriverSection(
    games: List<GameInfo>,
    anglePkg: String?,
    angleDriver: AngleDriverOptions.Driver?,
    isChanging: Boolean,
    canChange: Boolean,
    onSelectPackage: (String?) -> Unit,
    onSetDriver: (AngleDriverOptions.Driver) -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    val pickedName = games.firstOrNull { it.packageName == anglePkg }?.appName

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.gt_angle_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            InfoButton(InfoTopics.angleDriver)
        }
        if (games.isEmpty()) {
            RequirementNotice(stringResource(R.string.gt_no_games_detected))
        } else {
            CatsmokerOutlinedButton(
                onClick = { picking = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isChanging
            ) {
                Text(pickedName ?: stringResource(R.string.gt_angle_pick))
            }
            // Three driver chips wrap instead of squeezing on a narrow phone.
            ChipFlowRow {
                AngleDriverOptions.Driver.entries.forEach { driver ->
                    FilterChip(
                        selected = angleDriver == driver,
                        onClick = { onSetDriver(driver) },
                        enabled = canChange && !isChanging && anglePkg != null,
                        label = {
                            Text(
                                when (driver) {
                                    AngleDriverOptions.Driver.ANGLE -> stringResource(R.string.gt_angle_angle)
                                    AngleDriverOptions.Driver.NATIVE -> stringResource(R.string.gt_angle_native)
                                    AngleDriverOptions.Driver.DEFAULT -> stringResource(R.string.gt_angle_default)
                                },
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }
            // The table's own answer for the picked package — a missing entry is "no choice
            // stored", never a failure, and a 0 here would be a value the device never gave.
            Text(
                text = angleDriver?.let {
                    stringResource(
                        R.string.gt_angle_now,
                        when (it) {
                            AngleDriverOptions.Driver.ANGLE -> stringResource(R.string.gt_angle_angle)
                            AngleDriverOptions.Driver.NATIVE -> stringResource(R.string.gt_angle_native)
                            AngleDriverOptions.Driver.DEFAULT -> stringResource(R.string.gt_angle_default)
                        }
                    )
                } ?: stringResource(R.string.gt_angle_none),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!canChange) {
                RequirementNotice(stringResource(R.string.gt_angle_need))
            }
        }
    }

    if (picking) {
        AppPickerDialog(
            apps = games,
            onDismiss = { picking = false },
            onAppSelected = {
                onSelectPackage(it)
                picking = false
            }
        )
    }
}

/**
 * A plain switch for a setting this app writes and reads back itself, with its explanation tucked away.
 *
 * Distinct from [DevOptionSwitchRow], which renders a [GameDeveloperOptions.ToggleState] and therefore
 * has an availability and an unknown state to show. These two settings are simple booleans held in
 * `Settings.Global`, so there is nothing extra to report beyond whether a write channel exists.
 */
@Composable
internal fun DevSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    info: InfoTopic,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (checked) stringResource(R.string.gt_dev_on) else stringResource(R.string.gt_dev_off),
                    fontSize = 11.sp,
                    color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            InfoButton(info)
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        if (!enabled) {
            RequirementNotice(stringResource(R.string.gt_needs_root_shizuku_change))
        }
    }
}

/**
 * One Developer Options switch, rendering the device's answer rather than a local guess.
 *
 * The switch is disabled when the setting is unavailable or its state is unknown, and the reason is
 * printed underneath — an unavailable setting is reported, never silently accepted. It also never moves
 * on tap: the ViewModel only publishes a new state after the write was read back, so a rejected write
 * leaves the switch exactly where it was.
 *
 * When [GameDeveloperOptions.ToggleState.openDeveloperOptions] is set, the setting is one Android will
 * not let this app write but the user can set themselves, so the row offers the way there instead of
 * only refusing.
 */
@Composable
internal fun DevOptionSwitchRow(
    label: String,
    state: GameDeveloperOptions.ToggleState,
    info: InfoTopic,
    onCheckedChange: (Boolean) -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    val switchEnabled = state.available && state.enabled != null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (switchEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when {
                        state.enabled == null && !state.available -> stringResource(R.string.gt_dev_na)
                        state.enabled == null -> stringResource(R.string.gt_dev_unknown)
                        state.enabled == true -> stringResource(R.string.gt_dev_on)
                        else -> stringResource(R.string.gt_dev_off)
                    },
                    fontSize = 11.sp,
                    color = when {
                        state.enabled == null -> Color(0xFFFFB74D)
                        state.enabled == true -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            InfoButton(info)
            Switch(
                checked = state.enabled == true,
                onCheckedChange = onCheckedChange,
                // A switch that refuses to move is better than one that moves and changes nothing.
                enabled = switchEnabled
            )
        }
        state.detail?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.unavailableReason?.let {
            Text(it, fontSize = 11.sp, color = Color(0xFFFFB74D), lineHeight = 15.sp)
        }
        if (state.openDeveloperOptions) {
            CatsmokerOutlinedButton(onClick = onOpenDeveloperOptions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.gt_dev_open_android))
            }
        }
        if (state.available && state.enabled == null) {
            Text(
                stringResource(R.string.gt_dev_unknown_long),
                fontSize = 11.sp, color = Color(0xFFFFB74D)
            )
        }
    }
}

