package com.catsmoker.app.system.config

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catsmoker.app.R

/** Blocks feature composition until choices are recorded, never forces optional access. */
@Composable
fun GamingPrivacyGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val appPrefs = remember { context.getSharedPreferences("app_prefs", 0) }
    val consentPrefs = remember { context.getSharedPreferences(GamingAccessConsent.PREFS, 0) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(appPrefs, consentPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        appPrefs.registerOnSharedPreferenceChangeListener(listener)
        consentPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            appPrefs.unregisterOnSharedPreferenceChangeListener(listener)
            consentPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val needsChoice = remember(revision) {
        (appPrefs.getBoolean("terms_accepted", false) || !appPrefs.getBoolean("is_first_run", true)) &&
            !GamingAccessConsent.answered(context)
    }
    if (needsChoice) GamingPrivacyChoices(onDone = { revision++ }) else content()
}

@Composable
fun GamingPrivacyChoices(onDone: () -> Unit) {
    val context = LocalContext.current
    var inventory by remember { mutableStateOf(GamingAccessConsent.allows(context, GamingAccessConsent.INVENTORY)) }
    var usage by remember { mutableStateOf(GamingAccessConsent.allows(context, GamingAccessConsent.USAGE)) }
    var notifications by remember { mutableStateOf(GamingAccessConsent.allows(context, GamingAccessConsent.NOTIFICATIONS)) }
    fun save() {
        GamingAccessConsent.save(context, inventory, usage, notifications)
        onDone()
    }
    AlertDialog(
        onDismissRequest = {
            // Back/outside dismiss is refusal, never consent.
            GamingAccessConsent.save(context, false, false, false)
            onDone()
        },
        title = { Text(stringResource(R.string.policy_access_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.policy_access_intro))
                AccessChoice(inventory, { inventory = it }, R.string.policy_inventory)
                AccessChoice(usage, { usage = it }, R.string.policy_usage)
                AccessChoice(notifications, { notifications = it }, R.string.policy_notifications)
            }
        },
        confirmButton = { TextButton(onClick = { save() }) { Text(stringResource(R.string.policy_save_choices)) } },
        dismissButton = { TextButton(onClick = {
            GamingAccessConsent.save(context, false, false, false); onDone()
        }) { Text(stringResource(R.string.policy_decline)) } }
    )
}

@Composable
private fun AccessChoice(checked: Boolean, onChange: (Boolean) -> Unit, text: Int) {
    Row {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(stringResource(text), modifier = Modifier.padding(top = 10.dp))
    }
}
