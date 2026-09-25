package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behaviour lock for the game-session start/stop decisions.
 *
 * The monitor watches the foreground package and owns one session at a time: entering a
 * library game starts it, leaving to anything else ends it, switching games ends one and
 * starts the other. An unknown foreground (usage access missing, query threw) is never
 * acted on — ending a session over a failed read would drop optimizations for a game still
 * being played, and starting one would engage them for nobody.
 */
class GameSessionDeciderTest {

    @Test
    fun enteringAGameStartsIt() {
        assertEquals(
            GameSessionDecider.Action.Start("com.game.a"),
            GameSessionDecider.decide(
                foregroundPkg = "com.game.a",
                games = setOf("com.game.a"),
                activePkg = null
            )
        )
    }

    @Test
    fun stayingInTheSameGameIsANoop() {
        assertEquals(
            GameSessionDecider.Action.Noop,
            GameSessionDecider.decide(
                foregroundPkg = "com.game.a",
                games = setOf("com.game.a"),
                activePkg = "com.game.a"
            )
        )
    }

    @Test
    fun leavingToANonGameEndsTheSession() {
        assertEquals(
            GameSessionDecider.Action.Stop("com.game.a"),
            GameSessionDecider.decide(
                foregroundPkg = "com.launcher",
                games = setOf("com.game.a"),
                activePkg = "com.game.a"
            )
        )
    }

    @Test
    fun switchingGamesEndsOneAndStartsTheOther() {
        assertEquals(
            GameSessionDecider.Action.Switch(from = "com.game.a", to = "com.game.b"),
            GameSessionDecider.decide(
                foregroundPkg = "com.game.b",
                games = setOf("com.game.a", "com.game.b"),
                activePkg = "com.game.a"
            )
        )
    }

    @Test
    fun nonGameForegroundWithNoSessionIsANoop() {
        assertEquals(
            GameSessionDecider.Action.Noop,
            GameSessionDecider.decide(
                foregroundPkg = "com.launcher",
                games = setOf("com.game.a"),
                activePkg = null
            )
        )
    }

    @Test
    fun unknownForegroundNeverActs() {
        assertEquals(
            GameSessionDecider.Action.Noop,
            GameSessionDecider.decide(foregroundPkg = null, games = setOf("com.game.a"), activePkg = "com.game.a")
        )
        assertEquals(
            GameSessionDecider.Action.Noop,
            GameSessionDecider.decide(foregroundPkg = null, games = setOf("com.game.a"), activePkg = null)
        )
    }

    @Test
    fun emptyLibraryEndsAnActiveSessionButStartsNothing() {
        // Library emptied (games uninstalled) while a session was active: the session ends,
        // and with no games known nothing can start.
        assertEquals(
            GameSessionDecider.Action.Stop("com.game.a"),
            GameSessionDecider.decide(foregroundPkg = "com.game.a", games = emptySet(), activePkg = "com.game.a")
        )
        assertEquals(
            GameSessionDecider.Action.Noop,
            GameSessionDecider.decide(foregroundPkg = "com.game.a", games = emptySet(), activePkg = null)
        )
    }
}
