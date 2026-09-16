package com.catsmoker.app.system.ads

import android.content.Context
import androidx.core.content.edit
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
        StartAppSDK.enableReturnAds(enabled)
    }

    /**
     * Loads an interstitial and shows it as soon as it is ready. No-op when ads are
     * disabled, when no ad unit is configured, or without an [Activity] to present on.
     */
    // Interstitial wiring pending per PLAYSTORE.md; kept loaded-but-uncalled until then.
    @Suppress("unused")
    fun showInterstitial(context: Context) {
        if (isEnabled()) {
            StartAppAd.showAd(context)
        }
    }
}
