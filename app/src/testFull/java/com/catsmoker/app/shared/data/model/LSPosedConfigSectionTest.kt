package com.catsmoker.app.shared.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the wildcard section fallback: exact sections win first, then `*` pattern headers
 * (longest wins), with the system guard refusing protected packages. Both config channels
 * (provider render + Settings.Global copy the module reads) share this rule.
 */
class LSPosedConfigSectionTest {

    private val doc = """
        [com.pubg.krmobile]
        ro.product.model=Exact
        [com.pubg.*]
        ro.product.model=Family
        [com.pubg.kr*]
        ro.product.model=Longer
    """.trimIndent()

    @Test
    fun exactSectionWinsFirst() {
        assertEquals("ro.product.model=Exact\n", LSPosedConfig.parseSectionWildcard(doc, "com.pubg.krmobile"))
    }

    @Test
    fun longestPatternWinsTies() {
        // com.pubg.imobile matches only the family pattern.
        assertEquals("ro.product.model=Family\n", LSPosedConfig.parseSectionWildcard(doc, "com.pubg.imobile"))
        // A longer pattern beats the family one where both match is covered by the matcher;
        // here com.pubg.krmobile has its exact section, so assert the fallback shape directly.
        val familyOnly = "[com.pubg.*]\nro.product.model=Family\n[com.pubg.kr*]\nro.product.model=Longer"
        assertEquals("ro.product.model=Longer\n", LSPosedConfig.parseSectionWildcard(familyOnly, "com.pubg.krmobile"))
    }

    @Test
    fun systemGuardRefusesProtected() {
        val doc = "[*]\nro.product.model=All\n"
        assertNull(LSPosedConfig.parseSectionWildcard(doc, "android"))
        assertNull(LSPosedConfig.parseSectionWildcard(doc, "com.android.systemui"))
    }

    @Test
    fun noMatchStaysNull() {
        assertNull(LSPosedConfig.parseSectionWildcard(doc, "com.other.game"))
        assertNull(LSPosedConfig.parseSectionWildcard("", "com.a"))
        assertNull(LSPosedConfig.parseSectionWildcard(doc, ""))
    }
}
