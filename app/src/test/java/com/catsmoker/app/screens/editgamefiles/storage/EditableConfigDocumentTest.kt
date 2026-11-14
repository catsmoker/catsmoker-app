package com.catsmoker.app.screens.editgamefiles.storage

import com.catsmoker.app.screens.editgamefiles.model.*
import org.junit.Assert.*
import org.junit.Test

class EditableConfigDocumentTest {
    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8)
    private fun parse(text: String, file: EditableConfigFile) = EditableConfigDocument.parse(bytes(text), file)
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: Exception) { }
    }

    @Test fun `xml patch targets exact scope and preserves BOM CRLF comments binary arrays and presets`() {
        val xml = "\uFEFF<?xml version=\"1.0\"?>\r\n<registry><key name=\"HKEY_CURRENT_USER\"><key name=\"Software\">" +
            "<key name=\"Active\"><!--keep--><value name=\"FPS\" type=\"integer\"> 60 </value>" +
            "<value name=\"Blob\" type=\"binary\">0000ABFF</value><value name=\"List\" type=\"array\"><value>9</value></value></key>" +
            "<key name=\"Preset\"><value name=\"FPS\" type=\"integer\">30</value></key></key></key></registry>\r\n"
        val file = feralConfigFile("Active" to integerFields("FPS", "Blob", "List", "Missing"))
        val doc = parse(xml, file)
        assertEquals(mapOf("HKEY_CURRENT_USER/Software/Active/FPS" to "60"), doc.values)
        val edited = doc.patch(mapOf(doc.values.keys.single() to "90"))
        assertArrayEquals(bytes(xml.replace("> 60 </value>", "> 90 </value>")), edited)
        rejected { doc.patch(mapOf("HKEY_CURRENT_USER/Software/Active/Missing" to "1")) }
    }

    @Test fun `repeated xml fields in same scope disabled but other fields survive`() {
        val xml = "<registry><key name=\"HKEY_CURRENT_USER\"><key name=\"Software\"><key name=\"Game\">" +
            "<value name=\"FPS\" type=\"integer\">30</value><value name=\"FPS\" type=\"integer\">60</value>" +
            "<value name=\"Height\" type=\"integer\">720</value></key></key></key></registry>"
        val doc = parse(xml, feralConfigFile("Game" to integerFields("FPS", "Height")))
        assertEquals(listOf("HKEY_CURRENT_USER/Software/Game/Height"), doc.values.keys.toList())
    }

    @Test fun `xml string types cannot masquerade as integer fields and apostrophe scope decodes`() {
        val xml = "<registry><key name='HKEY_CURRENT_USER'><key name='Software'><key name='Sid&apos;s Game'>" +
            "<value name='Quality' type='string'>High</value><value name='FPS' type='string'>60</value>" +
            "</key></key></key></registry>"
        val doc = parse(xml, feralConfigFile("Sid's Game" to (tokenFields("Quality") + integerFields("FPS"))))
        assertEquals(mapOf("HKEY_CURRENT_USER/Software/Sid's Game/Quality" to "High"), doc.values)
        rejected { doc.patch(mapOf(doc.values.keys.single() to "<script>")) }
    }

    @Test fun `malformed xml and external entities rejected`() {
        val file = feralConfigFile("Game" to integerFields("FPS"))
        rejected { parse("<registry><key></registry>", file) }
        rejected { parse("<!DOCTYPE registry [<!ENTITY evil SYSTEM 'file:///secrets'>]><registry>&evil;</registry>", file) }
    }

    @Test fun `bedrock colon format preserves unknown fields and render distance is not converted`() {
        val text = "\uFEFFgfx_max_framerate:60\r\ngfx_renderdistance_new:128\r\ngfx_fancygraphics:1\r\nunknown:keep:all\r\n"
        val file = EditableConfigFile("options.txt", ConfigFormat.BEDROCK_OPTIONS,
            integerFields("gfx_max_framerate", "gfx_renderdistance_new", "gfx_fancygraphics"))
        val doc = parse(text, file)
        assertEquals("128", doc.values["gfx_renderdistance_new"])
        assertArrayEquals(bytes(text.replace("framerate:60", "framerate:0")), doc.patch(mapOf("gfx_max_framerate" to "0")))
    }

    @Test fun `civilization space INI preserves Version duplicate sections and equals lines`() {
        val text = "Version 10\r\n[Video]\r\nMSAA 1 ; keep\r\nUnknown=3\r\n[General]\r\nX 4\r\n" +
            "[Video]\r\nVSync 1\r\n[General]\r\nX 5\r\n"
        val file = EditableConfigFile("GraphicsOptions.txt", ConfigFormat.CIV6_INI,
            integerFields("MSAA", "VSync").map { it.copy(scope = "Video") })
        assertArrayEquals(bytes(text.replace("MSAA 1", "MSAA 4")), parse(text, file).patch(mapOf("Video/MSAA" to "4")))
        rejected { parse(text.removePrefix("Version 10\r\n"), file) }
        rejected { parse("Version 10\n[Video]\nMSAA=1\n", file) }
    }

    @Test fun `civilization repeated key in repeated section is ambiguous not overwritten`() {
        val text = "Version 10\n[Video]\nMSAA 1\n[Video]\nMSAA 2\nVSync 1\n"
        val file = EditableConfigFile("GraphicsOptions.txt", ConfigFormat.CIV6_INI,
            integerFields("MSAA", "VSync").map { it.copy(scope = "Video") })
        assertEquals(mapOf("Video/VSync" to "1"), parse(text, file).values)
    }

    @Test fun `terraria JSON patches top-level primitive only preserving whitespace nested arrays escapes and unknowns`() {
        val text = "{\r\n  \"WaterfallDrawLimit\": 100, \"DisableIntenseVisualEffects\": false," +
            " \"Nested\": {\"WaterfallDrawLimit\": 9}, \"Array\": [1,true,null,\"a\\\"b\"], \"Other\": 1e2\r\n}\r\n"
        val file = EditableConfigFile("config.json", ConfigFormat.TERRARIA_JSON,
            integerFields("WaterfallDrawLimit") + ConfigField("", "DisableIntenseVisualEffects", ConfigScalar.BOOLEAN))
        val doc = parse(text, file)
        assertEquals(mapOf("WaterfallDrawLimit" to "100", "DisableIntenseVisualEffects" to "false"), doc.values)
        assertArrayEquals(bytes(text.replace("Effects\": false", "Effects\": true")), doc.patch(mapOf("DisableIntenseVisualEffects" to "true")))
        rejected { doc.patch(mapOf("DisableIntenseVisualEffects" to "1")) }
    }

    @Test fun `JSON duplicate escaped keys disabled and string numbers unsupported`() {
        val file = EditableConfigFile("config.json", ConfigFormat.TERRARIA_JSON, integerFields("Limit", "Other", "String"))
        val doc = parse("{\"Limit\":1,\"L\\u0069mit\":2,\"Other\":3,\"String\":\"4\"}", file)
        assertEquals(mapOf("Other" to "3"), doc.values)
        listOf("{\"Other\":01}", "{\"Other\":1,}", "{\"Other\":1}garbage", "{\"Other\":1,\"X\":\"\\q\"}").forEach {
            rejected { parse(it, file) }
        }
    }

    @Test fun `invalid UTF8 binary empty oversized configs and overflowing numeric edits rejected`() {
        val file = EditableConfigFile("options.txt", ConfigFormat.BEDROCK_OPTIONS, integerFields("FPS"))
        listOf(byteArrayOf(), byteArrayOf(0xC3.toByte(), 0x28), bytes("FPS:1\u0000"), ByteArray(EditableConfigDocument.MAX_BYTES + 1)).forEach {
            try { EditableConfigDocument.parse(it, file); fail("Expected rejection") } catch (_: Exception) { }
        }
        rejected { parse("FPS:60", file).patch(mapOf("FPS" to "999999999999999999999")) }
        rejected { parse("FPS:60", file).patch(mapOf("FPS" to "1\nunknown:2")) }
    }
}
