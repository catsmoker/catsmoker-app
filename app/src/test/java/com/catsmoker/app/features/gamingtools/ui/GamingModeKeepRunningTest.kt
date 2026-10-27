package com.catsmoker.app.features.gamingtools.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keep-running control is a picker over a persisted set, not a switch. Its rows must always
 * follow the set the ViewModel passes in: adding a pick adds a row, removing one removes it, and an
 * unknown package still appears instead of silently disappearing.
 */
class GamingModeKeepRunningTest {

    @Test
    fun emptySetHasNoRows() {
        assertTrue(gamingModeKeptApps(emptySet(), emptyMap()).isEmpty())
    }

    @Test
    fun rowsFollowPickerNamesAndPackageFallbacks() {
        val rows = gamingModeKeptApps(
            picked = setOf("com.example.known", "com.example.unknown"),
            nameByPackage = mapOf("com.example.known" to "Known app")
        )

        assertEquals(
            listOf(
                GamingModeKeptApp("Known app", "com.example.known"),
                GamingModeKeptApp("com.example.unknown", "com.example.unknown")
            ),
            rows
        )
    }

    @Test
    fun addingAndRemovingAPickChangesOnlyThatRow() {
        val before = gamingModeKeptApps(
            picked = setOf("com.example.a"),
            nameByPackage = mapOf("com.example.a" to "A", "com.example.b" to "B")
        )
        val afterAdd = gamingModeKeptApps(
            picked = setOf("com.example.a", "com.example.b"),
            nameByPackage = mapOf("com.example.a" to "A", "com.example.b" to "B")
        )
        val afterRemove = gamingModeKeptApps(
            picked = setOf("com.example.b"),
            nameByPackage = mapOf("com.example.a" to "A", "com.example.b" to "B")
        )

        assertEquals(listOf(GamingModeKeptApp("A", "com.example.a")), before)
        assertEquals(
            listOf(
                GamingModeKeptApp("A", "com.example.a"),
                GamingModeKeptApp("B", "com.example.b")
            ),
            afterAdd
        )
        assertEquals(listOf(GamingModeKeptApp("B", "com.example.b")), afterRemove)
    }

    @Test
    fun countIsZeroForAnEmptyList() {
        assertEquals(0, GamingModeKeepRunning.count(emptySet()))
    }

    @Test
    fun countReflectsEverySavedPick() {
        assertEquals(
            3,
            GamingModeKeepRunning.count(setOf("com.example.a", "com.example.b", "com.example.c"))
        )
    }

    @Test
    fun countNeverCountsADuplicatePackageIdTwice() {
        assertEquals(
            2,
            GamingModeKeepRunning.count(listOf("com.example.a", "com.example.a", "com.example.b"))
        )
    }

    @Test
    fun countFollowsAddsAndRemovesImmediately() {
        val before = setOf("com.example.a")
        val afterAdd = before + "com.example.b"
        val afterRemove = afterAdd - "com.example.a"

        assertEquals(1, GamingModeKeepRunning.count(before))
        assertEquals(2, GamingModeKeepRunning.count(afterAdd))
        assertEquals(1, GamingModeKeepRunning.count(afterRemove))
    }

    @Test
    fun listStartsCollapsed() {
        assertFalse(GamingModeKeepRunning.DEFAULT_EXPANDED)
    }

    @Test
    fun togglingOnlyFlipsTheDisclosureState() {
        val expanded = GamingModeKeepRunning.toggle(GamingModeKeepRunning.DEFAULT_EXPANDED)

        assertTrue(expanded)
        assertFalse(GamingModeKeepRunning.toggle(expanded))
    }

    @Test
    fun collapsedSummaryShowsTheFirstFewNamesAndTheRemainder() {
        val rows = rows(5)

        val summary = GamingModeKeepRunning.preview(rows)

        assertEquals(listOf("App 1", "App 2", "App 3"), summary.labels)
        assertEquals(2, summary.hiddenCount)
    }

    @Test
    fun shortListNeedsNoRemainderLine() {
        val summary = GamingModeKeepRunning.preview(rows(2))

        assertEquals(listOf("App 1", "App 2"), summary.labels)
        assertEquals(0, summary.hiddenCount)
    }

    @Test
    fun emptyPreviewHasNoNames() {
        val summary = GamingModeKeepRunning.preview(emptyList())

        assertTrue(summary.labels.isEmpty())
        assertEquals(0, summary.hiddenCount)
    }

    private fun rows(count: Int): List<GamingModeKeptApp> =
        (1..count).map { GamingModeKeptApp(label = "App $it", packageName = "com.example.$it") }
}
