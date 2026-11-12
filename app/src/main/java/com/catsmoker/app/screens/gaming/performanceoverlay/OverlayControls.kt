package com.catsmoker.app.screens.gaming.performanceoverlay

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
import com.catsmoker.app.ui.components.CatsmokerButton
import com.catsmoker.app.ui.components.CatsmokerOutlinedButton
import java.io.File

/**
 * The performance readout's position controls.
 *
 * The readout only becomes draggable in move mode: outside it the window is FLAG_NOT_TOUCHABLE so
 * taps in its area still reach the game. Move mode is therefore a state the UI has to announce,
 * not a hidden toggle — the lines below say which mode is on and the buttons carry the way out.
 */
@Composable
fun OverlayPositionContent(
    isRunning: Boolean,
    isMoveMode: Boolean,
    isMoved: Boolean,
    onSetMoveMode: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    Column {
        Text(stringResource(R.string.gt_overlay_position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            when {
                !isRunning -> stringResource(R.string.gt_overlay_off)
                isMoveMode -> stringResource(R.string.gt_overlay_move)
                isMoved -> stringResource(R.string.gt_overlay_moved_hint)
                else -> stringResource(R.string.gt_overlay_default_hint)
            },
            color = if (isMoveMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The label names the state it will move to, not the state it is in, so the button never
            // reads as a description of the current mode.
            if (isMoveMode) {
                CatsmokerButton(onClick = { onSetMoveMode(false) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.gt_overlay_done))
                }
            } else {
                CatsmokerOutlinedButton(onClick = { onSetMoveMode(true) }, enabled = isRunning, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.OpenWith, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.gt_overlay_move_btn))
                }
            }
            // Offered whenever the readout sits off the default corner, including while the overlay
            // is off — the stored position outlives the overlay and would be reused at the next start.
            CatsmokerOutlinedButton(onClick = onReset, enabled = isMoved, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FilterCenterFocus, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.gt_overlay_reset))
            }
        }
    }
}

