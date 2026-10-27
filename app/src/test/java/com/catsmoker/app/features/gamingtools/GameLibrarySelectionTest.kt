package com.catsmoker.app.features.gamingtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the fix for the Remove button.
 *
 * The library is fed by two sources, and auto-discovered games never existed in `user_games`.
 * Removing one therefore has to write the exclusion set the discovery pass reads; editing only the
 * user set left the game to be re-added on the next refresh, which is exactly what the user saw.
 */
class GameLibrarySelectionTest {

    @Test
    fun removingAnAutoDiscoveredGameExcludesItEvenThoughItWasNeverPicked() {
        val after = GameLibrarySelection.remove(
            pkg = "com.example.auto",
            userGames = emptySet(),
            removedGames = emptySet()
        )
        assertEquals(emptySet<String>(), after.userGames)
        assertEquals(setOf("com.example.auto"), after.removedGames)
        assertFalse(
            GameLibrarySelection.isVisible(
                pkg = "com.example.auto",
                autoDiscovered = true,
                userGames = after.userGames,
                removedGames = after.removedGames
            )
        )
    }

    @Test
    fun removingAPickedGameClearsThePickAndExcludesIt() {
        val after = GameLibrarySelection.remove(
            pkg = "com.example.picked",
            userGames = setOf("com.example.picked", "com.example.other"),
            removedGames = emptySet()
        )
        assertEquals(setOf("com.example.other"), after.userGames)
        assertEquals(setOf("com.example.picked"), after.removedGames)
    }

    @Test
    fun addingARemovedGameAgainLiftsTheExclusion() {
        val after = GameLibrarySelection.add(
            pkg = "com.example.removed",
            userGames = emptySet(),
            removedGames = setOf("com.example.removed")
        )
        assertEquals(setOf("com.example.removed"), after.userGames)
        assertEquals(emptySet<String>(), after.removedGames)
        assertTrue(
            GameLibrarySelection.isVisible(
                pkg = "com.example.removed",
                autoDiscovered = true,
                userGames = after.userGames,
                removedGames = after.removedGames
            )
        )
    }

    @Test
    fun removalWinsOverUserSelectionUntilTheUserAddsItBack() {
        assertFalse(
            GameLibrarySelection.isVisible(
                pkg = "com.example.both",
                autoDiscovered = true,
                userGames = setOf("com.example.both"),
                removedGames = setOf("com.example.both")
            )
        )
    }

    @Test
    fun autoDiscoveredAndPickedGamesAreBothVisible() {
        assertTrue(
            GameLibrarySelection.isVisible(
                pkg = "com.example.auto",
                autoDiscovered = true,
                userGames = emptySet(),
                removedGames = emptySet()
            )
        )
        assertTrue(
            GameLibrarySelection.isVisible(
                pkg = "com.example.picked",
                autoDiscovered = false,
                userGames = setOf("com.example.picked"),
                removedGames = emptySet()
            )
        )
        assertFalse(
            GameLibrarySelection.isVisible(
                pkg = "com.example.plain",
                autoDiscovered = false,
                userGames = emptySet(),
                removedGames = emptySet()
            )
        )
    }

    @Test
    fun blankAndPaddedPackagesAreHandledWithoutInventingEntries() {
        assertEquals(
            GameLibrarySelection.Selection(emptySet(), emptySet()),
            GameLibrarySelection.add("   ", emptySet(), emptySet())
        )
        assertEquals(
            setOf("com.example.game"),
            GameLibrarySelection.add(" com.example.game ", emptySet(), emptySet()).userGames
        )
    }
}
