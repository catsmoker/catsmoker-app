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
import com.catsmoker.app.screens.gaming.graphics.AnimationScaleKind
import com.catsmoker.app.ui.components.ChipFlowRow
import java.io.File

/**
 * One animation scale, as a label and three chips on a single line.
 *
 * The values offered are off, 0.5× and 1×. Developer Options itself also offers 1.5×, 2×, 5× and 10×,
 * but every one of those makes the UI slower than stock, which is the opposite of what anyone opens
 * this card for — so they are not listed. They are written straight to
 * `Settings.Global.WINDOW_ANIMATION_SCALE` / `TRANSITION_ANIMATION_SCALE` / `ANIMATOR_DURATION_SCALE`,
 * so a change here is the same change the system screen makes, applied immediately, and the engine
 * verifies each write by reading the setting back — a refused write is reported, not assumed to work.
 */
@Composable
internal fun AnimationScaleRow(
    label: String,
    kind: AnimationScaleKind,
    current: Float,
    canWrite: Boolean,
    onSetScale: (AnimationScaleKind, Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(92.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            // A value another app set that is not one of the three is shown as it is, not snapped onto
            // the nearest chip — the chips would otherwise misreport what the system currently holds.
            if (ANIMATION_SCALE_VALUES.none { kotlin.math.abs(it - current) < 0.005f }) {
                Text(formatAnimationScale(current), fontSize = 10.sp, color = Color(0xFFFFB74D))
            }
        }
        // Three compact chips wrap instead of squeezing next to the fixed label.
        ChipFlowRow {
            ANIMATION_SCALE_VALUES.forEach { value ->
                FilterChip(
                    selected = kotlin.math.abs(current - value) < 0.005f,
                    onClick = { onSetScale(kind, value) },
                    enabled = canWrite,
                    label = { Text(if (value == 0f) stringResource(R.string.gt_dev_anim_off) else formatAnimationScale(value), fontSize = 11.sp) }
                )
            }
        }
    }
}

/**
 * Off, half, and stock.
 *
 * Developer Options' full set runs to 10×, but everything above 1× only makes the device feel slower;
 * offering those from a card whose purpose is to speed the UI up would be offering a pessimisation.
 */
internal val ANIMATION_SCALE_VALUES = listOf(0f, 0.5f, 1f)

internal fun formatAnimationScale(value: Float): String =
    if (value % 1f == 0f) "${value.toInt()}x" else "${value}x"
