package com.catsmoker.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
    data class Blockquote(val text: String) : MarkdownLine()
    data object Divider : MarkdownLine()
    data class Paragraph(val text: String) : MarkdownLine()
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
 * bullet lists, blockquotes, and inline bold/code.
 */
@Composable
fun UpdateNotesPreview(
    notes: String,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val lines = remember(notes) { notes.lines() }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (rawLine in lines) {
            when (val line = classifyMarkdownLine(rawLine)) {
                is MarkdownLine.Blank -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                is MarkdownLine.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                is MarkdownLine.Header -> {
                    val (style, topPadding) = when (line.level) {
                        1 -> MaterialTheme.typography.titleLarge to 12.dp
                        2 -> MaterialTheme.typography.titleMedium to 10.dp
                        3 -> MaterialTheme.typography.titleSmall to 8.dp
                        else -> MaterialTheme.typography.labelLarge to 6.dp
                    }
                    Text(
                        text = parseInlineMarkdown(line.text, primaryColor),
                        style = style,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = topPadding, bottom = 2.dp)
                    )
                }
                is MarkdownLine.Bullet -> {
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
                            text = parseInlineMarkdown(line.text, primaryColor),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
                is MarkdownLine.Blockquote -> {
                    Surface(
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
                                    .background(
                                        color = primaryColor,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = parseInlineMarkdown(line.text, primaryColor),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is MarkdownLine.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(line.text, primaryColor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
