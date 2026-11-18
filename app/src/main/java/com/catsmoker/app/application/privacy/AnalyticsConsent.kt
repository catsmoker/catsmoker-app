package com.catsmoker.app.application.privacy

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * The single owner of the user's analytics choice and of its effect on the SDK.
 *
 * Both distributions bundle Analytics, but its automatic provider is removed. Collection
 * needs accepted terms and a saved, current choice; the checked onboarding draft alone
 * must not start Firebase.
 *
 * The choice lives in `app_prefs` (key `analytics_enabled`) rather than a new file
 * because the key already shipped in [com.catsmoker.app.screens.settings.SettingsViewModel];
 * reusing it preserves the preference identity and any explicit opt-out a user already made.
 *
 * [sync] runs from [com.catsmoker.app.system.CatsmokerApp.onCreate] on every process
 * start, because the SDK persists its own flag and the two can drift if the app data is
 * restored without the SDK's preferences, or if the user changes the choice while a
 * previous process is still shutting down.
 */
object AnalyticsConsent {

    const val PREFS = "app_prefs"
    const val KEY_ENABLED = "analytics_enabled"
    const val KEY_TERMS_ACCEPTED = "terms_accepted"
    private const val KEY_CHOICE_VERSION = "analytics_choice_version"
    const val DEFAULT_ENABLED = false

    fun isEnabled(context: Context): Boolean = appPrefs(context).let {
        PrivacyPolicy.analyticsAllowed(
            termsAccepted = it.getBoolean(KEY_TERMS_ACCEPTED, false),
            enabled = it.getBoolean(KEY_ENABLED, DEFAULT_ENABLED),
            choiceVersion = it.getInt(KEY_CHOICE_VERSION, 0)
        )
    }

    /** Persist terms and the applied choice together, before the SDK sees either. */
    fun acceptAgreement(context: Context, enabled: Boolean) {
        appPrefs(context).edit {
            putBoolean(KEY_TERMS_ACCEPTED, true)
            putBoolean(KEY_ENABLED, enabled)
            putInt(KEY_CHOICE_VERSION, PrivacyPolicy.VERSION)
        }
        sync(context)
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
        sync(context)
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
            // Analytics collection is not advertising consent. Keep advertising uses denied here;
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
