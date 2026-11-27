package com.catsmoker.app.screens.gaming.display

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The refresh rate the user wants in each context, remembered once and reused.
 *
 * Four scopes, because they are the four situations where the answer legitimately differs: the
 * phone on its own, an attached external display, Samsung DeX, and one particular game. A game
 * scope is keyed by package name so a saved preference survives reinstalling the game, and a
 * scope with no saved value falls back to the global one rather than to a guessed default.
 */
@Singleton
class DisplayRateStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The rate saved for [scope], or null when the user never chose one there. */
    fun rate(scope: String): Float? = prefs.getString(key(scope), null)?.toFloatOrNull()

    /** Saves [hz] for [scope]; null removes the preference instead of storing a zero. */
    fun setRate(scope: String, hz: Float?) {
        prefs.edit {
            if (hz == null || hz <= 0f) remove(key(scope)) else putString(key(scope), hz.toString())
        }
    }

    /**
     * The rate to apply for one request, most specific scope first.
     *
     * @param game the selected package, or null for a global request.
     * @param onExternalDisplay true when an external display is attached and lit.
     * @param onDeX true when the external display is a DeX desktop.
     */
    fun resolve(game: String?, onExternalDisplay: Boolean, onDeX: Boolean): Float? {
        val chain = buildList {
            game?.let { add(gameScope(it)) }
            if (onDeX) add(DEX)
            if (onExternalDisplay) add(EXTERNAL)
            add(GLOBAL)
        }
        return chain.firstNotNullOfOrNull { rate(it) }
    }

    /** Which scope a value would be saved under, for the UI to name it honestly. */
    fun scopeLabel(scope: String): String = when (scope) {
        GLOBAL -> "All displays"
        EXTERNAL -> "External display"
        DEX -> "Samsung DeX"
        else -> scope.removePrefix(GAME_PREFIX)
    }

    companion object {
        const val GLOBAL = "global"
        const val EXTERNAL = "external"
        const val DEX = "dex"

        private const val GAME_PREFIX = "game:"
        private const val PREFS_NAME = "display_rates"
        private const val KEY_PREFIX = "hz:"

        fun gameScope(pkg: String): String = GAME_PREFIX + pkg

        private fun key(scope: String): String = KEY_PREFIX + scope
    }
}
