package com.catsmoker.app.system

import android.app.Application
import android.content.Context
import com.catsmoker.app.BuildConfig
import com.startapp.sdk.adsbase.StartAppSDK

/**
 * Full-variant ads bootstrap (Start.io).
 *
 * Same FQN as the playstore variant's [AdsInit]; [CatsmokerApp] calls this
 * without knowing which SDK is on the classpath.
 */
object AdsInit {
    fun init(app: Application) {
        val prefs = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val adsEnabledSetting = prefs.getBoolean("ads_enabled", true)
        val appId = BuildConfig.STARTIO_APP_ID
        if (appId.isNotEmpty()) {
            StartAppSDK.init(app, appId, true)
            StartAppSDK.enableReturnAds(false) // Disable to prevent early WebView creation

            // If ads are disabled in settings, make sure SDK knows (though init still happens)
            if (!adsEnabledSetting) {
                StartAppSDK.enableReturnAds(false)
            }
            // Demo ID: 205489527
            if (appId == "205489527") {
                StartAppSDK.setTestAdsEnabled(true)
            }
        }
    }
}
