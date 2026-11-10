package com.catsmoker.app.system

import android.app.Activity
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.catsmoker.app.system.ads.AdConsentManager

@Composable
fun DistributionPrivacyGate() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val prefs = remember { context.getSharedPreferences("app_prefs", 0) }
    var complete by remember { mutableStateOf(!prefs.getBoolean("is_first_run", true)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            complete = !prefs.getBoolean("is_first_run", true)
            AdConsentManager.refresh(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(activity, complete) { if (complete) AdConsentManager.gather(activity) }
}
