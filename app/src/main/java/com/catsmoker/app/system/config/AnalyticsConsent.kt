package com.catsmoker.app.system.config

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * The single owner of the user's analytics choice and of its effect on the SDK.
 *
 * The Settings switch used to write a preference nothing read: Firebase Analytics is
 * bundled in both flavours (`firebase-analytics`, initialised by `FirebaseInitProvider`
 * before any of our code runs), so the toggle claimed a control it did not have. This
 * object closes that gap — it holds the preference, and every write also tells
 * [FirebaseAnalytics] whether collection is allowed.
 *
 * The choice lives in `app_prefs` (key `analytics_enabled`) rather than a new file
 * because the key already shipped in [com.catsmoker.app.features.settings.SettingsViewModel];
 * reusing it keeps users who already flipped the switch on their stored answer.
 *
 * Default is enabled, matching Firebase's own default and the toggle's existing copy.
 * [sync] runs from [com.catsmoker.app.system.CatsmokerApp.onCreate] on every process
 * start, because the SDK persists its own flag and the two can drift if the app data is
 * restored without the SDK's preferences, or if the user changes the choice while a
 * previous process is still shutting down.
 */
object AnalyticsConsent {

    const val PREFS = "app_prefs"
    const val KEY_ENABLED = "analytics_enabled"

    /** Firebase collects until the user says otherwise; the legal text states this. */
    const val DEFAULT_ENABLED = true

    fun isEnabled(context: Context): Boolean = appPrefs(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    /**
     * Persists the choice first, then applies it. A failed SDK call must never lose the
     * preference, so the write is not conditional on the SDK — [sync] re-applies it on the
     * next start.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        appPrefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        apply(context, enabled)
    }

    /** Re-applies the stored choice to the SDK. Idempotent; safe at every startup. */
    fun sync(context: Context) {
        apply(context, isEnabled(context))
    }

    private fun appPrefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * An SDK-side throw (missing google-services metadata, Play Services broken) must not
     * take the app down on startup — the same fail-safe posture as `AdsInit`. The choice
     * itself is already persisted, so a failure here is retried next launch.
     */
    private fun apply(context: Context, enabled: Boolean) {
        runCatching {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        }
    }
}
