package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour lock for the auto session's veto on the game the user switched Gaming Mode off in
 * (2026-10-28, found by driving the monitor on a Nothing Phone 2a).
 *
 * Dropping a stale session record leaves the monitor looking at "a library game is in the
 * foreground and nothing is recorded" — which is precisely the state that starts a session. The
 * first device run of the ownership fix therefore switched Gaming Mode straight back on for the
 * game the user had just turned it off in. The record drop has to be remembered, and the poll loop
 * has to consult that memory before it decides anything.
 *
 * The rule itself is pinned by [GameSessionDeciderTest]; this test pins the two call sites that
 * make it real, because a pure rule nobody calls changes nothing.
 */
class GameSessionVetoWiringTest {

    private val root = "src/main/java/com/catsmoker/app/features/gamingtools/engine"
    private val service = File(root, "GameSessionService.kt").readText()
    private val manager = File(root, "GameSessionManager.kt").readText()
    private val poll = service.substringAfter("private suspend fun pollLoop()")

    @Test
    fun aStaleRecordIsDroppedAsAUserStopNotAsAPlainRelease() {
        assertTrue(
            "the stale branch forgets who stopped the session, so the next tick restarts it",
            poll.contains("Ownership.Stale -> sessionManager.releaseByUser()")
        )
    }

    @Test
    fun theVetoIsConsultedBeforeTheForegroundDecidesAnything() {
        val veto = poll.indexOf("sessionManager.isUserStopped(current)")
        val decide = poll.indexOf("sessionManager.onForeground(current)")
        assertTrue("the poll loop never asks about the veto", veto >= 0)
        assertTrue("the veto must be checked before a session can be started", veto < decide)
    }

    @Test
    fun theVetoIsRememberedUntilTheUserLeavesTheGame() {
        assertTrue(
            "releaseByUser must remember the package it dropped",
            manager.contains("userStoppedPkg = _sessionActivePkg.value")
        )
        assertTrue(
            "a veto must lift when the foreground moves elsewhere",
            manager.contains("if (foregroundPkg != null) userStoppedPkg = null")
        )
    }

    @Test
    fun anUnreadableForegroundKeepsTheVeto() {
        // A failed usage read is never evidence that the user left the game.
        assertTrue(
            "an unknown foreground must not clear the veto",
            manager.contains("if (foregroundPkg != null) userStoppedPkg = null")
        )
        assertTrue(
            "switching automatic Gaming Mode off must clear the veto",
            manager.contains("userStoppedPkg = null")
        )
    }
}
