package com.catsmoker.app.features.gamingtools.tools.forcestop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for the extra suspend list.
 *
 * The list holds user-picked packages frozen at Gaming Mode activation alongside the automatic
 * sweep. Two entries can never be honored, however picked: Catsmoker itself (freezing the
 * process that must later unsuspend everything would strand every other entry frozen) and the
 * active game (the thing being played). Both are filtered at use time rather than at pick
 * time, so the stored list stays exactly what the user chose.
 */
class SuspendListStoreTest {

    @Test
    fun blanksAndDuplicatesDropOut() {
        assertEquals(
            listOf("com.example.a"),
            SuspendListStore.filterTargets(
                setOf("com.example.a", "", "  ", "com.example.a"),
                activeGamePkg = null,
                selfPkg = "com.catsmoker.app"
            )
        )
    }

    @Test
    fun selfAndActiveGameAreNeverTargets() {
        assertTrue(
            SuspendListStore.filterTargets(
                setOf("com.catsmoker.app", "com.game.played"),
                activeGamePkg = "com.game.played",
                selfPkg = "com.catsmoker.app"
            ).isEmpty()
        )
    }

    @Test
    fun everythingElsePassesThroughSorted() {
        assertEquals(
            listOf("com.example.a", "com.example.b"),
            SuspendListStore.filterTargets(
                setOf("com.example.b", "com.example.a"),
                activeGamePkg = "com.other.game",
                selfPkg = "com.catsmoker.app"
            )
        )
    }

    @Test
    fun emptyStaysEmpty() {
        assertTrue(
            SuspendListStore.filterTargets(emptySet(), activeGamePkg = null, selfPkg = "x").isEmpty()
        )
    }
}
