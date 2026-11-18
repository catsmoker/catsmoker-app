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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.R
import com.catsmoker.app.screens.gaming.GameInfo
import com.catsmoker.app.screens.gaming.components.GamingToolSheet
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
fun AutomationDialog(selectedGame: GameInfo?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val controls = automationControls()
    val access = controls.access()
    val enabled by access.enabled.collectAsStateWithLifecycle()
    val status by controls.automation().status.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var integration by rememberSaveable { mutableStateOf(0) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    GamingToolSheet(stringResource(R.string.gt_automation), onDismiss) {
        Text(stringResource(R.string.gt_automation_intro), style = MaterialTheme.typography.bodyLarge)
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
            Text("••••  ••••  ••••  ••••", style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.gt_automation_key_help), style = MaterialTheme.typography.bodySmall)
            Row {
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
        HorizontalDivider()
        Text(stringResource(R.string.gt_automation_integrations), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tasker", "MacroDroid", "ADB", stringResource(R.string.gt_automation_shortcuts)).forEachIndexed { index, name ->
                FilterChip(selected = integration == index, onClick = { integration = index }, label = { Text(name) })
            }
        }
        Text(stringResource(when (integration) {
            0 -> R.string.gt_automation_tasker_help
            1 -> R.string.gt_automation_macro_help
            2 -> R.string.gt_automation_adb_help
            else -> R.string.gt_automation_shortcut_help
        }))
        if (integration == 3) {
            Text(selectedGame?.appName ?: stringResource(R.string.gt_context_select_game),
                style = MaterialTheme.typography.titleSmall)
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
        }
        Text(stringResource(R.string.gt_automation_background_simple), style = MaterialTheme.typography.bodySmall)
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
        Text(stringResource(R.string.gt_automation_last_result), style = MaterialTheme.typography.titleSmall)
        val result = remember(status) { runCatching { JSONObject(status) }.getOrDefault(JSONObject()) }
        Text(stringResource(gamingOperationLabel(result.optString("result"))))
        result.optString("error").takeIf { it.isNotBlank() && it != "null" }?.let { Text(it) }
        feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        TextButton(onClick = { advanced = !advanced }) {
            Text(stringResource(if (advanced) R.string.gt_advanced_hide else R.string.gt_automation_advanced))
        }
        if (advanced) {
            Text(stringResource(R.string.gt_automation_contract_help))
            val pkg = selectedGame?.packageName ?: "com.example.game"
            SelectionContainer {
                Text(automationExample(integration, pkg), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.gt_automation_result_help), style = MaterialTheme.typography.bodySmall)
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
    val receiver = "com.catsmoker.app.screens.gaming.automation.GameAutomationReceiver"
    return if (integration == 2) {
        "adb shell am broadcast -n com.catsmoker.app/$receiver -a ${AutomationProtocol.ACTION} " +
            "--es token TOKEN --es operation APPLY_SAVED --es game $pkg\n\n" +
            "adb shell am broadcast -n com.catsmoker.app/$receiver -a ${AutomationProtocol.ACTION} " +
            "--es token TOKEN --es operation QUERY_STATUS\n\n" +
            "adb shell am broadcast -n com.catsmoker.app/$receiver -a ${AutomationProtocol.ACTION} " +
            "--es token TOKEN --es operation RESTORE"
    } else {
        "Action: ${AutomationProtocol.ACTION}\nPackage: com.catsmoker.app\nClass: $receiver\n" +
            "token: TOKEN\noperation: APPLY_SAVED\ngame: $pkg\n\noperation: RESTORE"
    }
}
