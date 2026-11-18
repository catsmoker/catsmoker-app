package com.catsmoker.app.screens.gaming

import org.junit.Assert.*
import org.junit.Test

class GameLibraryOrderingTest {
    private data class Game(val pkg: String, val label: String)
    private val games = ('A'..'M').map { Game("com.game.$it", it.toString()) }

    @Test fun selectedOutsidePreviewReplacesOnlyFinalSlotWithoutDuplicates() {
        val preview = GameLibraryOrdering.preview(games, "com.game.M", 8) { it.pkg }
        assertEquals(games.take(7) + games.last(), preview)
        assertEquals(8, preview.map { it.pkg }.distinct().size)
        assertEquals(games.take(8), GameLibraryOrdering.preview(games, null, 8) { it.pkg })
    }

    @Test fun selectedInsidePreviewAndMissingSelectionDoNotChangeNormalOrder() {
        assertEquals(games.take(8), GameLibraryOrdering.preview(games, "com.game.B", 8) { it.pkg })
        assertEquals(games.take(8), GameLibraryOrdering.preview(games, "uninstalled", 8) { it.pkg })
        assertEquals(games.take(3), GameLibraryOrdering.preview(games.take(3), "com.game.C", 8) { it.pkg })
    }

    @Test fun expandedOrderIsStableDistinctAndUsesRecencyThenLabelThenPackage() {
        val rank = listOf("com.game.M", "com.game.B", "uninstalled")
        val ordered = GameLibraryOrdering.ordered(games.reversed() + games, rank, { it.pkg }, { it.label })
        assertEquals(listOf(games.last(), games[1]) + games.filter { it.pkg !in rank }, ordered)
        assertEquals(games.size, ordered.map { it.pkg }.distinct().size)
        assertEquals(ordered, GameLibraryOrdering.ordered(games.shuffled(), rank, { it.pkg }, { it.label }))
    }

    @Test fun currentVisitSnapshotDoesNotChangeWhenNewSelectionsAreRecorded() {
        val disk = mutableMapOf<String, String>()
        val store = GameSettingsRepository({ disk[it] }, { k, v -> if (v == null) disk.remove(k) else disk[k] = v }, GameSettings())
        val visitRecents = store.recentPackages()
        val current = GameLibraryOrdering.ordered(games, visitRecents, { it.pkg }, { it.label })
        store.toggle("com.game.M")
        assertEquals(current, GameLibraryOrdering.ordered(games, visitRecents, { it.pkg }, { it.label }))
        assertEquals(games.last(), GameLibraryOrdering.ordered(games, store.recentPackages(), { it.pkg }, { it.label }).first())
    }

    @Test fun sameLabelsHavePackageTieBreakAndInstallRemovalArePredictable() {
        val tied = listOf(Game("com.game.b", "same"), Game("com.game.a", "Same"))
        assertEquals(listOf("com.game.a", "com.game.b"),
            GameLibraryOrdering.ordered(tied, emptyList(), { it.pkg }, { it.label }).map { it.pkg })
        assertEquals(emptyList<Game>(), GameLibraryOrdering.preview(emptyList<Game>(), null, 8) { it.pkg })
    }

    @Test fun collapsedSearchKeepsTheSelectedGameWithoutDuplicatingOrDroppingMatchesUnnecessarily() {
        val picked = games.last()
        assertEquals(games.take(7) + picked,
            GameLibraryOrdering.preview(games.take(10), picked.pkg, 8, picked) { it.pkg })
        assertEquals(games.take(2) + picked,
            GameLibraryOrdering.preview(games.take(2), picked.pkg, 8, picked) { it.pkg })
        assertEquals(listOf(picked),
            GameLibraryOrdering.preview(emptyList(), picked.pkg, 8, picked) { it.pkg })
        assertEquals(games.take(8),
            GameLibraryOrdering.preview(games.take(10), null, 8, picked) { it.pkg })
    }
}
