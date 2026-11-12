package com.catsmoker.app.screens.gaming.apps

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
import com.catsmoker.app.ui.components.CatsmokerOutlinedButton
import java.io.File

/**
 * The one grant Auto Force Stop cannot work without, reported where the switch is.
 *
 * There is deliberately no app list here: the feature closes every app the user leaves. The
 * service keeps its own code-level protections (home screen, keyboard, this app, system
 * packages), which are not user exceptions and cannot be edited. The root/Shizuku requirement is
 * stated by the card itself; this notice covers the "Usage access" appop, which no permission
 * dialog can ask for.
 */
@Composable
fun AutoForceStopUsageNotice() {
    val context = LocalContext.current
    // The service reads foreground app changes from UsageStatsManager, which needs the "Usage access"
    // appop — a grant no permission dialog can ask for. Without it the poll loop runs and finds
    // nothing, so the toggle would sit on "enabled" while doing absolutely nothing. Checked here so
    // the card can say so instead of pretending to work.
    val hasUsageAccess = remember {
        runCatching {
            val appOps = context.getSystemService(android.app.AppOpsManager::class.java)
            // checkOpNoThrow is the non-deprecated spelling on the compile SDK and has
            // existed since API 19, so no version branch is needed.
            val mode = appOps?.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!hasUsageAccess) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE53935).copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(R.string.gt_afs_usage_off_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                Text(
                    stringResource(R.string.gt_afs_usage_off_text),
                    fontSize = 11.sp,
                    color = Color(0xFFE53935),
                    lineHeight = 15.sp
                )
                CatsmokerOutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }.onFailure {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }) { Text(stringResource(R.string.gt_afs_usage_btn)) }
            }
        }
    }
}
