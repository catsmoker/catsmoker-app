package com.catsmoker.app.application.privacy

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * The single owner of the user's analytics choice and of its effect on the SDK.
 *
 * Both distributions bundle Analytics, but its automatic provider is removed. Only
 * an explicit, current-version onboarding selection applied on Continue or a Settings
 * choice may initialize it.
 *
 * The choice lives in `app_prefs` (key `analytics_enabled`) rather than a new file
 * because the key already shipped in [com.catsmoker.app.screens.settings.SettingsViewModel];
 * reusing it preserves the preference identity; a separate choice version ensures
 * an old default-on value is never mistaken for a new explicit opt-in.
 *
 * SDK collection and the onboarding switch default to disabled. A pending selection is
 * not consent; neither is a legacy default-on preference.
 * [sync] runs from [com.catsmoker.app.system.CatsmokerApp.onCreate] on every process
 * start, because the SDK persists its own flag and the two can drift if the app data is
 * restored without the SDK's preferences, or if the user changes the choice while a
 * previous process is still shutting down.
 */
object AnalyticsConsent {

    const val PREFS = "app_prefs"
    const val KEY_ENABLED = "analytics_enabled"

    /** Collection remains off until an explicit choice is applied. */
    const val DEFAULT_ENABLED = false

    private const val KEY_CHOICE_VERSION = "analytics_choice_version"

    fun isEnabled(context: Context): Boolean = appPrefs(context).let {
        PrivacyPolicy.analyticsAllowed(it.getBoolean(KEY_ENABLED, DEFAULT_ENABLED),
            it.getInt(KEY_CHOICE_VERSION, 0))
    }

    /**
     * Persists the choice first, then applies it. A failed SDK call must never lose the
     * preference, so the write is not conditional on the SDK — [sync] re-applies it on the
     * next start.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        appPrefs(context).edit {
            putBoolean(KEY_ENABLED, enabled)
            putInt(KEY_CHOICE_VERSION, PrivacyPolicy.VERSION)
        }
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
            if (FirebaseApp.getApps(context).isEmpty()) {
                if (!enabled) return@runCatching
                if (FirebaseApp.initializeApp(context) == null) return@runCatching
            }
            val analytics = FirebaseAnalytics.getInstance(context)
            // Analytics opt-in is not advertising consent. Keep advertising uses denied here;
            // Play's independent CMP controls its advertising SDK.
            analytics.setConsent(mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (enabled)
                    FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED
            ))
            analytics.setAnalyticsCollectionEnabled(enabled)
        }
    }
}
