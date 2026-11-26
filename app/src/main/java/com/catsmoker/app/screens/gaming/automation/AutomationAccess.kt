package com.catsmoker.app.screens.gaming.automation

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AutomationAccess @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("game_automation_access", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(token() != null)
    val enabled = _enabled.asStateFlow()
    fun token(): String? = prefs.getString("token", null)
    fun accepts(token: String?): Boolean = AutomationProtocol.authorized(token(), token)
    fun enable() {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit { putString("token", bytes.joinToString("") { "%02x".format(it.toInt() and 255) }) }
        _enabled.value = true
    }
    fun revoke() {
        prefs.edit { remove("token") }
        _enabled.value = false
        GameAutomationReceiver.cancelContinuation(context)
        // Token removal remains effective even if an OEM launcher rejects shortcut maintenance.
        runCatching {
            val shortcuts = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            shortcuts.disableShortcuts(shortcuts.pinnedShortcuts.map { it.id }.filter { it.startsWith("gaming:") })
        }
    }
}
