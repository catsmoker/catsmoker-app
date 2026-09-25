package com.catsmoker.app.system.config

import org.junit.Assert.assertEquals
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
    }

    @Test
    fun bareTagsPassThrough() {
        assertEquals("ar", LocaleHelper.normalizeTag("ar"))
        assertEquals("en", LocaleHelper.normalizeTag("en"))
        assertEquals("es", LocaleHelper.normalizeTag("es"))
        assertEquals("zh-CN", LocaleHelper.normalizeTag("zh-CN"))
    }

    @Test
    fun unknownTagsPassThrough() {
        assertEquals("fr", LocaleHelper.normalizeTag("fr"))
    }
}
