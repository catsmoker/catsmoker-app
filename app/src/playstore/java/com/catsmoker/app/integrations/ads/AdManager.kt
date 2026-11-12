package com.catsmoker.app.integrations.ads

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play Store-variant ad gateway (AdMob).
 *
 * Same FQN, constructor and public methods as the full variant's [AdManager]
 * (Start.io) so shared callers (`HomeViewModel`) compile
 * against both. The gate differs on purpose: ads follow ONLY the verified
 * `remove_ads` purchase ([RemoveAdsPolicy.ENTITLED_PREF_KEY], written solely
 * by [RemoveAdsRepository] after Play reports PURCHASED). The legacy
 * `ads_enabled` preference is ignored here, so the excluded free toggle
 * can never disable ads, even if its stored
 * value is flipped by hand.
 */
@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun isPreferenceKey(key: String?): Boolean = key == RemoveAdsPolicy.ENTITLED_PREF_KEY

    fun isEnabled(): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return !prefs.getBoolean(RemoveAdsPolicy.ENTITLED_PREF_KEY, false)
    }

}
