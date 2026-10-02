package com.catsmoker.app.features.editgamefiles.pubg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The native-hex `UserCustom.ini` corpus for a future `+CVars=` encoder (TODO M190/M212).
 *
 * Both files are shipped template blobs from the two decompiled reference tools
 * (`reference/pubg-closed-source-tools/`): the section is `[UserCustom DeviceProfile]`,
 * every cvar line is a `+CVars=<hex>` blob the engine decodes at runtime (proven native
 * encoding, not obfuscation — both tools emit it). Meanings of individual hex bodies are
 * UNVERIFIED: this test pins the corpus shape only, and no whole template is ever pushed
 * (see TODO M211/M189 — whole-blob push stays rejected).
 */
class PubgUserCustomCorpusTest {

    private fun corpus(name: String): List<String> {
        val file = File("src/test/resources/$name")
        assumeTrue("corpus file not found at ${file.absolutePath}", file.isFile)
        return file.readLines()
    }

    @Test
    fun euTemplateHasTwentyNineHexLines() {
        val lines = corpus("pubg_usercustom_eu.ini").filter { it.isNotBlank() }
        assertEquals("[UserCustom DeviceProfile]", lines.first().trim())
        val cvars = lines.filter { it.trim().startsWith("+CVars=") }
        assertEquals(30, lines.size)
        assertEquals(29, cvars.size)
        assertTrue(cvars.all { it.trim().removePrefix("+CVars=").matches(Regex("[0-9A-Fa-f]+")) })
    }

    @Test
    fun tqTemplateHasFortySixHexLines() {
        val lines = corpus("pubg_usercustom_tq.ini").filter { it.isNotBlank() }
        assertEquals("[UserCustom DeviceProfile]", lines.first().trim())
        val cvars = lines.filter { it.trim().startsWith("+CVars=") }
        assertEquals(47, lines.size)
        assertEquals(46, cvars.size)
        assertTrue(cvars.all { it.trim().removePrefix("+CVars=").matches(Regex("[0-9A-Fa-f]+")) })
    }

    @Test
    fun templatesShareTheSectionButDifferInBodies() {
        // Same native container, different tier sets — the reason a future encoder must learn
        // from BOTH (dual-tool corpus), not average them.
        val eu = corpus("pubg_usercustom_eu.ini").filter { it.trim().startsWith("+CVars=") }.toSet()
        val tq = corpus("pubg_usercustom_tq.ini").filter { it.trim().startsWith("+CVars=") }.toSet()
        assertTrue("corpora overlap (shared native lines)", eu.intersect(tq).isNotEmpty())
        assertTrue("corpora differ (distinct tier sets)", (eu - tq).isNotEmpty() && (tq - eu).isNotEmpty())
    }
}
