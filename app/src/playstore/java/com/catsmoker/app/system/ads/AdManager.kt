package com.catsmoker.app.system.ads

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play Store-variant ad gateway (AdMob).
 *
 * Same FQN, constructor and public methods as the full variant's [AdManager]
 * (Start.io) so shared callers ([SettingsViewModel], `MainViewModel`) compile
 * against both. The user's `ads_enabled` toggle is the single gate: every
 * surface checks [isEnabled] before loading or showing anything, so opting
 * out means zero ad requests.
 */
@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun isEnabled(): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("ads_enabled", true)
    }

    fun setEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("ads_enabled", enabled) }
    }
}
