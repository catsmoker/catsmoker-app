package com.catsmoker.app.features.gamingtools.tools

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the Gaming Mode card's "Advanced" group is open — a UI preference, nothing more.
 *
 * The two app pickers ("Apps kept running", "Extra suspended apps") are configuration surfaces the
 * user touches a few times and then leaves alone, but they were the tallest part of the card and
 * pushed the read-back report and the Recovery block off a phone screen. They now live behind one
 * disclosure, and this store remembers where the user left it, so reopening the screen or the app
 * shows the same shape instead of snapping back.
 *
 * Own prefs file, like every feature store: nothing here can be confused with a package list, and
 * clearing the disclosure state never touches [com.catsmoker.app.features.gamingtools.tools.forcestop.SuspendExemptStore]
 * or `SuspendListStore`. A fresh install reads [DEFAULT_EXPANDED], which is closed.
 */
@Singleton
class GamingModeAdvancedStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The remembered disclosure state; closed until the user opens it once. */
    fun isExpanded(): Boolean = prefs.getBoolean(KEY_EXPANDED, DEFAULT_EXPANDED)

    /** Persists the new disclosure state. Folding never edits an app list. */
    fun setExpanded(expanded: Boolean) {
        prefs.edit { putBoolean(KEY_EXPANDED, expanded) }
    }

    companion object {
        /** Closed by default, so a fresh install shows the card without the two pickers. */
        const val DEFAULT_EXPANDED = false

        private const val PREFS_NAME = "gaming_mode_advanced_prefs"
        private const val KEY_EXPANDED = "advanced_expanded"
    }
}
