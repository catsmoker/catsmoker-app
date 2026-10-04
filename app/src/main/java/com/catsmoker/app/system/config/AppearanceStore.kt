package com.catsmoker.app.system.config

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Appearance preferences: theme mode and app language.
 *
 * A plain singleton rather than a Hilt binding because both consumers live outside the
 * object graph's reach: [com.catsmoker.app.system.CatsmokerApp.attachBaseContext] and
 * [com.catsmoker.app.system.MainActivity.attachBaseContext] run before injection exists,
 * and the theme is read inside `setContent` before any ViewModel is created. Own prefs
 * file (`appearance_prefs`) — new features get their own file rather than `app_prefs`.
 */
object AppearanceStore {

    enum class ThemeMode { SYSTEM, DARK, LIGHT, DYNAMIC }

    /**
     * Capability, not preference: Material You dynamic color exists only on Android 12+
     * (API 31). A plain `val` on this singleton, so it is computed once per process —
     * the OS version cannot change under a running app, and no prefs/DataStore is spent
     * remembering it. The UI offers the Dynamic option only where this is true; the
     * stored [ThemeMode] preference itself is untouched.
     */
    val supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Theme options the UI may offer. Pure in the capability argument so it stays
     * JVM-testable (`Build.VERSION.SDK_INT` is 0 in unit tests): pass nothing in the
     * app, `true`/`false` in tests.
     */
    fun availableThemeModes(supportsDynamic: Boolean = supportsDynamicColor): List<ThemeMode> =
        if (supportsDynamic) listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.DYNAMIC)
        else listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT)

    /** Empty means "follow the system". Otherwise a BCP-47 tag resolvable by Resources. */
    const val LANGUAGE_SYSTEM = ""
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_ARABIC = "ar"
    const val LANGUAGE_SPANISH = "es"
    const val LANGUAGE_CHINESE = "zh-CN"

    private const val PREFS = "appearance_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_CHOSEN = "appearance_chosen"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    @Volatile
    private var initialized = false

    /** Idempotent: safe to call from every entry point that needs the flow primed. */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            _themeMode.value = themeModeSync(context)
            initialized = true
        }
    }

    fun themeModeSync(context: Context): ThemeMode {
        val raw = prefs(context).getString(KEY_THEME, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(raw ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit { putString(KEY_THEME, mode.name) }
        _themeMode.value = mode
    }

    fun languageTag(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, LANGUAGE_SYSTEM).orEmpty()

    /**
     * Marks the gate answered. Synchronous commit, not apply: the language path recreates
     * the activity immediately after, and the recreated instance must see the flag even
     * if the process dies mid-restart.
     */
    fun setChosen(context: Context) {
        prefs(context).edit(commit = true) { putBoolean(KEY_CHOSEN, true) }
    }

    /**
     * Synchronous commit, not apply: every caller restarts the process immediately after
     * ([LocaleHelper.restartApp] ends it with `exit(0)`), and an async `apply()` queued
     * write dies with the VM before it flushes — the fresh process then reads the stale
     * tag and stays on the previous language (Arabic-selected English showing Arabic).
     * Same reason [setChosen] commits.
     */
    fun setLanguage(context: Context, tag: String) {
        // Normalized on write so a region variant (e.g. `ar-SA` from a previous build)
        // can never persist and resolve to the wrong table later.
        prefs(context).edit(commit = true) { putString(KEY_LANGUAGE, LocaleHelper.normalizeTag(tag)) }
    }

    /** Whether the onboarding appearance gate was answered (survives a locale restart). */
    fun isChosen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHOSEN, false)

    // NOTE: uses the passed context directly, never context.applicationContext — during
    // Application.attachBaseContext the application object is not attached yet and
    // getApplicationContext() returns null, which crashed startup (the locale wrap reads
    // prefs before super.attachBaseContext runs). The prefs file is app-scoped by name
    // on any context, so this is identical everywhere else too.
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
