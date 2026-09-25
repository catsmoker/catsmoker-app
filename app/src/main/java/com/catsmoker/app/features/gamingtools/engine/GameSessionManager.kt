package com.catsmoker.app.features.gamingtools.engine

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides game-session transitions from the foreground package.
 *
 * One session at a time: entering a library game starts it, leaving to anything else ends
 * it, switching games ends one and starts the other. An unknown foreground (usage access
 * missing, query threw) is never acted on — ending a session over a failed read would drop
 * optimizations for a game still being played.
 */
object GameSessionDecider {

    sealed interface Action {
        data class Start(val pkg: String) : Action
        data class Stop(val pkg: String) : Action
        data class Switch(val from: String, val to: String) : Action
        data object Noop : Action
    }

    fun decide(foregroundPkg: String?, games: Set<String>, activePkg: String?): Action {
        if (foregroundPkg == null) return Action.Noop
        val isGame = foregroundPkg in games
        return when {
            activePkg == null && isGame -> Action.Start(foregroundPkg)
            activePkg != null && !isGame -> Action.Stop(activePkg)
            activePkg != null && isGame && activePkg != foregroundPkg ->
                Action.Switch(from = activePkg, to = foregroundPkg)
            else -> Action.Noop
        }
    }
}

/**
 * Owns the auto game session: which library games exist, whether auto-activate is armed,
 * and which package holds the session right now.
 *
 * Privilege-free by design: it only decides. The monitor service executes — starting and
 * stopping Gaming Mode through the engine — so this class stays unit-shaped (prefs + flows)
 * with the [GameSessionDecider] rule doing the thinking. Its own prefs file: the game set
 * here means "auto-start for these", which must never be confused with the suspend list's
 * "freeze these" or the keep-alive list's "never close these".
 */
@Singleton
class GameSessionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _sessionActivePkg = MutableStateFlow<String?>(null)
    /** Package holding the auto session, or null when none is active. */
    val sessionActivePkg: StateFlow<String?> = _sessionActivePkg.asStateFlow()

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        if (!enabled) _sessionActivePkg.value = null
    }

    fun getGames(): Set<String> = prefs.getStringSet(KEY_GAMES, emptySet()) ?: emptySet()

    fun setGames(pkgs: Set<String>) {
        prefs.edit { putStringSet(KEY_GAMES, pkgs) }
    }

    /**
     * Applies one foreground sighting. Returns the decided action so the service — the only
     * caller with the engine — can execute it, then records the new session holder.
     */
    fun onForeground(foregroundPkg: String?): GameSessionDecider.Action {
        val action = GameSessionDecider.decide(foregroundPkg, getGames(), _sessionActivePkg.value)
        when (action) {
            is GameSessionDecider.Action.Start -> _sessionActivePkg.value = action.pkg
            is GameSessionDecider.Action.Stop -> _sessionActivePkg.value = null
            is GameSessionDecider.Action.Switch -> _sessionActivePkg.value = action.to
            GameSessionDecider.Action.Noop -> Unit
        }
        return action
    }

    /**
     * Drops the recorded session without acting — the monitor calls this when Gaming Mode is
     * already active for another reason (a manual activation), so leaving the game later does
     * not revert a session this monitor never started.
     */
    fun release() {
        _sessionActivePkg.value = null
    }

    private companion object {
        const val PREFS_NAME = "GameSessionPrefs"
        const val KEY_ENABLED = "auto_session_enabled"
        const val KEY_GAMES = "auto_session_games"
    }
}
