package com.catsmoker.app.screens.gaming.automation

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PersistableBundle
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.R
import com.catsmoker.app.screens.gaming.GameInfo
import com.catsmoker.app.screens.gaming.components.gamingOperationLabel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutomationControlsEntryPoint {
    fun access(): AutomationAccess
    fun automation(): GameAutomation
}

@Composable
private fun automationControls(): AutomationControlsEntryPoint {
    val context = LocalContext.current
    return remember { EntryPointAccessors.fromApplication(context.applicationContext, AutomationControlsEntryPoint::class.java) }
}

@Composable
fun AutomationEntry(onClick: () -> Unit) {
    val enabled = if (LocalInspectionMode.current) false else {
        val value by automationControls().access().enabled.collectAsStateWithLifecycle()
        value
    }
    val status = stringResource(if (enabled) R.string.gt_automation_on else R.string.gt_automation_off)
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).semantics { stateDescription = status },
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.gt_automation), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun AutomationDialog(
    selectedGame: GameInfo?,
    canApply: Boolean,
    backendLabel: String,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val controls = automationControls()
    val access = controls.access()
    val enabled by access.enabled.collectAsStateWithLifecycle()
    val automation = controls.automation()
    val status by automation.status.collectAsStateWithLifecycle()
    val delivery by automation.delivery.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var integration by rememberSaveable { mutableStateOf(0) }
    var showResult by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val setup: @Composable () -> Unit = {
        Text(selectedGame?.appName ?: stringResource(R.string.gt_context_select_game),
            style = MaterialTheme.typography.titleMedium)
        selectedGame?.let { Text(it.packageName, style = MaterialTheme.typography.bodySmall) }
        Text(backendLabel, style = MaterialTheme.typography.bodySmall)
        FilledTonalButton(enabled = canApply && selectedGame != null, onClick = onApply) {
            Text(stringResource(R.string.gt_workspace_apply))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = enabled, role = Role.Switch,
            onValueChange = { if (it) access.enable() else confirm = "revoke" }),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (enabled) R.string.gt_automation_on else R.string.gt_automation_off),
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.gt_automation_activation_hint), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
        if (enabled) {
            HorizontalDivider()
            Text(stringResource(R.string.gt_automation_key), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.gt_automation_key_help), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FilledTonalButton(onClick = {
                    val clip = ClipData.newPlainText("CatSmoker automation", access.token().orEmpty())
                    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    feedback = context.getString(R.string.gt_automation_copied)
                }) { Text(stringResource(R.string.gt_automation_copy)) }
                TextButton(onClick = { confirm = "regenerate" }) { Text(stringResource(R.string.gt_automation_regenerate)) }
            }
        }
        if (enabled) {
            Text(stringResource(if (context.getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(context.packageName))
                R.string.gt_automation_unattended_ready else R.string.gt_automation_unattended_restricted),
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    .onFailure { feedback = context.getString(R.string.gt_automation_settings_unavailable) }
            }) { Text(stringResource(R.string.gt_automation_battery_settings)) }
        }
        HorizontalDivider()
        Text(stringResource(R.string.gt_automation_last_result), style = MaterialTheme.typography.titleSmall)
        val result = remember(status) { JSONObject(status) }
        val receipt = remember(delivery) { JSONObject(delivery) }
        Text(stringResource(gamingOperationLabel(result.optString("result"))))
        if (receipt.optString("result") != "idle") {
            Text(stringResource(R.string.gt_automation_last_delivery,
                stringResource(gamingOperationLabel(receipt.optString("result")))),
                style = MaterialTheme.typography.bodySmall)
        }
        result.optString("error").takeIf { it.isNotBlank() && it != "null" }?.let { Text(it) }
        TextButton(onClick = { showResult = !showResult }) {
            Text(stringResource(if (showResult) R.string.gt_advanced_hide else R.string.gt_automation_details))
        }
        if (showResult) SelectionContainer {
            Text(JSONObject().put("last", result).put("lastDelivery", receipt).toString(2),
                style = MaterialTheme.typography.bodySmall)
        }
        feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
    val integrations: @Composable () -> Unit = {
        Text(stringResource(R.string.gt_automation_integrations), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tasker", "MacroDroid", "ADB", stringResource(R.string.gt_automation_shortcuts)).forEachIndexed { index, name ->
                FilterChip(selected = integration == index, onClick = { integration = index }, label = { Text(name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface))
            }
        }
        Text(stringResource(when (integration) {
            0 -> R.string.gt_automation_tasker_help
            1 -> R.string.gt_automation_macro_help
            2 -> R.string.gt_automation_adb_help
            else -> R.string.gt_automation_shortcut_help
        }))
        if (integration == 3) {
            FilledTonalButton(enabled = enabled && selectedGame != null, onClick = {
                val game = selectedGame ?: return@FilledTonalButton
                val token = access.token() ?: return@FilledTonalButton
                scope.launch {
                    val requested = withContext(Dispatchers.IO) {
                        runCatching {
                            val manager = context.getSystemService(ShortcutManager::class.java)
                            if (!manager.isRequestPinShortcutSupported) return@runCatching false
                            val intent = Intent(context, GameAutomationActivity::class.java).apply {
                                action = AutomationProtocol.ACTION
                                putExtra("operation", "APPLY_AND_LAUNCH")
                                putExtra("game", game.packageName)
                                putExtra("token", token)
                            }
                            val id = "gaming:${game.packageName}"
                            manager.enableShortcuts(listOf(id))
                            manager.requestPinShortcut(ShortcutInfo.Builder(context, id)
                                .setShortLabel(game.appName.take(40))
                                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                                .setIntent(intent).build(), null)
                        }.getOrDefault(false)
                    }
                    feedback = context.getString(if (requested) R.string.gt_automation_shortcut_requested
                        else R.string.gt_automation_shortcut_unavailable)
                }
            }) { Text(stringResource(R.string.gt_automation_shortcut)) }
        } else if (selectedGame != null) {
            val example = automationExample(integration, selectedGame.packageName)
            val copied = stringResource(R.string.gt_automation_copied_template)
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("CatSmoker automation template",
                        if (integration == 2) example.substringBefore('\n') else example))
                feedback = copied
            }) { Text(stringResource(if (integration == 2) R.string.gt_automation_copy_adb else R.string.gt_automation_copy_intent)) }
            SelectionContainer {
                Text(example, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        } else Text(stringResource(R.string.gt_context_select_game))
        Text(stringResource(R.string.gt_automation_background_simple), style = MaterialTheme.typography.bodySmall)
    }
    val setupScroll = rememberScrollState()
    val integrationScroll = rememberScrollState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 940.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.gt_automation), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.gt_automation_close)) }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                    val gap = Arrangement.spacedBy(8.dp)
                    if (maxWidth >= 680.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f).verticalScroll(setupScroll), verticalArrangement = gap) { setup() }
                            Column(Modifier.weight(1.35f).verticalScroll(integrationScroll), verticalArrangement = gap) { integrations() }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().verticalScroll(setupScroll), verticalArrangement = gap) {
                            setup()
                            HorizontalDivider()
                            integrations()
                        }
                    }
                }
            }
        }
    }
    if (confirm != null) AlertDialog(onDismissRequest = { confirm = null },
        title = { Text(stringResource(if (confirm == "regenerate") R.string.gt_automation_regenerate else R.string.gt_automation_revoke)) },
        text = { Text(stringResource(R.string.gt_automation_invalidate_warning)) },
        confirmButton = {
            TextButton(onClick = {
                val regenerate = confirm == "regenerate"
                access.revoke()
                if (regenerate) access.enable()
                confirm = null
                feedback = null
            }) { Text(stringResource(R.string.gt_automation_confirm)) }
        }, dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.gt_keep_access)) } })
}

/** Examples never interpolate the secret; copying a key is a separate, explicit sensitive action. */
internal fun automationExample(integration: Int, pkg: String): String {
    require(com.catsmoker.app.screens.gaming.GameSettings.validPackage(pkg))
    val receiver = AutomationProtocol.RECEIVER
    val target = AutomationProtocol.APP_PACKAGE
    return if (integration == 2) {
        val prefix = "adb shell am broadcast --user current -n $target/$receiver -a ${AutomationProtocol.ACTION} --es token TOKEN"
        "$prefix --es operation APPLY_SAVED --es game $pkg\n\n" +
            "$prefix --es operation QUERY_STATUS\n\n" +
            "$prefix --es operation RESTORE"
    } else {
        "Action: ${AutomationProtocol.ACTION}\nPackage: $target\nClass: $receiver\nTarget: Broadcast Receiver\n" +
            "\nString extras:\ntoken: TOKEN\noperation: APPLY_SAVED\ngame: $pkg"
    }
}
