package com.catsmoker.app.integrations.ads

import android.content.Context
import androidx.core.content.edit
import com.startapp.sdk.adsbase.StartAppSDK
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun isPreferenceKey(key: String?): Boolean = key == "ads_enabled"

    fun isEnabled(): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("ads_enabled", true)
    }

    fun setEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("ads_enabled", enabled) }
        StartAppSDK.enableReturnAds(enabled)
    }
}
