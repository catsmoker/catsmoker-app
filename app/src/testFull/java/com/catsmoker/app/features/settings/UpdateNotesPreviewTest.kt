package com.catsmoker.app.features.settings

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotesPreviewTest {

    @Test
    fun classifiesHeaderLinesCorrectly() {
        assertEquals(MarkdownLine.Header(1, "Top Level"), classifyMarkdownLine("# Top Level"))
        assertEquals(MarkdownLine.Header(2, "Sub Section"), classifyMarkdownLine("## Sub Section"))
        assertEquals(MarkdownLine.Header(3, "Feature Notes"), classifyMarkdownLine("### Feature Notes"))
        assertEquals(MarkdownLine.Header(4, "Deep Detail"), classifyMarkdownLine("#### Deep Detail"))
    }

    @Test
    fun classifiesDividersCorrectly() {
        assertEquals(MarkdownLine.Divider, classifyMarkdownLine("---"))
        assertEquals(MarkdownLine.Divider, classifyMarkdownLine("--------"))
        assertEquals(MarkdownLine.Divider, classifyMarkdownLine("***"))
        assertEquals(MarkdownLine.Divider, classifyMarkdownLine("___"))
    }

    @Test
    fun classifiesBulletPointsCorrectly() {
        assertEquals(MarkdownLine.Bullet("first point"), classifyMarkdownLine("• first point"))
        assertEquals(MarkdownLine.Bullet("second point"), classifyMarkdownLine("- second point"))
        assertEquals(MarkdownLine.Bullet("third point"), classifyMarkdownLine("* third point"))
        assertEquals(MarkdownLine.Bullet("fourth point"), classifyMarkdownLine("+ fourth point"))
    }

    @Test
    fun classifiesQuotesCorrectly() {
        assertEquals(MarkdownLine.Blockquote("Important warning"), classifyMarkdownLine("> Important warning"))
        assertEquals(MarkdownLine.Blockquote("Note text"), classifyMarkdownLine("│ Note text"))
    }

    @Test
    fun classifiesBlankAndParagraphCorrectly() {
        assertEquals(MarkdownLine.Blank, classifyMarkdownLine(""))
        assertEquals(MarkdownLine.Blank, classifyMarkdownLine("   "))
        assertEquals(MarkdownLine.Paragraph("Just some text."), classifyMarkdownLine("Just some text."))
    }

    @Test
    fun parsesInlineMarkdownStyles() {
        val annotated = parseInlineMarkdown("Check **bold** and `code` and *italic* and [link](https://example.com)!", Color.Red)
        assertEquals("Check bold and code and italic and link!", annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}
