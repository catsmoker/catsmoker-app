package com.catsmoker.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownLine {
    data class Header(val level: Int, val text: String) : MarkdownLine()
    data class Bullet(val text: String) : MarkdownLine()
    /** Ordered/numbered list item; the leading marker is stripped, like [Bullet]. */
    data class Numbered(val text: String) : MarkdownLine()
    /** Alias for [Numbered], kept for callers that name ordered lists differently. */
    data class Ordered(val text: String) : MarkdownLine()
    /** Alias for [Numbered]. */
    data class NumberedList(val text: String) : MarkdownLine()
    /** Alias for [Numbered]. */
    data class OrderedList(val text: String) : MarkdownLine()
    data class Blockquote(val text: String) : MarkdownLine()
    data object Divider : MarkdownLine()
    data class Paragraph(val text: String) : MarkdownLine()
    data class CodeBlock(val text: String) : MarkdownLine()
    /** Alias for [CodeBlock]. */
    data class Code(val text: String) : MarkdownLine()
    /** Alias for [CodeBlock]. */
    data class CodeFence(val text: String) : MarkdownLine()
    /** Alias for [CodeBlock]. */
    data class Pre(val text: String) : MarkdownLine()
    data class Table(val text: String) : MarkdownLine()
    /** Alias for [Table]. */
    data class TableRow(val text: String) : MarkdownLine()
    /** Alias for [Table]. */
    data class MarkdownTable(val text: String) : MarkdownLine()
    data object Blank : MarkdownLine()
}

/**
 * Classifies a single markdown line for structured layout rendering.
 */
fun classifyMarkdownLine(line: String): MarkdownLine {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return MarkdownLine.Blank
    if (trimmed.all { it == '-' || it == '*' || it == '_' } && trimmed.length >= 3) {
        return MarkdownLine.Divider
    }
    if (trimmed.startsWith("#### ")) {
        return MarkdownLine.Header(4, trimmed.removePrefix("#### ").trim())
    }
    if (trimmed.startsWith("### ")) {
        return MarkdownLine.Header(3, trimmed.removePrefix("### ").trim())
    }
    if (trimmed.startsWith("## ")) {
        return MarkdownLine.Header(2, trimmed.removePrefix("## ").trim())
    }
    if (trimmed.startsWith("# ")) {
        return MarkdownLine.Header(1, trimmed.removePrefix("# ").trim())
    }
    if (trimmed.startsWith(">") || trimmed.startsWith("│")) {
        return MarkdownLine.Blockquote(trimmed.removePrefix(">").removePrefix("│").trim())
    }
    if (trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
        return MarkdownLine.Bullet(trimmed.substring(2).trim())
    }
    // Numbered lists: "1. text" or "1) text".
    val numberedMatch = Regex("^\\d+[.)]\\s+(.*)$").find(trimmed)
    if (numberedMatch != null) {
        return MarkdownLine.Numbered(numberedMatch.groupValues[1].trim())
    }
    // Fenced code blocks.
    if (trimmed.startsWith("```")) {
        return MarkdownLine.CodeBlock(trimmed.removePrefix("```").trim())
    }
    // Tables (GFM): a row starting with a pipe.
    if (trimmed.startsWith("|")) {
        return MarkdownLine.Table(trimmed)
    }
    return MarkdownLine.Paragraph(trimmed)
}

/**
 * Parses inline formatting: `**bold**`, `*italic*`, `` `code` ``, and `[label](url)`.
 */
fun parseInlineMarkdown(text: String, primaryColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            // Check bold: **text**
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                    continue
                }
            }
            // Check inline code: `code`
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }
            // Check italic: *italic*
            if (text[i] == '*' && (i + 1 == text.length || text[i + 1] != '*')) {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && (end + 1 == text.length || text[end + 1] != '*')) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }
            // Check link: [label](url)
            if (text[i] == '[') {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val label = text.substring(i + 1, closeBracket)
                        pushStyle(SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline))
                        append(label)
                        pop()
                        i = closeParen + 1
                        continue
                    }
                }
            }
            append(text[i])
            i++
        }
    }
}

/**
 * Scrollable, styled preview of release notes formatting headings, dividers,
 * bullet/numbered lists, blockquotes, inline bold/code, fenced code blocks and
 * GFM tables. Tables scroll horizontally so they stay readable on narrow screens.
 */
@Composable
fun UpdateNotesPreview(
    notes: String,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val blocks = remember(notes) { parseNoteBlocks(notes) }
    val horizontalScroll = rememberScrollState()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is NoteBlock.Blank -> Spacer(modifier = Modifier.height(4.dp))
                is NoteBlock.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                is NoteBlock.Header -> {
                    val (style, topPadding) = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge to 12.dp
                        2 -> MaterialTheme.typography.titleMedium to 10.dp
                        3 -> MaterialTheme.typography.titleSmall to 8.dp
                        else -> MaterialTheme.typography.labelLarge to 6.dp
                    }
                    Text(
                        text = parseInlineMarkdown(block.text, primaryColor),
                        style = style,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = topPadding, bottom = 2.dp)
                    )
                }
                is NoteBlock.Paragraph -> Text(
                    text = parseInlineMarkdown(block.text, primaryColor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                is NoteBlock.BulletList -> {
                    for (item in block.items) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 2.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = parseInlineMarkdown(item, primaryColor),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                is NoteBlock.NumberedList -> {
                    block.items.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 2.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${idx + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = parseInlineMarkdown(item, primaryColor),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                is NoteBlock.Blockquote -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .background(color = primaryColor, shape = RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, primaryColor),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is NoteBlock.CodeBlock -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                is NoteBlock.Table -> {
                    // Horizontal scroll keeps wide tables readable on narrow screens
                    // instead of clipping or squashing columns.
                    Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                        Column {
                            // header
                            Row {
                                for (cell in block.headers) {
                                    Text(
                                        text = parseInlineMarkdown(cell, primaryColor),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .widthIn(min = 80.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            for (row in block.rows) {
                                Row {
                                    row.forEach { cell ->
                                        Text(
                                            text = parseInlineMarkdown(cell, primaryColor),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .widthIn(min = 80.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class NoteBlock {
    data class Header(val level: Int, val text: String) : NoteBlock()
    data class Paragraph(val text: String) : NoteBlock()
    data class BulletList(val items: List<String>) : NoteBlock()
    data class NumberedList(val items: List<String>) : NoteBlock()
    data class Blockquote(val text: String) : NoteBlock()
    data object Divider : NoteBlock()
    data class CodeBlock(val language: String, val code: String) : NoteBlock()
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val aligns: List<String?>
    ) : NoteBlock()
    data object Blank : NoteBlock()
}

private fun parseNoteBlocks(notes: String): List<NoteBlock> {
    val lines = notes.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<NoteBlock>()
    var i = 0
    val para = StringBuilder()
    fun flushPara() {
        if (para.isNotEmpty()) {
            blocks.add(NoteBlock.Paragraph(para.toString().trim()))
            para.setLength(0)
        }
    }
    while (i < lines.size) {
        val trimmed = lines[i].trim()
        if (trimmed.isEmpty()) {
            flushPara()
            i++
            continue
        }
        if (trimmed.startsWith("```")) {
            flushPara()
            val lang = trimmed.removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                code.append(lines[i]).append('\n')
                i++
            }
            i++ // closing fence
            blocks.add(NoteBlock.CodeBlock(lang, code.toString().trimEnd('\n')))
            continue
        }
        if (trimmed.all { it == '-' || it == '*' || it == '_' } && trimmed.length >= 3) {
            flushPara()
            blocks.add(NoteBlock.Divider)
            i++
            continue
        }
        val h = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
        if (h != null) {
            flushPara()
            blocks.add(NoteBlock.Header(h.groupValues[1].length, h.groupValues[2]))
            i++
            continue
        }
        if (trimmed.startsWith(">")) {
            flushPara()
            val q = StringBuilder()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                if (q.isNotEmpty()) q.append(' ')
                q.append(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(NoteBlock.Blockquote(q.toString()))
            continue
        }
        if (trimmed.startsWith("|") && i + 1 < lines.size && isAlignmentRow(lines[i + 1].trim())) {
            flushPara()
            val headers = splitRow(trimmed)
            val aligns = splitRow(lines[i + 1].trim()).map { alignmentOf(it) }
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                rows.add(splitRow(lines[i].trim()))
                i++
            }
            blocks.add(NoteBlock.Table(headers, rows, aligns))
            continue
        }
        val bullet = Regex("^[-*+]\\s+(.*)$").find(trimmed)
        if (bullet != null) {
            flushPara()
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val t = lines[i].trim()
                val m = Regex("^[-*+]\\s+(.*)$").find(t)
                if (m == null) break
                items.add(m.groupValues[1])
                i++
            }
            blocks.add(NoteBlock.BulletList(items))
            continue
        }
        val numbered = Regex("^\\d+[.)]\\s+(.*)$").find(trimmed)
        if (numbered != null) {
            flushPara()
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val t = lines[i].trim()
                val m = Regex("^\\d+[.)]\\s+(.*)$").find(t)
                if (m == null) break
                items.add(m.groupValues[1])
                i++
            }
            blocks.add(NoteBlock.NumberedList(items))
            continue
        }
        para.append(trimmed).append(' ')
        i++
    }
    flushPara()
    return blocks
}

private fun isAlignmentRow(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.startsWith("|")) return false
    val cells = splitRow(trimmed)
    return cells.isNotEmpty() && cells.all { Regex("^:?-{1,}:?$").matches(it.trim()) }
}

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

private fun alignmentOf(spec: String): String? {
    val cell = spec.trim()
    return when {
        cell.startsWith(":") && cell.endsWith(":") -> "center"
        cell.endsWith(":") -> "right"
        cell.startsWith(":") -> "left"
        else -> null
    }
}
