package com.catsmoker.app.features.gamingtools.tools.forcestop

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * State transitions behind the Gaming Mode keep list.
 *
 * The control inside the Gaming Mode card calls the same store as the old standalone card, so these
 * pin the two operations that control exposes: adding a pick, removing a pick with the same toggle,
 * and removing one explicitly. An unknown package is not invented, and a removal never disturbs the
 * other picks.
 */
class SuspendExemptStoreTest {

    @Test
    fun togglingAddsAPickAndKeepsTheRest() {
        assertEquals(
            setOf("com.example.a", "com.example.b"),
            SuspendExemptStore.toggled(setOf("com.example.a"), "com.example.b")
        )
    }

    @Test
    fun togglingAnExistingPickRemovesOnlyIt() {
        assertEquals(
            setOf("com.example.b"),
            SuspendExemptStore.toggled(setOf("com.example.a", "com.example.b"), "com.example.a")
        )
    }

    @Test
    fun togglingDoesNotMutateTheCallersSet() {
        val current = setOf("com.example.a")

        SuspendExemptStore.toggled(current, "com.example.b")

        assertEquals(setOf("com.example.a"), current)
    }

    @Test
    fun explicitRemovalKeepsTheOtherPicks() {
        assertEquals(
            setOf("com.example.b"),
            SuspendExemptStore.removed(setOf("com.example.a", "com.example.b"), "com.example.a")
        )
    }

    @Test
    fun removingAnAbsentPickIsANoop() {
        assertEquals(
            setOf("com.example.a"),
            SuspendExemptStore.removed(setOf("com.example.a"), "com.example.missing")
        )
    }
}
