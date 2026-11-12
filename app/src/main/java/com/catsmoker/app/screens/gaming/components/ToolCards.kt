package com.catsmoker.app.screens.gaming.components

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
import com.catsmoker.app.screens.gaming.components.RequirementNotice
import com.catsmoker.app.ui.components.InfoButton
import com.catsmoker.app.ui.components.InfoTopic
import com.catsmoker.app.ui.components.SectionCard
import java.io.File

@Composable
fun FeatureToggleCard(title: String, subtitle: String, icon: ImageVector, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    SectionCard(enabled = enabled) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (enabled) MaterialTheme.colorScheme.surfaceVariant else Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

/**
 * A tool card whose body opens on tap.
 *
 * @param enabled false when the feature cannot run here at all. The card greys out and will not open,
 *   because opening it would show controls that could only fail.
 * @param requirementNote what to say when [enabled] is false. A disabled card that gives no reason is
 *   just a dead card, and its body — where the explanation would normally live — cannot be opened, so
 *   the reason has to sit on the outside of it.
 * @param info the feature's questions. The icon sits beside the title, outside the expand target, so
 *   reading the explanation never opens or closes the card, and stays available even when the card is
 *   disabled.
 * @param notice content shown under the row whether the card is open or closed, for a card whose only
 *   body would be an actionable warning (Auto Force Stop's missing Usage access grant).
 * @param expandable false for a card with no body of its own: the row is then just its switch and the
 *   title no longer pretends to open anything.
 */
@Composable
fun ExpandableToolCard(title: String, subtitle: String, icon: ImageVector, isToggleable: Boolean = false, isToggled: Boolean = false, enabled: Boolean = true, requirementNote: String? = null, onToggleChange: (Boolean) -> Unit = {}, forceExpand: Boolean = false, info: InfoTopic? = null, notice: (@Composable () -> Unit)? = null, expandable: Boolean = true, content: @Composable () -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(forceExpand) { if (forceExpand) expanded = true }
    val opens = enabled && expandable
    SectionCard(enabled = enabled) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (enabled) MaterialTheme.colorScheme.surfaceVariant else Color.Gray.copy(alpha = 0.1f)).clickable(enabled = opens) { expanded = !expanded }, contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f).clickable(enabled = opens) { expanded = !expanded }) {
                    Text(title, fontWeight = FontWeight.Bold, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                if (info != null) {
                    InfoButton(topic = info)
                }
                if (isToggleable) {
                    Switch(checked = isToggled, onCheckedChange = onToggleChange, enabled = enabled)
                } else if (expandable) {
                    IconButton(onClick = { expanded = !expanded }, enabled = enabled) { Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (!enabled && requirementNote != null) {
                Spacer(modifier = Modifier.height(12.dp))
                RequirementNotice(requirementNote)
            }
            if (notice != null) {
                Spacer(modifier = Modifier.height(12.dp))
                notice()
            }
            if (expandable && expanded && enabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }
}

