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
import com.catsmoker.app.platform.display.DisplayMetricsProvider
import com.catsmoker.app.screens.gaming.GamingToolsViewModel
import com.catsmoker.app.screens.gaming.components.RequirementNotice
import com.catsmoker.app.screens.gaming.mode.DownscaleRow
import com.catsmoker.app.screens.gaming.mode.PointerSpeedRow
import com.catsmoker.app.ui.components.CatsmokerButton
import com.catsmoker.app.ui.components.CatsmokerOutlinedButton
import com.catsmoker.app.ui.components.ChipFlowRow
import com.catsmoker.app.ui.theme.LogTerminalBackground
import com.catsmoker.app.ui.theme.logLineColor
import java.io.File

/**
 * Resolution picker, plus the two next-activation tuning rows that belong with it.
 *
 * Every fixed entry is a scaling of the panel's real resolution, computed by
 * `DisplayMetricsProvider` — the same reader spoof profiles use — so nothing here is a hardcoded
 * size that the display may not support. When the panel could not be read the picker says so
 * instead of offering entries derived from a guess.
 *
 * Render scale lowers the game's *internal* render resolution (`game_overlay`
 * downscaleFactor, per game, at the next Gaming Mode activation) while the override
 * above changes the device display itself — related in purpose, separate channels,
 * so they share this card as clearly labeled subsections rather than one control.
 * Touch speed only changes the pointer speed (`Settings.System.pointer_speed`,
 * same next-activation timing) and is labeled as such; it never touches resolution.
 */
@Composable
fun ResolutionChangerContent(
    native: DisplayMetricsProvider.Snapshot?,
    activeOverride: DisplayMetricsProvider.Snapshot?,
    options: List<GamingToolsViewModel.ResolutionOption>,
    selectedOptionId: String?,
    width: String, height: String, dpi: String,
    validationError: String?,
    isApplying: Boolean,
    isRoot: Boolean,
    isShizuku: Boolean,
    /**
     * Whether the three number fields accept typing.
     *
     * False for every preset: the fields then show what that preset would apply and cannot be edited
     * into something the chip no longer describes. Only the "Custom" chip sets this true. The
     * ViewModel enforces the same rule on its side, so a stray edit cannot slip through the UI.
     */
    editable: Boolean,
    log: List<String>,
    onOptionSelected: (String) -> Unit,
    onWidthChange: (String) -> Unit, onHeightChange: (String) -> Unit, onDpiChange: (String) -> Unit,
    onApply: () -> Unit, onReset: () -> Unit,
    /** Render scale for the next Gaming Mode activation, or null for full resolution. */
    downscale: Float?,
    /** Chooses the render scale; takes effect on the next activation, never mid-session. */
    onDownscaleChange: (Float?) -> Unit,
    /** Touch speed for the next activation, or null for stock (untouched). */
    pointerSpeed: Int?,
    /** Chooses the touch speed; takes effect on the next activation, never mid-session. */
    onPointerSpeedChange: (Int?) -> Unit,
    /**
     * Whether the two tuning rows accept input. Locked while Gaming Mode is active or
     * busy, or without root/Shizuku — the same rule they always had.
     */
    tuningEnabled: Boolean,
) {
    // execResult prefers root and only falls back to Shizuku, so this names the channel that will
    // actually run `wm` rather than offering a choice the app does not honour.
    val channel = when {
        isRoot -> "Root"
        isShizuku -> "Shizuku"
        else -> null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (native != null && native.isValid) {
            Text(stringResource(R.string.gt_res_your_screen, native.label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        } else {
            Text(
                stringResource(R.string.gt_res_unknown_screen),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = activeOverride?.let { stringResource(R.string.gt_res_changed_to, it.label) } ?: stringResource(R.string.gt_res_not_changed),
            fontSize = 11.sp,
            color = if (activeOverride != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (options.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.gt_res_choose_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            // Resolution presets wrap instead of scrolling sideways: every size stays
            // visible on a narrow phone, one line on a tablet.
            ChipFlowRow {
                options.forEach { option ->
                    FilterChip(
                        selected = option.id == selectedOptionId,
                        onClick = { onOptionSelected(option.id) },
                        label = { Text(option.label, fontSize = 11.sp) }
                    )
                }
            }
            // The numbers behind the chosen preset, so nothing is applied unseen.
            options.firstOrNull { it.id == selectedOptionId }?.target?.let { target ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(stringResource(R.string.gt_res_preset_sets, target.label), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // readOnly rather than enabled = false: the numbers stay legible so a preset can be inspected
        // before it is applied, but the keyboard never opens for them.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = width, onValueChange = onWidthChange, label = { Text(stringResource(R.string.gt_res_width)) },
                singleLine = true,
                readOnly = !editable,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = validationError != null,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = height, onValueChange = onHeightChange, label = { Text(stringResource(R.string.gt_res_height)) },
                singleLine = true,
                readOnly = !editable,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = validationError != null,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = dpi, onValueChange = onDpiChange, label = { Text(stringResource(R.string.gt_res_dpi)) },
            singleLine = true,
            readOnly = !editable,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = validationError != null,
            supportingText = validationError?.let { { Text(it, fontSize = 11.sp) } },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (editable) stringResource(R.string.gt_res_editable_hint)
            else stringResource(R.string.gt_res_locked_hint),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (channel == null) {
            RequirementNotice(
                stringResource(R.string.gt_res_need)
            )
        } else {
            Text(
                stringResource(
                    if (channel == "Root") R.string.gt_res_ready_root else R.string.gt_res_ready_shizuku
                ),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatsmokerButton(
                onClick = onApply,
                enabled = channel != null && validationError == null && !isApplying,
                modifier = Modifier.weight(1f)
            ) {
                if (isApplying) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.gt_res_apply))
                }
            }
            CatsmokerOutlinedButton(
                onClick = onReset,
                enabled = channel != null && !isApplying,
                modifier = Modifier.weight(0.6f)
            ) { Text(stringResource(R.string.gt_res_reset)) }
        }
        // Next-activation tuning, kept with the resolution controls it relates to.
        // Render scale lowers the game's internal render resolution (the display
        // override above is untouched); touch speed only changes the pointer speed.
        // Both wait for the next Gaming Mode run and restore afterwards, so the rows
        // lock under the same rule Gaming Mode itself uses.
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))
        DownscaleRow(
            current = downscale,
            enabled = tuningEnabled,
            onSelect = onDownscaleChange
        )
        Spacer(modifier = Modifier.height(16.dp))
        PointerSpeedRow(
            current = pointerSpeed,
            enabled = tuningEnabled,
            onSelect = onPointerSpeedChange
        )
        if (log.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
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
    }
}
