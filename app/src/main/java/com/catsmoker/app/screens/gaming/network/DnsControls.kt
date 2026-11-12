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
import com.catsmoker.app.screens.gaming.components.RequirementNotice
import com.catsmoker.app.screens.gaming.network.DnsFeature
import com.catsmoker.app.ui.components.CatsmokerOutlinedButton
import java.io.File

/**
 * The Private DNS provider chips, shared by the supported and unsupported branches.
 *
 * Rendered disabled (never hidden) where Private DNS does not exist, so the options
 * stay visible with the reason above them. A disabled chip cannot be tapped, so the
 * write callbacks can never fire from the unsupported branch.
 */
@Composable
internal fun DnsProviderChips(
    selectedHostname: String?,
    selectedMode: DnsFeature.Mode?,
    chipsEnabled: Boolean,
    onApplyProvider: (DnsFeature.Provider) -> Unit,
    onSetAutomatic: () -> Unit,
    onDisable: () -> Unit
) {
    // Chips rather than buttons: one of these is the current state, and a chip can show
    // which without a separate label.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DnsFeature.PROVIDERS.forEach { provider ->
            FilterChip(
                selected = selectedHostname == provider.hostname &&
                    selectedMode == DnsFeature.Mode.PROVIDER,
                onClick = { onApplyProvider(provider) },
                enabled = chipsEnabled,
                label = { Text(provider.label, fontSize = 12.sp) }
            )
        }
        FilterChip(
            selected = selectedMode == DnsFeature.Mode.AUTOMATIC,
            onClick = onSetAutomatic,
            enabled = chipsEnabled,
            label = { Text(stringResource(R.string.gt_dns_auto), fontSize = 12.sp) }
        )
        FilterChip(
            selected = selectedMode == DnsFeature.Mode.OFF,
            onClick = onDisable,
            enabled = chipsEnabled,
            label = { Text(stringResource(R.string.gt_dns_off), fontSize = 12.sp) }
        )
    }
}

/**
 * The Private DNS card.
 *
 * It reports the resolvers the active network is *using*, not the ones this app asked for, because the
 * first is evidence and the second is only an intention. The previous version of this card wrote
 * `net.dns1`/`net.dns2` — properties nothing on Android has read since version 5 — so both buttons
 * reported success and changed nothing at all; see [DnsFeature] for the full account.
 */
@Composable
fun DnsContent(
    status: DnsFeature.Status?,
    isChanging: Boolean,
    onRefresh: () -> Unit,
    onApplyProvider: (DnsFeature.Provider) -> Unit,
    onSetAutomatic: () -> Unit,
    onDisable: () -> Unit,
    /** Measured reply ms per provider address (null per address = never replied); null = never measured. */
    pingMs: Map<String, Int?>?,
    isMeasuringPing: Boolean,
    onMeasurePing: () -> Unit
) {
    // Private DNS is equally settable from Settings, so the reading is refreshed on open rather than
    // trusted from whenever this app last wrote it.
    LaunchedEffect(Unit) { onRefresh() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isChanging) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.gt_dns_applying), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when {
            status == null -> Text(stringResource(R.string.gt_dns_checking), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            !status.supported -> {
                Text(
                    status.unsupportedReason ?: stringResource(R.string.gt_dns_unsupported_generic),
                    fontSize = 12.sp, color = Color(0xFFEF9A9A)
                )
                // Global rule: the provider options stay visible but disabled, so
                // the user sees what exists on newer phones instead of nothing.
                // Disabled chips cannot be tapped, so nothing is written.
                Spacer(modifier = Modifier.height(8.dp))
                DnsProviderChips(
                    selectedHostname = null,
                    selectedMode = null,
                    chipsEnabled = false,
                    onApplyProvider = onApplyProvider,
                    onSetAutomatic = onSetAutomatic,
                    onDisable = onDisable
                )
            }
            else -> {
                Text(
                    stringResource(
                        R.string.gt_dns_now_using,
                        status.mode?.let { dnsModeLabel(it) }
                            ?: status.rawMode
                            ?: stringResource(R.string.gt_dns_could_not_read)
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (status.mode == null) Color(0xFFFFB74D) else MaterialTheme.colorScheme.primary
                )
                status.hostname?.let {
                    Text(stringResource(R.string.gt_dns_set_to, it), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // The one line that proves anything happened. If it does not change after applying a
                // provider, the change did not take, whatever the toast said.
                Text(
                    if (status.activeServers.isEmpty()) {
                        stringResource(R.string.gt_dns_no_servers)
                    } else {
                        stringResource(R.string.gt_dns_in_use, status.activeServers.joinToString())
                    },
                    fontSize = 11.sp,
                    color = if (status.activeServers.isEmpty()) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                status.validatedPrivateDns?.let {
                    Text(stringResource(R.string.gt_dns_validated, it), fontSize = 11.sp, color = Color(0xFF81C784))
                }
                if (status.vpnActive) {
                    Text(
                        stringResource(R.string.gt_dns_vpn_note),
                        fontSize = 11.sp, color = Color(0xFFFFB74D)
                    )
                }
                if (!status.canWrite) {
                    RequirementNotice(
                        stringResource(R.string.gt_dns_need)
                    )
                }

                val writable = status.canWrite && !isChanging
                Spacer(modifier = Modifier.height(2.dp))
                // Chips rather than buttons: one of these is the current state, and a chip can show
                // which without a separate label.
                DnsProviderChips(
                    selectedHostname = status.hostname,
                    selectedMode = status.mode,
                    chipsEnabled = writable,
                    onApplyProvider = onApplyProvider,
                    onSetAutomatic = onSetAutomatic,
                    onDisable = onDisable
                )

                // The selected provider's note and proof addresses live in the card's info sheet,
                // which builds the same section for the provider currently in use.

                // Measured reply times, one line per provider — the numbers behind "usually
                // the fastest". Never measured reads as no rows at all; a provider whose
                // address never replied reads as "no reply", never 0 ms.
                CatsmokerOutlinedButton(
                    onClick = onMeasurePing,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isMeasuringPing && !isChanging
                ) {
                    if (isMeasuringPing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.gt_dns_ping_measure), fontSize = 12.sp)
                    }
                }
                pingMs?.forEach { (host, ms) ->
                    val label = DnsFeature.PROVIDERS.firstOrNull { host in it.addresses }?.label
                    Text(
                        text = "${label ?: host} · $host · " +
                            (ms?.let { "$it ms" } ?: stringResource(R.string.gt_dns_ping_none)),
                        fontSize = 11.sp,
                        color = if (ms != null) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Localized DNS mode name. The enum itself keeps the `Settings.Global` values (never translated),
 * so the mapping lives here at the call site inside the composable.
 */
@Composable
internal fun dnsModeLabel(mode: DnsFeature.Mode): String = stringResource(
    when (mode) {
        DnsFeature.Mode.OFF -> R.string.gt_dns_off
        DnsFeature.Mode.AUTOMATIC -> R.string.gt_dns_auto
        DnsFeature.Mode.PROVIDER -> R.string.gt_dns_mode_provider
    }
)
