package com.catsmoker.app.screens.gaming.network

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
import com.catsmoker.app.screens.gaming.components.RequirementNotice
import com.catsmoker.app.screens.gaming.network.BackgroundDataRestrictor
import com.catsmoker.app.screens.gaming.network.BackgroundNetworkControls
import java.io.File

/**
 * Two independent ways to stop other apps using the network on the full build;
 * Play ships only the Data Saver half.
 *
 * They are separate switches because they are separate mechanisms with separate requirements, and
 * either alone is a sensible choice:
 * - **App block (local VPN)** works on any network and needs no root, but only one VPN can be active
 *   on the device at a time. Full build only; Play's VpnService policy rejects a non-core VPN.
 * - **Data Saver** is Android's own policy, needs root or Shizuku, and only applies to metered
 *   networks — but it costs nothing and leaves the VPN slot free.
 *
 * Turning both on is allowed and is the strongest setting. Nothing here forces a choice between them.
 *
 * Everything shown is read from the device: [BackgroundDataRestrictor.Status] carries nulls for
 * anything that could not be read, and those render as an explicit unavailable line rather than a
 * default that would look like a reading. Distribution-specific controls are supplied by the
 * selected flavor, not hidden shared implementations.
 */
@Composable
fun BackgroundDataContent(
    status: BackgroundDataRestrictor.Status?,
    engaged: Boolean,
    isChanging: Boolean,
    hasPrivilege: Boolean,
    games: List<GameInfo>,
    onSetDataSaver: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackgroundNetworkControls(games)
        NetworkBlockSwitch(
            title = stringResource(R.string.gt_net_ds_title),
            subtitle = stringResource(R.string.gt_net_ds_sub),
            checked = status?.dataSaverOn == true,
            enabled = hasPrivilege && !isChanging,
            busy = isChanging,
            onCheckedChange = onSetDataSaver
        )
        if (!hasPrivilege) {
            RequirementNotice(
                stringResource(R.string.gt_net_ds_need)
            )
        }

        // --- Live state, or an honest gap where a reading should be ---
        when {
            status == null -> Text(stringResource(R.string.gt_net_checking), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                // Data Saver's own state and how many apps it is actually stopping, in one line, the
                // same shape as the VPN switch above. This used to be two lines, the second listing
                // every restricted package by name — but with Data Saver on that list is most of the
                // phone, so it pushed everything under it off screen and said nothing the count does
                // not. The number is `restrictedUids`, what `cmd netpolicy` actually reported, not the
                // subset whose names happened to resolve.
                val stoppedCount = status.restrictedUids.size
                Text(
                    when {
                        status.dataSaverOn == null -> stringResource(R.string.gt_net_unknown)
                        status.dataSaverOn == true && stoppedCount > 0 -> stringResource(R.string.gt_net_ds_on_count, stoppedCount)
                        status.dataSaverOn == true -> stringResource(R.string.gt_net_on)
                        // Off with apps still restricted is a real state, not a contradiction: the
                        // per-app blacklist blocks whether or not Data Saver is on, and `disable`
                        // deliberately leaves behind entries this app did not add.
                        stoppedCount > 0 -> stringResource(R.string.gt_net_ds_off_stopped, stoppedCount)
                        else -> stringResource(R.string.gt_net_off)
                    },
                    fontSize = 11.sp,
                    color = if (status.dataSaverOn == null) Color(0xFFFFB74D) else MaterialTheme.colorScheme.primary
                )
                if (engaged) {
                    Text(
                        stringResource(R.string.gt_net_engaged),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    when (status.meteredNow) {
                        true -> stringResource(R.string.gt_net_metered)
                        false -> stringResource(R.string.gt_net_unmetered)
                        null -> stringResource(R.string.gt_net_unknown_net)
                    },
                    fontSize = 11.sp,
                    color = if (status.meteredNow == false) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                // The per-app list is gone, but this stays: a build whose `cmd netpolicy` cannot list
                // the blacklist is a real limitation the count line above cannot express, since an
                // unreadable list and an empty one both leave `restrictedUids` empty.
                if (!status.perAppSupported && status.privileged) {
                    Text(
                        stringResource(R.string.gt_net_no_perapp),
                        fontSize = 11.sp,
                        color = Color(0xFFFFB74D)
                    )
                }
                if (status.exemptedPackages.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.gt_net_exempt,
                            status.exemptedPackages.size,
                            status.exemptedPackages.joinToString()
                        ),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * One labelled on/off row for [BackgroundDataContent].
 *
 * Switches use measured state: the flavor's network control and read-back Data Saver value —
 * so it never moves on tap alone. [busy] shows a spinner in place of the switch while the operation
 * is in flight, which is what stops a second tap racing the first.
 */
@Composable
internal fun NetworkBlockSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    busy: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

