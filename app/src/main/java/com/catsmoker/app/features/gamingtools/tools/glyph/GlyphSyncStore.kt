package com.catsmoker.app.features.gamingtools.tools.glyph

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the Gaming Mode loading bar is mirrored onto the Glyph Interface.
 *
 * Its own prefs file, like every feature store: this answer belongs to the Glyph feature and
 * must not be confused with the engine's own record (`gaming_engine_prefs`), which is what
 * actually describes a session.
 *
 * Defaults to on. The switch only exists on a phone the GDK can actually drive, so the default
 * is only ever read where there is hardware to light — a Xiaomi never asks and never binds the
 * Nothing service.
 */
@Singleton
class GlyphSyncStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The user's choice, or the default (on) when it was never touched. */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_GAMING_MODE, true)

    /** Persists [enabled]; it applies from the next Gaming Mode run (and immediately, if one is live). */
    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SYNC_GAMING_MODE, enabled) }
    }

    private companion object {
        const val PREFS_NAME = "glyph_prefs"
        const val KEY_SYNC_GAMING_MODE = "sync_gaming_mode"
    }
}
