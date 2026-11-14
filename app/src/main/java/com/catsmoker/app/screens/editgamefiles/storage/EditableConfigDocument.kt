package com.catsmoker.app.screens.editgamefiles.storage

import com.catsmoker.app.screens.editgamefiles.model.ConfigField
import com.catsmoker.app.screens.editgamefiles.model.ConfigFormat
import com.catsmoker.app.screens.editgamefiles.model.ConfigScalar
import com.catsmoker.app.screens.editgamefiles.model.EditableConfigFile
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/** Span edits keep unknown settings, comments, duplicate sections and binary XML values intact. */
class EditableConfigDocument private constructor(
    private val text: String,
    private val spec: EditableConfigFile,
    private val spans: Map<String, Span>
) {
    private data class Span(val start: Int, val end: Int, val value: String, val scalar: ConfigScalar?)

    val values: Map<String, String> = spec.fields.mapNotNull { field ->
        spans[field.id]?.takeIf { it.scalar == field.scalar && field.accepts(it.value) }
            ?.let { field.id to it.value }
    }.toMap()

    fun patch(edits: Map<String, String>): ByteArray {
        require(edits.isNotEmpty()) { "No changes selected." }
        val fields = spec.fields.associateBy(ConfigField::id)
        edits.forEach { (id, value) ->
            require(id in values) { "Missing, ambiguous or unsupported setting: $id" }
            require(fields.getValue(id).accepts(value)) { "Invalid value: $id" }
        }
        var result = text
        edits.entries.sortedByDescending { spans.getValue(it.key).start }.forEach { (id, value) ->
            val span = spans.getValue(id)
            result = result.replaceRange(span.start, span.end, value)
        }
        val bytes = result.toByteArray(Charsets.UTF_8)
        val reparsed = parse(bytes, spec)
        require(edits.all { (id, value) -> reparsed.values[id] == value }) { "Edited config failed validation." }
        return bytes
    }

    companion object {
        const val MAX_BYTES = 1024 * 1024

        fun parse(bytes: ByteArray, spec: EditableConfigFile): EditableConfigDocument {
            require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Config is empty or larger than 1 MiB." }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            require('\u0000' !in text) { "Binary/UTF-16 configs are not supported." }
            val all = when (spec.format) {
                ConfigFormat.FERAL_XML -> xmlSpans(text)
                ConfigFormat.BEDROCK_OPTIONS -> lineSpans(text, ini = false)
                ConfigFormat.CIV6_INI -> lineSpans(text, ini = true)
                ConfigFormat.TERRARIA_JSON -> ConfigJsonSpans(text).read().map { entry ->
                    entry.id to Span(entry.start, entry.end, entry.value, entry.scalar)
                }
            }
            // A repeated name in the SAME scope is ambiguous. Different scopes remain independent.
            val spans = all.groupBy({ it.first }, { it.second }).filterValues { it.size == 1 }.mapValues { it.value.single() }
            val document = EditableConfigDocument(text, spec, spans)
            require(document.values.isNotEmpty()) { "No recognized settings with the expected types in this file." }
            return document
        }

        private fun lineSpans(text: String, ini: Boolean): List<Pair<String, Span>> {
            if (ini) require(Regex("(?m)^Version +\\d+\\r?$").containsMatchIn(text.removePrefix("\uFEFF"))) {
                "Civilization VI Version header is missing."
            }
            val result = mutableListOf<Pair<String, Span>>()
            var scope = ""
            for (line in Regex("[^\\r\\n]+").findAll(text)) {
                val raw = line.value.removePrefix("\uFEFF")
                val section = Regex("\\s*\\[([^]]+)]\\s*").matchEntire(raw)
                if (ini && section != null) {
                    scope = section.groupValues[1]
                    continue
                }
                val regex = if (ini) Regex("[ \\t]*([A-Za-z][A-Za-z0-9_]*)[ \\t]+([^;#]*?)[ \\t]*(?:[;#].*)?")
                    else Regex("[ \\t]*([A-Za-z][A-Za-z0-9_]*):[ \\t]*([^;#]*?)[ \\t]*(?:[;#].*)?")
                val match = regex.matchEntire(raw) ?: continue
                if (ini && scope.isEmpty()) continue // Version is never editable.
                val value = match.groups[2] ?: continue
                if (value.value.isEmpty()) continue
                val scalar = scalarOf(value.value)
                val offset = line.range.first + (line.value.length - raw.length)
                val id = if (ini) "$scope/${match.groupValues[1]}" else match.groupValues[1]
                result += id to Span(offset + value.range.first, offset + value.range.last + 1, value.value, scalar)
            }
            return result
        }

        private fun xmlSpans(text: String): List<Pair<String, Span>> {
            require(!Regex("<!\\s*(?:DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                "XML declarations with entities are not allowed."
            }
            val builder = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }
                .newDocumentBuilder()
            builder.setEntityResolver { _, _ -> throw IllegalArgumentException("External XML entities are not allowed.") }
            builder.setErrorHandler(object : org.xml.sax.helpers.DefaultHandler() {
                override fun error(e: org.xml.sax.SAXParseException) { throw e }
                override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
            })
            val dom = builder.parse(InputSource(StringReader(text.removePrefix("\uFEFF"))))
            require(dom.documentElement.tagName == "registry") { "Not a Feral registry XML file." }
            val tokens = Regex("<!--[\\s\\S]*?-->|<\\?[\\s\\S]*?\\?>|<(?:[^>\"']|\"[^\"]*\"|'[^']*')*>")
                .findAll(text).toList()
            val keys = mutableListOf<String>()
            val result = mutableListOf<Pair<String, Span>>()
            tokens.forEachIndexed { index, token ->
                val tag = token.value
                when {
                    Regex("<key(?:\\s|>)").containsMatchIn(tag) -> {
                        val name = attribute(tag, "name") ?: throw IllegalArgumentException("Unnamed registry scope.")
                        require('/' !in name) { "Unsupported registry scope name." }
                        if (!tag.endsWith("/>")) keys += name
                    }
                    tag == "</key>" -> { require(keys.isNotEmpty()); keys.removeAt(keys.lastIndex) }
                    Regex("<value(?:\\s|>)").containsMatchIn(tag) && !tag.endsWith("/>") -> {
                        val next = tokens.getOrNull(index + 1) ?: return@forEachIndexed
                        if (next.value != "</value>") return@forEachIndexed // arrays are read-only.
                        val name = attribute(tag, "name") ?: return@forEachIndexed
                        val type = when (attribute(tag, "type")) {
                            "integer" -> ConfigScalar.INTEGER
                            "double" -> ConfigScalar.DECIMAL
                            "string" -> ConfigScalar.TOKEN
                            else -> null // binary blobs never become editable numbers.
                        }
                        val start = token.range.last + 1
                        val body = text.substring(start, next.range.first)
                        val value = body.trim()
                        if (value.isNotEmpty()) {
                            val offset = start + body.indexOf(value)
                            result += "${keys.joinToString("/")}/$name" to Span(offset, offset + value.length, value, type)
                        }
                    }
                }
            }
            return result
        }

        private fun attribute(tag: String, name: String): String? =
            Regex("\\b${Regex.escape(name)}\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')").find(tag)?.let {
                (it.groups[1]?.value ?: it.groups[2]?.value)?.replace("&apos;", "'")
                    ?.replace("&quot;", "\"")?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&amp;", "&")
            }

        internal fun scalarOf(value: String): ConfigScalar? = when {
            value == "true" || value == "false" -> ConfigScalar.BOOLEAN
            value.matches(Regex("-?\\d+")) -> ConfigScalar.INTEGER
            value.matches(Regex("-?\\d+\\.\\d+")) -> ConfigScalar.DECIMAL
            value.matches(Regex("[A-Za-z][A-Za-z0-9_-]*")) -> ConfigScalar.TOKEN
            else -> null
        }
    }
}
