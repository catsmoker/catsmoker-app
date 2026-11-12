package com.catsmoker.app.screens.gaming.crosshair

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
import com.catsmoker.app.screens.gaming.crosshair.CrosshairAssets
import com.catsmoker.app.ui.components.CatsmokerButton
import com.catsmoker.app.ui.components.CatsmokerOutlinedButton
import java.io.File

/**
 * Style picker plus the reposition controls.
 *
 * "Move" is a mode with a visible on state rather than a fire-and-forget button, because while it is
 * on the overlay is taking the taps that would otherwise reach the game. The copy says so, and the
 * same control turns it off — the overlay's own banner and its notification are the other two exits.
 */
@Composable
fun CrosshairPicker(
    selected: String,
    onOpenLibrary: () -> Unit,
    isRunning: Boolean,
    isMoveMode: Boolean,
    isOffCentre: Boolean,
    onSetMoveMode: (Boolean) -> Unit,
    onRecentre: () -> Unit
) {
    val context = LocalContext.current
    val normalizedSelected = remember(selected) { CrosshairAssets.normalize(selected) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.gt_crosshair_style), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onOpenLibrary) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.core_desc_add))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                val bitmap = remember(normalizedSelected) {
                    if (CrosshairAssets.isDownloadedAsset(normalizedSelected)) {
                        BitmapFactory.decodeFile(File(context.filesDir, "crosshairs/$normalizedSelected").absolutePath)
                    } else {
                        context.assets.open("crosshair/${CrosshairAssets.DEFAULT_ASSET}").use {
                            BitmapFactory.decodeStream(it)
                        }
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(R.string.gt_crosshair_position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            when {
                !isRunning -> stringResource(R.string.gt_crosshair_off)
                isMoveMode -> stringResource(R.string.gt_crosshair_move)
                isOffCentre -> stringResource(R.string.gt_crosshair_recenter_hint)
                else -> stringResource(R.string.gt_crosshair_centred)
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
                    Text(stringResource(R.string.gt_crosshair_done))
                }
            } else {
                CatsmokerOutlinedButton(onClick = { onSetMoveMode(true) }, enabled = isRunning, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.OpenWith, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.gt_crosshair_move_btn))
                }
            }
            // Offered whenever the crosshair is off centre, including while the overlay is off — the
            // stored offset outlives the overlay and would be reused at the next start.
            CatsmokerOutlinedButton(onClick = onRecentre, enabled = isOffCentre, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FilterCenterFocus, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.gt_crosshair_centre))
            }
        }
    }
}

