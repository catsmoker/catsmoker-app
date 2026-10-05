package com.catsmoker.app.system

import android.app.Application
import android.content.Context
import com.catsmoker.app.system.ads.RemoveAdsPolicy
import com.google.android.gms.ads.MobileAds

/**
 * Play Store-variant ads bootstrap (AdMob).
 *
 * Same FQN as the full variant's [AdsInit]. Initialization is fail-safe: an
 * SDK-side throw (missing App ID, bad WebView) must never take the app down —
 * the banners additionally collapse when no ad arrives. Skipped entirely once
 * the verified `remove_ads` purchase is on record; the legacy `ads_enabled`
 * preference is ignored here, matching [com.catsmoker.app.system.ads.AdManager].
 */
object AdsInit {
    fun init(app: Application) {
        val prefs = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val adFree = prefs.getBoolean(RemoveAdsPolicy.ENTITLED_PREF_KEY, false)
        if (adFree) return
        runCatching {
            MobileAds.initialize(app)
        }
    }
}
