package com.catsmoker.app.screens.editgamefiles.games.wutheringwaves

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Overlay only generated keys; keep the live game's unrelated sections, comments and arrays. */
object WuwaIniMerge {
    private data class Key(val section: String, val name: String, val element: String)
    private data class Entry(val key: Key, val line: String, val value: String)
    private val linePattern = Regex("[^\\r\\n]+(?:\\r\\n|\\n|\\r)?|\\r\\n|\\n|\\r")
    private val frameCeilings = setOf("t.maxfps", "frameratelimit")

    fun merge(existing: ByteArray?, generated: ByteArray): ByteArray {
        val overlay = decode(generated)
        val desired = linkedMapOf<Key, Entry>()
        var section = ""
        for (line in overlay.lineSequence()) {
            val text = line.trim().removePrefix("\uFEFF")
            if (text.startsWith("[") && text.endsWith("]")) section = text.lowercase(Locale.ROOT)
            else if (text.isNotEmpty() && !text.startsWith(";") && !text.startsWith("#")) {
                val entry = entry(section, line) ?: error("Generated INI contains an unsupported line.")
                require(section.isNotEmpty()) { "Generated INI has a setting outside a section." }
                desired[entry.key] = entry
            }
        }
        require(desired.isNotEmpty()) { "Generated INI has no settings." }
        if (existing == null || existing.isEmpty()) return generated
        val source = decode(existing)
        val newline = if ("\r\n" in source) "\r\n" else "\n"
        val seen = mutableSetOf<Key>()
        section = ""
        val output = StringBuilder()
        for (match in linePattern.findAll(source)) {
            val raw = match.value
            val line = raw.trimEnd('\r', '\n')
            val text = line.trim().removePrefix("\uFEFF")
            if (text.startsWith("[") && text.endsWith("]")) section = text.lowercase(Locale.ROOT)
            val current = entry(section, line)
            val replacement = current?.let { desired[it.key] }
            if (current == null || replacement == null) {
                output.append(raw)
            } else {
                seen.add(current.key)
                // Raising a game's ceiling is allowed; lowering an existing/unlimited one is not.
                val fps = current.key.element.ifEmpty { current.key.name }
                val oldValue = current.value.toDoubleOrNull()
                val newValue = replacement.value.toDoubleOrNull()
                if (fps in frameCeilings && (oldValue == null || oldValue <= 0 ||
                        newValue == null || newValue < oldValue)) {
                    output.append(raw)
                } else {
                    output.append(replacement.line).append(raw.substring(line.length))
                }
            }
        }
        require(section.isNotEmpty()) { "The current file is not a supported section-based INI." }
        val pending = desired.filterKeys { it !in seen }.values.groupBy { it.key.section }
        for ((scope, entries) in pending) {
            if (output.isNotEmpty() && output.last() != '\n' && output.last() != '\r') output.append(newline)
            output.append(scope).append(newline)
            entries.forEach { output.append(it.line).append(newline) }
        }
        return output.toString().toByteArray(Charsets.UTF_8)
    }

    private fun entry(section: String, line: String): Entry? {
        val text = line.trim()
        if (text.startsWith(";") || text.startsWith("#") || '=' !in text) return null
        val name = text.substringBefore('=').trim().lowercase(Locale.ROOT)
        val value = text.substringAfter('=').trim()
        val element = when {
            name == "+cvars" -> value.substringBefore('=').trim().lowercase(Locale.ROOT)
            name == "paths" || name.startsWith("+") || name.startsWith(".") || name.startsWith("-") ->
                value.lowercase(Locale.ROOT)
            else -> ""
        }
        val scalar = if (name == "+cvars") value.substringAfter('=', "") else value
        return Entry(Key(section, name, element), line, scalar)
    }

    private fun decode(bytes: ByteArray): String {
        require(bytes.size <= 4 * 1024 * 1024) { "INI exceeds the 4 MiB editing limit." }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        require('\u0000' !in text) { "Binary or UTF-16 files are not supported by this editor." }
        return text
    }
}
