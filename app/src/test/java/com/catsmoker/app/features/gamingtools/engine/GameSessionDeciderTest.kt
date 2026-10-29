package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun aSessionThisMonitorStartedIsItsOwn() {
        assertEquals(
            GameSessionDecider.Ownership.Self,
            GameSessionDecider.ownership(engineBusy = true, recordedPkg = "com.game.a")
        )
    }

    @Test
    fun anActiveEngineWithNoRecordedSessionIsAManualOne() {
        assertEquals(
            GameSessionDecider.Ownership.Manual,
            GameSessionDecider.ownership(engineBusy = true, recordedPkg = null)
        )
    }

    @Test
    fun anIdleEngineWithNoRecordedSessionOwnsNothing() {
        assertEquals(
            GameSessionDecider.Ownership.Self,
            GameSessionDecider.ownership(engineBusy = false, recordedPkg = null)
        )
    }

    @Test
    fun aRecordWhoseEngineAlreadyStoppedIsStale() {
        // The user turned the session off by hand: the engine is idle while the monitor still
        // remembers the game. Keeping that record would make the next manual activation look
        // like the monitor's own, and leaving the game would then revert it.
        assertEquals(
            GameSessionDecider.Ownership.Stale,
            GameSessionDecider.ownership(engineBusy = false, recordedPkg = "com.game.a")
        )
    }

    @Test
    fun theGameTheUserSwitchedOffIsNotStartedAgain() {
        // Dropping the stale record must not read as "this game has no session yet, start one".
        // The user's stop is a veto until they leave that game.
        assertTrue(GameSessionDecider.isUserStopped("com.game.a", "com.game.a"))
    }

    @Test
    fun leavingThatGameLiftsTheVeto() {
        assertFalse(GameSessionDecider.isUserStopped("com.game.b", "com.game.a"))
        assertFalse(GameSessionDecider.isUserStopped("com.launcher", "com.game.a"))
    }

    @Test
    fun withNoVetoNothingIsSuppressed() {
        assertFalse(GameSessionDecider.isUserStopped("com.game.a", null))
        assertFalse(GameSessionDecider.isUserStopped(null, "com.game.a"))
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
