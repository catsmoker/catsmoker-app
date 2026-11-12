package com.catsmoker.app.screens.gaming

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
import java.io.File

/**
 * One game in "Your Games": a launcher-style icon tile whose popup carries the three actions.
 *
 * The icon itself is the button and the name underneath is a plain label, so the tap target is
 * the familiar launcher icon rather than a hidden chevron. The 56 dp [IconButton] keeps the touch
 * target above the 48 dp rule while the artwork inside stays 48 dp. Only one menu is open at a
 * time — the screen hoists [isMenuOpen] — and every action is addressed by [GameInfo.packageName]
 * so two games sharing a label can never act on each other.
 */
@Composable
fun GameLibraryTile(
    game: GameInfo,
    isMenuOpen: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onLaunch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onOptimize: (String) -> Unit,
    isOptimizing: Boolean,
    modifier: Modifier = Modifier
) {
    // Cached per game: toBitmap() allocates on every recomposition otherwise.
    val icon = remember(game.packageName, game.icon) { game.icon.toBitmap().asImageBitmap() }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            IconButton(onClick = onToggleMenu, modifier = Modifier.size(56.dp)) {
                Image(
                    bitmap = icon,
                    // The icon is the only control on the tile, so it carries the game name: a
                    // screen reader announces "Actions for <game>", not an unnamed button.
                    contentDescription = stringResource(R.string.gt_library_actions, game.appName),
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                )
            }
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.gt_library_launch)) },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = {
                        onDismissMenu()
                        onLaunch(game.packageName)
                    }
                )
                // Pre-launch speed-profile compile for exactly this game. Runs through the same
                // sweep lock as the full booster, so it can never race one — the item spins while
                // the platform works and the toast reports what it said.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.gt_library_optimize)) },
                    leadingIcon = {
                        if (isOptimizing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Build, contentDescription = null)
                        }
                    },
                    enabled = !isOptimizing,
                    onClick = {
                        onDismissMenu()
                        onOptimize(game.packageName)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.gt_library_remove),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        onDismissMenu()
                        onRemove(game.packageName)
                    }
                )
            }
        }
        Text(
            text = game.appName,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 2.dp, end = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            // Two lines always: tiles in the same row stay the same height, and a long name
            // ellipsizes instead of pushing the row taller than its neighbours.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
