package com.catsmoker.app.screens.editgamefiles.storage

import com.catsmoker.app.screens.editgamefiles.model.ConfigScalar

/** Strict JSON reader retaining only top-level primitive spans; nested names cannot shadow settings. */
internal class ConfigJsonSpans(private val text: String) {
    data class Entry(val id: String, val start: Int, val end: Int, val value: String, val scalar: ConfigScalar?)
    private var pos = if (text.startsWith('\uFEFF')) 1 else 0
    private val entries = mutableListOf<Entry>()

    fun read(): List<Entry> {
        whitespace()
        require(text.getOrNull(pos) == '{') { "Expected a JSON object." }
        value(0)
        whitespace()
        require(pos == text.length) { "Unexpected data after JSON." }
        return entries
    }

    private fun value(depth: Int) {
        require(depth <= 64) { "JSON nesting is too deep." }
        whitespace()
        when (text.getOrNull(pos)) {
            '{' -> {
                pos++
                whitespace()
                if (consume('}')) return
                do {
                    whitespace()
                    val name = string()
                    whitespace()
                    require(consume(':')) { "Expected JSON colon." }
                    whitespace()
                    val start = pos
                    value(depth + 1)
                    if (depth == 0) {
                        val raw = text.substring(start, pos)
                        // JSON strings are deliberately not writable in the Terraria schema.
                        entries += Entry(name, start, pos, raw, EditableConfigDocument.scalarOf(raw))
                    }
                    whitespace()
                    if (consume('}')) return
                    require(consume(',')) { "Expected JSON comma." }
                } while (true)
            }
            '[' -> {
                pos++
                whitespace()
                if (consume(']')) return
                do {
                    value(depth + 1)
                    whitespace()
                    if (consume(']')) return
                    require(consume(',')) { "Expected JSON array comma." }
                } while (true)
            }
            '"' -> string()
            else -> {
                val match = Regex("(?:true|false|null|-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)")
                    .find(text, pos)
                require(match != null && match.range.first == pos) { "Invalid JSON value." }
                pos = match.range.last + 1
            }
        }
    }

    private fun string(): String {
        require(consume('"')) { "Expected JSON string." }
        val result = StringBuilder()
        while (pos < text.length) {
            val c = text[pos++]
            when {
                c == '"' -> return result.toString()
                c == '\\' -> {
                    require(pos < text.length)
                    when (val escaped = text[pos++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            require(pos + 4 <= text.length)
                            val code = text.substring(pos, pos + 4)
                            require(code.matches(Regex("[0-9a-fA-F]{4}")))
                            result.append(code.toInt(16).toChar())
                            pos += 4
                        }
                        else -> throw IllegalArgumentException("Invalid JSON escape.")
                    }
                }
                c < ' ' -> throw IllegalArgumentException("Control character in JSON string.")
                else -> result.append(c)
            }
        }
        throw IllegalArgumentException("Unterminated JSON string.")
    }

    private fun whitespace() { while (text.getOrNull(pos) in listOf(' ', '\t', '\r', '\n')) pos++ }
    private fun consume(c: Char): Boolean = if (text.getOrNull(pos) == c) { pos++; true } else false
}
