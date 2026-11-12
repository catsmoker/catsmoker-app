package com.catsmoker.app.application.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Behaviour lock for locale-tag normalization (Batch 5: EN/AR leakage fix).
 *
 * Region variants from previous builds (`ar-SA`) must fold to the shipped base
 * (`ar`) — otherwise the wrap resolves a configuration the stripped resources
 * cannot satisfy and the UI falls back to the wrong language.
 */
class LocaleHelperTest {

    @Test
    fun emptyStaysSystem() {
        assertEquals("", LocaleHelper.normalizeTag(""))
    }

    @Test
    fun regionVariantsFoldToShippedBase() {
        assertEquals("ar", LocaleHelper.normalizeTag("ar-SA"))
        assertEquals("ar", LocaleHelper.normalizeTag("ar-EG"))
        assertEquals("en", LocaleHelper.normalizeTag("en-GB"))
        assertEquals("es", LocaleHelper.normalizeTag("es-ES"))
        assertEquals("zh-CN", LocaleHelper.normalizeTag("zh-TW"))
        assertEquals("zh-CN", LocaleHelper.normalizeTag("zh-CN"))
        assertEquals("pt-BR", LocaleHelper.normalizeTag("pt-PT"))
        assertEquals("pt-BR", LocaleHelper.normalizeTag("pt"))
    }

    @Test
    fun bareTagsPassThrough() {
        assertEquals("ar", LocaleHelper.normalizeTag("ar"))
        assertEquals("en", LocaleHelper.normalizeTag("en"))
        assertEquals("es", LocaleHelper.normalizeTag("es"))
        assertEquals("zh-CN", LocaleHelper.normalizeTag("zh-CN"))
        assertEquals("pt-BR", LocaleHelper.normalizeTag("pt-BR"))
    }

    @Test
    fun unknownTagsPassThrough() {
        assertEquals("fr", LocaleHelper.normalizeTag("fr"))
    }

    @Test
    fun followingSystemMatchesEveryShippedLanguageIncludingRegionVariants() {
        listOf(
            "en-GB" to "en", "ar-SA" to "ar", "es-ES" to "es",
            "zh-CN" to "zh-CN", "pt-BR" to "pt-BR", "ja-JP" to "ja",
        ).forEach { (system, selected) ->
            assertEquals(LocaleHelper.normalizeTag(selected), LocaleHelper.effectiveLanguageTag("", system))
        }
    }

    @Test
    fun systemAndMatchingExplicitSelectionsHaveTheSameEffectiveLanguageInBothDirections() {
        listOf(
            "en-GB" to "en", "ar-SA" to "ar", "es-ES" to "es",
            "zh-CN" to "zh-CN", "pt-BR" to "pt-BR", "ja-JP" to "ja",
        ).forEach { (system, selected) ->
            assertNotEquals("", LocaleHelper.normalizeTag(selected))
            assertEquals(
                LocaleHelper.effectiveLanguageTag("", system),
                LocaleHelper.effectiveLanguageTag(selected, system),
            )
            assertEquals(
                LocaleHelper.effectiveLanguageTag(selected, system),
                LocaleHelper.effectiveLanguageTag("", system),
            )
        }
    }

    @Test
    fun switchingToOrFromADifferentSystemLanguageChangesTheEffectiveLanguage() {
        assertNotEquals(
            LocaleHelper.effectiveLanguageTag("ar", "en-GB"),
            LocaleHelper.effectiveLanguageTag("", "en-GB"),
        )
        assertNotEquals(
            LocaleHelper.effectiveLanguageTag("", "en-GB"),
            LocaleHelper.effectiveLanguageTag("ar", "en-GB"),
        )
    }

    @Test
    fun explicitPreferenceTakesPrecedenceOverSystemLanguage() {
        assertEquals("ar", LocaleHelper.effectiveLanguageTag("ar-SA", "en-GB"))
        assertEquals("en", LocaleHelper.effectiveLanguageTag("en", "ar-SA"))
    }

    @Test
    fun differentLanguageStillDiffersFromEffectiveSystemLanguage() {
        assertNotEquals(
            LocaleHelper.normalizeTag("ar"), LocaleHelper.effectiveLanguageTag("", "en-GB"),
        )
    }
}
