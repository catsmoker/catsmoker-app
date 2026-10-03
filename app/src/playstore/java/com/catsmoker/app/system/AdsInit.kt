package com.catsmoker.app.system

import android.app.Application
import android.content.Context
import com.google.android.gms.ads.MobileAds

/**
 * Play Store-variant ads bootstrap (AdMob).
 *
 * Same FQN as the full variant's [AdsInit]. Initialization is fail-safe: an
 * SDK-side throw (missing App ID, bad WebView) must never take the app down —
 * the banners additionally collapse when no ad arrives.
 */
object AdsInit {
    fun init(app: Application) {
        val prefs = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val adsEnabled = prefs.getBoolean("ads_enabled", true)
        if (!adsEnabled) return
        runCatching {
            MobileAds.initialize(app)
        }
    }
}
