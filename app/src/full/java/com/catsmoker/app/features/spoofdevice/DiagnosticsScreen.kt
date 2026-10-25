package com.catsmoker.app.features.spoofdevice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.components.ScreenScaffold
import com.catsmoker.app.shared.ui.components.SectionCard

/**
 * Read-only identity report for the app's own process, plus a copy action.
 *
 * The values come from `Build` in this process, so they are the real device side
 * unless this app itself is scoped in the Xposed manager (it is deliberately not
 * in the shipped scope). They are not a `getprop` dump and cannot observe another
 * process's hooked reads — the copy action exists so the report can be attached
 * to a bug report instead of retyped.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Resolve at composition, not from the click's LocalContext: the language can change
    // while this screen is retained, and a click-time resource read would keep the old locale.
    val copiedMessage = stringResource(R.string.spoof_diag_copied)

    val hardware = listOf(
        stringResource(R.string.spoof_field_brand) to Build.BRAND,
        stringResource(R.string.spoof_field_manufacturer) to Build.MANUFACTURER,
        stringResource(R.string.spoof_field_model) to Build.MODEL,
        stringResource(R.string.spoof_diag_product) to Build.PRODUCT,
        stringResource(R.string.spoof_diag_device) to Build.DEVICE,
        stringResource(R.string.spoof_field_board) to Build.BOARD,
        stringResource(R.string.spoof_field_hardware) to Build.HARDWARE
    )
    val software = listOf(
        stringResource(R.string.spoof_diag_release) to Build.VERSION.RELEASE,
        stringResource(R.string.spoof_field_sdk) to Build.VERSION.SDK_INT.toString(),
        stringResource(R.string.spoof_field_build_id) to Build.ID,
        stringResource(R.string.spoof_field_incremental) to Build.VERSION.INCREMENTAL,
        stringResource(R.string.spoof_field_fingerprint) to Build.FINGERPRINT
    )
    val system = listOf(
        stringResource(R.string.spoof_field_bootloader) to Build.BOOTLOADER,
        stringResource(R.string.spoof_diag_radio) to
            (Build.getRadioVersion() ?: stringResource(R.string.spoof_diag_unknown)),
        stringResource(R.string.spoof_diag_tags) to Build.TAGS,
        stringResource(R.string.spoof_diag_type) to Build.TYPE
    )
    val native = listOf(
        // Load fact, not a capability claim: the version string the packaged `.so`
        // reports about itself, or unknown when this ABI has no library to load.
        stringResource(R.string.spoof_diag_native_bridge) to
            (com.catsmoker.app.system.shell.NativeBridge.version()
                ?: stringResource(R.string.spoof_diag_unknown))
    )
    val sections = listOf(
        stringResource(R.string.spoof_diag_hardware) to hardware,
        stringResource(R.string.spoof_diag_software) to software,
        stringResource(R.string.spoof_diag_system) to system,
        stringResource(R.string.spoof_diag_native_title) to native
    )

    ScreenScaffold(
        title = stringResource(R.string.spoof_menu_diag),
        subtitle = stringResource(R.string.spoof_diag_subtitle),
        onBack = onBack,
        trailingContent = {
            IconButton(
                onClick = {
                    val report = sections.joinToString("\n\n") { (title, fields) ->
                        title + "\n" + fields.joinToString("\n") { (label, value) ->
                            "$label: $value"
                        }
                    }
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("catsmoker-spoof-diagnostics", report)
                    )
                    Toast.makeText(
                        context,
                        copiedMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.spoof_diag_copy)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            sections.forEach { (title, fields) ->
                DiagGroup(title = title) {
                    fields.forEach { (label, value) -> DiagField(label, value) }
                }
            }
        }
    }
}

@Composable
fun DiagGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        SectionCard(content = content)
    }
}

@Composable
fun DiagField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
