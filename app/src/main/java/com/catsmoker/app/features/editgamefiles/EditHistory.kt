package com.catsmoker.app.features.editgamefiles

/**
 * Undo/redo tracker for one editor working copy.
 *
 * Adapted from the reference HSR app's `BaseChangeManager`
 * (`reference/gamingtools/hsrgraphicdroid-main/.../data/BaseChangeManager.kt`, read in full):
 * bounded history stacks, an explicit baseline, redo cleared by any new edit. Simplified for
 * this codebase: snapshots are whole values and dirtiness is `equals` against the baseline
 * (the editor models are data classes), so there is no per-field modified set. The baseline
 * resets explicitly — on load, apply and restore — because the tracker cannot observe writes
 * the game manager performs behind its back.
 *
 * Plain JVM, no coroutines: ViewModels drive it from `StateFlow.update` blocks.
 */
class EditHistory<T>(private val maxSize: Int = 50) {

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    private var baseline: T? = null
    private var hasBaseline = false

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** The loaded/applied value edits compare against; clears both stacks. */
    fun setBaseline(value: T) {
        baseline = value
        hasBaseline = true
        undoStack.clear()
        redoStack.clear()
    }

    /** Records the pre-edit value. Call with the value *before* applying the edit. */
    fun push(current: T) {
        undoStack.addLast(current)
        while (undoStack.size > maxSize) undoStack.removeFirst()
        redoStack.clear()
    }

    /** The value to restore, or null when there is nothing to undo. */
    fun undo(current: T): T? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        return undoStack.removeLast()
    }

    /** The value to restore, or null when there is nothing to redo. */
    fun redo(current: T): T? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current)
        while (undoStack.size > maxSize) undoStack.removeFirst()
        return redoStack.removeLast()
    }

    /** True when [current] differs from the baseline (or no baseline was ever set). */
    fun isDirty(current: T): Boolean {
        if (!hasBaseline) return true
        return baseline != current
    }
}
