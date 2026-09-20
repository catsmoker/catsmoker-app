package com.catsmoker.app.system.ads

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.catsmoker.app.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play-store branch ad gateway (AdMob — see PLAYSTORE.md).
 *
 * The user's `ads_enabled` toggle is the single gate: every surface checks [isEnabled]
 * before loading or showing anything, so opting out means zero ad requests.
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

    /**
     * Loads an interstitial and shows it as soon as it is ready. No-op when ads are
     * disabled, when no ad unit is configured, or without an [Activity] to present on.
     * An SDK-side throw degrades to a no-op as well — an ad must never crash the app.
     */
    // Interstitial wiring pending per PLAYSTORE.md; kept loaded-but-uncalled until then.
    @Suppress("unused")
    fun showInterstitial(context: Context) {
        if (!isEnabled()) return
        val activity = context as? Activity ?: return
        val adUnitId = BuildConfig.ADMOB_INTERSTITIAL_ID
        if (adUnitId.isEmpty()) return
        try {
            InterstitialAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        runCatching { ad.show(activity) }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        // Nothing to show — the app simply continues without an ad.
                    }
                }
            )
        } catch (_: Exception) {
            // Nothing to show — the app simply continues without an ad.
        }
    }
}
