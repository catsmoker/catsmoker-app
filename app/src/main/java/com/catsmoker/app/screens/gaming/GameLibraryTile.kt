package com.catsmoker.app.screens.gaming

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.catsmoker.app.R

/**
 * One game in "Your Games": a launcher-style icon tile whose popup carries the three actions.
 *
 * Tap only restores editor inputs; long press opens actions without changing selection.
 * Accessibility services expose the same actions without requiring the gesture.
 * Every action is addressed by package, so games sharing a label cannot act on each other.
 */
@Composable
fun GameLibraryTile(
    game: GameInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    isMenuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onLaunch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onOptimize: (String) -> Unit,
    isOptimizing: Boolean,
    modifier: Modifier = Modifier
) {
    // Cached per game: toBitmap() allocates on every recomposition otherwise.
    val icon = remember(game.packageName, game.icon) { game.icon.toBitmap().asImageBitmap() }
    val launchLabel = stringResource(R.string.gt_library_launch)
    val optimizeLabel = stringResource(R.string.gt_library_optimize)
    val removeLabel = stringResource(R.string.gt_library_remove)
    // Popup and accessibility actions share callbacks, including dismissal and package targeting.
    val launchAction = {
        onDismissMenu()
        onLaunch(game.packageName)
    }
    val optimizeAction = {
        onDismissMenu()
        onOptimize(game.packageName)
    }
    val removeAction = {
        onDismissMenu()
        onRemove(game.packageName)
    }
    Column(
        modifier = modifier
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(
                    if (isSelected) R.string.gt_library_deselect else R.string.gt_library_select, game.appName),
                onClick = onSelect,
                onLongClickLabel = stringResource(R.string.gt_library_actions, game.appName),
                onLongClick = onOpenMenu,
                hapticFeedbackEnabled = true
            )
            .semantics(mergeDescendants = true) {
                selected = isSelected
                customActions = buildList {
                    add(CustomAccessibilityAction(launchLabel) { launchAction(); true })
                    if (!isOptimizing) {
                        add(CustomAccessibilityAction(optimizeLabel) { optimizeAction(); true })
                    }
                    add(CustomAccessibilityAction(removeLabel) { removeAction(); true })
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(56.dp)
            .border(if (isSelected) 2.dp else 0.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
            )
        }
        Box {
            Text(
                text = game.appName,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.gt_library_launch)) },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = launchAction
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
                    onClick = optimizeAction
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
                    onClick = removeAction
                )
            }
        }
    }
}
