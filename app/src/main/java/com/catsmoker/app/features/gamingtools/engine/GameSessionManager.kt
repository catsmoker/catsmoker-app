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
            activePkg != null && activePkg != foregroundPkg ->
                Action.Switch(from = activePkg, to = foregroundPkg)            else -> Action.Noop
        }
    }

    /**
     * What the engine's current state says about the session this monitor recorded.
     *
     * Gaming Mode has more than one caller: this monitor, the card's power button, and the
     * notification's Stop action. "The engine is busy" therefore does not mean "the monitor
     * started it" — the recorded package is the only evidence of ownership.
     */
    enum class Ownership {
        /** The engine and the record agree: nothing to reconcile before the foreground is read. */
        Self,

        /**
         * The engine is running a session this monitor never recorded — a manual activation.
         * Hands off: recording over it and reverting it on the way out of a game would undo the
         * user's own mode.
         */
        Manual,

        /**
         * A record survives an engine that is already idle — the user turned the session off by
         * hand. Dropping it matters: kept, the next manual activation would look like the
         * monitor's own and be reverted when its game is left.
         */
        Stale
    }

    fun ownership(engineBusy: Boolean, recordedPkg: String?): Ownership = when {
        engineBusy && recordedPkg == null -> Ownership.Manual
        !engineBusy && recordedPkg != null -> Ownership.Stale
        else -> Ownership.Self
    }

    /**
     * Whether the game in the foreground is the one the user switched Gaming Mode off for.
     *
     * Dropping a [Ownership.Stale] record leaves "a library game is foreground with no recorded
     * session", which is exactly the state that starts one. Without this veto the monitor would
     * switch the mode straight back on for the game the user just turned it off in, every 3 s.
     * The veto lifts when the user leaves that game (a later visit is a fresh intent) and when
     * automatic Gaming Mode itself is switched off.
     */
    fun isUserStopped(foregroundPkg: String?, vetoPkg: String?): Boolean =
        foregroundPkg != null && foregroundPkg == vetoPkg
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

    /** The game whose session the user ended by hand; suppressed until they leave it. */
    private var userStoppedPkg: String? = null

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        if (!enabled) {
            _sessionActivePkg.value = null
            userStoppedPkg = null
        }
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
     * Drops the recorded session without acting.
     *
     * Called by the monitor when the engine is already idle — the session the record describes
     * ended some other way (the card's power button, or a manual revert) — and by its own shutdown
     * after the revert it owes. It never touches Gaming Mode itself: the engine is the only
     * writer, and a record is book-keeping, not a lever.
     */
    fun release() {
        _sessionActivePkg.value = null
    }

    /**
     * Drops the record because the user ended the session themselves, and remembers the game so
     * the next poll does not start the mode again for it.
     */
    fun releaseByUser() {
        userStoppedPkg = _sessionActivePkg.value
        _sessionActivePkg.value = null
    }

    /**
     * Whether this foreground sighting is inside the game the user switched the mode off for.
     *
     * Answers no (and lifts the veto) as soon as the foreground moves elsewhere: coming back to
     * the game later is a new decision, not a continuation of the suppressed one. An unreadable
     * foreground neither lifts nor triggers the veto — a failed read must not restart the mode.
     */
    fun isUserStopped(foregroundPkg: String?): Boolean {
        if (!GameSessionDecider.isUserStopped(foregroundPkg, userStoppedPkg)) {
            if (foregroundPkg != null) userStoppedPkg = null
            return false
        }
        return true
    }

    private companion object {
        const val PREFS_NAME = "GameSessionPrefs"
        const val KEY_ENABLED = "auto_session_enabled"
        const val KEY_GAMES = "auto_session_games"
    }
}
