package com.catsmoker.app.screens.editgamefiles.games.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Quality tab lists sixteen axes closed, one row each. What that row reports decides whether the
 * list is trustworthy: it has to say what is *selected for writing*, and fall back to the file only
 * when nothing is queued. Showing the file value after a change would make the list contradict the
 * deploy bar, which counts pending edits.
 */
class GridLadderRowTest {

    @Test
    fun aPendingChoiceWinsOverTheFile() {
        assertEquals("high", ladderRowValue(selected = "high", current = "medium"))
    }

    @Test
    fun withNothingQueuedTheFileValueIsShown() {
        assertEquals("medium", ladderRowValue(selected = null, current = "medium"))
    }

    @Test
    fun anAxisTheFileDoesNotCarryReportsNothing() {
        // Null is what makes the row render "not in the file yet" and disable its chips.
        assertNull(ladderRowValue(selected = null, current = null))
    }
}
