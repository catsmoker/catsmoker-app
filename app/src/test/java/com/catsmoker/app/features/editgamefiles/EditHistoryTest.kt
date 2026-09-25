package com.catsmoker.app.features.editgamefiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for the editor undo/redo tracker.
 *
 * Adapted from the reference HSR app's `BaseChangeManager` (read in full): history stacks with
 * a cap, a baseline the dirty flag compares against, and redo cleared by any new edit. Two
 * deliberate simplifications: snapshots are whole values compared with `equals` (the editor
 * models are data classes), so there is no per-field modified set; and the baseline resets
 * explicitly via [EditHistory.setBaseline] on load/apply/restore rather than by observing
 * writes the tracker cannot see.
 */
class EditHistoryTest {

    private data class Cfg(val fps: Int, val quality: Int)

    @Test
    fun freshHistoryIsCleanAndCannotUndoOrRedo() {
        val history = EditHistory<Cfg>()
        history.setBaseline(Cfg(60, 2))
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertFalse(history.isDirty(Cfg(60, 2)))
        assertNull(history.undo(Cfg(60, 2)))
        assertNull(history.redo(Cfg(60, 2)))
    }

    @Test
    fun pushThenUndoRestoresPriorValue() {
        val history = EditHistory<Cfg>()
        history.setBaseline(Cfg(60, 2))
        history.push(Cfg(60, 2))
        assertTrue(history.canUndo)
        assertEquals(Cfg(60, 2), history.undo(Cfg(90, 2)))
        assertTrue(history.canRedo)
        assertFalse(history.isDirty(Cfg(60, 2)))
    }

    @Test
    fun redoReappliesUndoneValue() {
        val history = EditHistory<Cfg>()
        history.setBaseline(Cfg(60, 2))
        history.push(Cfg(60, 2))
        history.undo(Cfg(90, 2))
        assertEquals(Cfg(90, 2), history.redo(Cfg(60, 2)))
        assertFalse(history.canRedo)
    }

    @Test
    fun newPushClearsRedo() {
        val history = EditHistory<Cfg>()
        history.setBaseline(Cfg(60, 2))
        history.push(Cfg(60, 2))
        history.undo(Cfg(90, 2))
        history.push(Cfg(90, 2))
        assertFalse(history.canRedo)
        assertNull(history.redo(Cfg(120, 2)))
    }

    @Test
    fun dirtyComparesAgainstBaseline() {
        val history = EditHistory<Cfg>()
        history.setBaseline(Cfg(60, 2))
        assertTrue(history.isDirty(Cfg(90, 2)))
        // Undoing back to the baseline value is clean again.
        history.push(Cfg(60, 2))
        history.undo(Cfg(90, 2))
        assertFalse(history.isDirty(Cfg(60, 2)))
    }

    @Test
    fun historyCapsAtFifty() {
        val history = EditHistory<Cfg>(maxSize = 3)
        history.setBaseline(Cfg(0, 0))
        history.push(Cfg(1, 0))
        history.push(Cfg(2, 0))
        history.push(Cfg(3, 0))
        history.push(Cfg(4, 0))
        // Oldest dropped: three undos reach back to Cfg(2,0), the fourth finds nothing.
        assertEquals(Cfg(4, 0), history.undo(Cfg(5, 0)))
        assertEquals(Cfg(3, 0), history.undo(Cfg(4, 0)))
        assertEquals(Cfg(2, 0), history.undo(Cfg(3, 0)))
        assertNull(history.undo(Cfg(2, 0)))
    }

    @Test
    fun setBaselineResetsEverything() {
        val history = EditHistory<Cfg>()
        history.setBaseline(Cfg(60, 2))
        history.push(Cfg(60, 2))
        history.setBaseline(Cfg(90, 3))
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertFalse(history.isDirty(Cfg(90, 3)))
    }
}
