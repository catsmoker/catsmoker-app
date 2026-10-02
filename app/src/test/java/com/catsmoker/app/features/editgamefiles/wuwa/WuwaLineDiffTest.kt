package com.catsmoker.app.features.editgamefiles.wuwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the INI line-diff against
 * `reference/gamingtools/WuWa-Config-Android-main/.../util/LineDiff.kt`, read in full before
 * this test was written: LCS backtrack (context / added / removed with old/new line numbers),
 * added+removed+unchanged summary, and the tie-break (addition preferred on equal scores).
 */
class WuwaLineDiffTest {

    @Test
    fun identicalTextsAreAllContext() {
        val text = "[SystemSettings]\nr.Foo=1"
        val result = WuwaLineDiff.compute(text, text)
        assertEquals(0, result.summary.added)
        assertEquals(0, result.summary.removed)
        assertEquals(2, result.summary.unchanged)
        assertTrue(result.lines.all { it.kind == WuwaDiffKind.CONTEXT })
    }

    @Test
    fun changedValueIsARemovePlusAnAdd() {
        val result = WuwaLineDiff.compute("r.Foo=1", "r.Foo=2")
        assertEquals(1, result.summary.added)
        assertEquals(1, result.summary.removed)
        assertEquals(0, result.summary.unchanged)
        assertEquals(
            listOf(WuwaDiffKind.REMOVED, WuwaDiffKind.ADDED),
            result.lines.map { it.kind }
        )
        assertEquals("r.Foo=1", result.lines[0].text)
        assertEquals("r.Foo=2", result.lines[1].text)
    }

    @Test
    fun surroundingContextKeepsItsLineNumbers() {
        val oldText = "a\nb\nc"
        val newText = "a\nB\nc"
        val result = WuwaLineDiff.compute(oldText, newText)
        val first = result.lines.first()
        val last = result.lines.last()
        assertEquals(WuwaDiffKind.CONTEXT, first.kind)
        assertEquals(1 to 1, (first.oldLineNumber ?: -1) to (first.newLineNumber ?: -1))
        assertEquals(WuwaDiffKind.CONTEXT, last.kind)
        assertEquals(3 to 3, (last.oldLineNumber ?: -1) to (last.newLineNumber ?: -1))
    }

    @Test
    fun appendedLinesAreAddsWithNewNumbersOnly() {
        val result = WuwaLineDiff.compute("a", "a\nb")
        val added = result.lines.filter { it.kind == WuwaDiffKind.ADDED }
        assertEquals(1, added.size)
        assertEquals(null, added[0].oldLineNumber)
        assertEquals(2, added[0].newLineNumber)
    }

    @Test
    fun emptyOldTextIsAllAdded() {
        val result = WuwaLineDiff.compute("", "x\ny")
        // "".split('\n') is one empty line, which the new text replaces.
        assertEquals(2, result.summary.added)
        assertEquals(1, result.summary.removed)
    }

    @Test
    fun summaryTalliesAnIniEdit() {
        val oldText = "[SystemSettings]\nr.Foo=1\nr.Bar=2"
        val newText = "[SystemSettings]\nr.Foo=1\nr.Bar=3\nr.Baz=4"
        val result = WuwaLineDiff.compute(oldText, newText)
        assertEquals(2, result.summary.added)
        assertEquals(1, result.summary.removed)
        assertEquals(2, result.summary.unchanged)
    }
}
