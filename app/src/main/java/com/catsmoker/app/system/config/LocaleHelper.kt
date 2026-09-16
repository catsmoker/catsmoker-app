package com.catsmoker.app.system.config

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Manual per-app locale wrapping (no appcompat dependency in this app, so
 * `AppCompatDelegate.setApplicationLocales` is unavailable).
 *
 * Both the [android.app.Application] and the activity route `attachBaseContext` through
 * [wrap], so Compose resources, ViewModel `getString` calls, services and notifications
 * all resolve the saved language. Changing the language needs an activity `recreate()`
 * (the Settings screen emits that); the theme instead recomposes live via
 * [AppearanceStore.themeMode].
 */
object LocaleHelper {

    // In-app switcher covers all API levels with activity recreate(); no Play Core language delivery.
    @SuppressLint("AppBundleLocaleChanges")
    fun wrap(base: Context): Context {
        val tag = AppearanceStore.languageTag(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}
