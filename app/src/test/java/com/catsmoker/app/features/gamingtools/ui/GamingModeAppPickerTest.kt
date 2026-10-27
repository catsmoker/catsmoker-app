package com.catsmoker.app.features.gamingtools.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared app picker is a control over a persisted set, not a switch. Its rows must always
 * follow the set the ViewModel passes in: adding a pick adds a row, removing one removes it, and an
 * unknown package still appears instead of silently disappearing.
 */
class GamingModeAppPickerTest {

    @Test
    fun emptySetHasNoRows() {
        assertTrue(gamingModePickedApps(emptySet(), emptyMap()).isEmpty())
    }

    @Test
    fun rowsFollowPickerNamesAndPackageFallbacks() {
        val rows = gamingModePickedApps(
            picked = setOf("com.example.known", "com.example.unknown"),
            nameByPackage = mapOf("com.example.known" to "Known app")
        )

        assertEquals(
            listOf(
                GamingModePickedApp("Known app", "com.example.known"),
                GamingModePickedApp("com.example.unknown", "com.example.unknown")
            ),
            rows
        )
    }

    @Test
    fun addingAndRemovingAPickChangesOnlyThatRow() {
        val before = gamingModePickedApps(
            picked = setOf("com.example.a"),
            nameByPackage = mapOf("com.example.a" to "A", "com.example.b" to "B")
        )
        val afterAdd = gamingModePickedApps(
            picked = setOf("com.example.a", "com.example.b"),
            nameByPackage = mapOf("com.example.a" to "A", "com.example.b" to "B")
        )
        val afterRemove = gamingModePickedApps(
            picked = setOf("com.example.b"),
            nameByPackage = mapOf("com.example.a" to "A", "com.example.b" to "B")
        )

        assertEquals(listOf(GamingModePickedApp("A", "com.example.a")), before)
        assertEquals(
            listOf(
                GamingModePickedApp("A", "com.example.a"),
                GamingModePickedApp("B", "com.example.b")
            ),
            afterAdd
        )
        assertEquals(listOf(GamingModePickedApp("B", "com.example.b")), afterRemove)
    }

    @Test
    fun countIsZeroForAnEmptyList() {
        assertEquals(0, GamingModeAppPicker.count(emptySet()))
    }

    @Test
    fun countReflectsEverySavedPick() {
        assertEquals(
            3,
            GamingModeAppPicker.count(setOf("com.example.a", "com.example.b", "com.example.c"))
        )
    }

    @Test
    fun countNeverCountsADuplicatePackageIdTwice() {
        assertEquals(
            2,
            GamingModeAppPicker.count(listOf("com.example.a", "com.example.a", "com.example.b"))
        )
    }

    @Test
    fun countFollowsAddsAndRemovesImmediately() {
        val before = setOf("com.example.a")
        val afterAdd = before + "com.example.b"
        val afterRemove = afterAdd - "com.example.a"

        assertEquals(1, GamingModeAppPicker.count(before))
        assertEquals(2, GamingModeAppPicker.count(afterAdd))
        assertEquals(1, GamingModeAppPicker.count(afterRemove))
    }

    @Test
    fun listStartsCollapsed() {
        assertFalse(GamingModeAppPicker.DEFAULT_EXPANDED)
    }

    @Test
    fun togglingOnlyFlipsTheDisclosureState() {
        val expanded = GamingModeAppPicker.toggle(GamingModeAppPicker.DEFAULT_EXPANDED)

        assertTrue(expanded)
        assertFalse(GamingModeAppPicker.toggle(expanded))
    }

    @Test
    fun collapsedSummaryShowsTheFirstFewNamesAndTheRemainder() {
        val rows = rows(5)

        val summary = GamingModeAppPicker.preview(rows)

        assertEquals(listOf("App 1", "App 2", "App 3"), summary.labels)
        assertEquals(2, summary.hiddenCount)
    }

    @Test
    fun shortListNeedsNoRemainderLine() {
        val summary = GamingModeAppPicker.preview(rows(2))

        assertEquals(listOf("App 1", "App 2"), summary.labels)
        assertEquals(0, summary.hiddenCount)
    }

    @Test
    fun emptyPreviewHasNoNames() {
        val summary = GamingModeAppPicker.preview(emptyList())

        assertTrue(summary.labels.isEmpty())
        assertEquals(0, summary.hiddenCount)
    }

    private fun rows(count: Int): List<GamingModePickedApp> =
        (1..count).map { GamingModePickedApp(label = "App $it", packageName = "com.example.$it") }
}
