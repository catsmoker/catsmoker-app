package com.catsmoker.app.system.config

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import java.util.Locale

/**
 * Manual per-app locale wrapping (no appcompat dependency in this app, so
 * `AppCompatDelegate.setApplicationLocales` is unavailable).
 *
 * Both the [android.app.Application] and the activity route `attachBaseContext` through
 * [wrap], so Compose resources, ViewModel `getString` calls, services and notifications
 * all resolve the saved language. Changing the language restarts the process (the
 * Settings/onboarding screens emit that): a bare activity `recreate()` leaves the
 * application context — and every cached `getString` in the surviving ViewModels,
 * engine state and running services — on the previous language, which is how
 * English-selected UI kept showing Arabic until the process died.
 */
object LocaleHelper {

    /**
     * Normalizes a stored tag to one of the shipped locales (`en`, `ar`, `es`, `zh-CN`)
     * or `""` (system). Region variants (`ar-SA`, `zh-TW`) fold to their shipped base;
     * unknown tags pass through and fall back to English resources at runtime.
     * Pure — covered by `LocaleHelperTest`.
     */
    fun normalizeTag(tag: String): String {
        if (tag.isEmpty()) return tag
        val base = tag.substringBefore('-').lowercase()
        return when (base) {
            "ar" -> AppearanceStore.LANGUAGE_ARABIC
            "es" -> AppearanceStore.LANGUAGE_SPANISH
            "en" -> AppearanceStore.LANGUAGE_ENGLISH
            "zh" -> AppearanceStore.LANGUAGE_CHINESE
            else -> tag
        }
    }

    // In-app switcher covers all API levels with a process restart; no Play Core language delivery.
    @SuppressLint("AppBundleLocaleChanges")
    fun wrap(base: Context): Context {
        val tag = normalizeTag(AppearanceStore.languageTag(base))
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    /**
     * Relaunches the app from its launcher intent and ends this process, so the next
     * process resolves every resource — activity strings, cached ViewModel strings,
     * engine state, services and notification channels — in the newly picked language.
     */
    fun restartApp(context: Context) {
        val launch = runCatching {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        }.getOrNull()
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            runCatching { context.startActivity(launch) }
        }
        Runtime.getRuntime().exit(0)
    }
}
