package com.catsmoker.app.features.gamingtools.tools.graphics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `angle_gl_driver_selection_pkgs/values` parallel-list format, pinned before use.
 *
 * Android holds one comma-separated package list and one comma-separated driver list
 * (`angle`/`native`/`default`), zipped by position. The lists are device truth shared with
 * Developer Options and any other tool — so this app merges into them, never overwrites them,
 * and unknown tokens pass through verbatim rather than being "cleaned" into something the
 * device never held.
 */
class AngleDriverOptionsTest {

    @Test
    fun blankListsParseToNothing() {
        assertTrue(AngleDriverOptions.parsePairs(null, null).isEmpty())
        assertTrue(AngleDriverOptions.parsePairs("", "").isEmpty())
        assertTrue(AngleDriverOptions.parsePairs("null", "null").isEmpty())
    }

    @Test
    fun pairsZipByPosition() {
        val pairs = AngleDriverOptions.parsePairs(
            "com.game.a,com.game.b",
            "angle,native"
        )
        assertEquals(
            listOf("com.game.a" to "angle", "com.game.b" to "native"),
            pairs
        )
    }

    @Test
    fun raggedListsPairUpToTheShorterOne() {
        // A foreign write may leave the lists different lengths; pairing past the end would
        // invent entries, so the excess is left alone (and preserved on write-back).
        val pairs = AngleDriverOptions.parsePairs("com.game.a,com.game.b", "angle")
        assertEquals(listOf("com.game.a" to "angle"), pairs)
    }

    @Test
    fun unknownTokensPassThroughVerbatim() {
        val pairs = AngleDriverOptions.parsePairs("com.game.a", "weird")
        assertEquals(listOf("com.game.a" to "weird"), pairs)
    }

    @Test
    fun settingADriverReplacesInPlaceAndKeepsOthers() {
        val start = listOf("com.game.a" to "native", "com.game.b" to "angle")
        val updated = AngleDriverOptions.withDriver(start, "com.game.a", "angle")
        assertEquals(
            listOf("com.game.a" to "angle", "com.game.b" to "angle"),
            updated
        )
    }

    @Test
    fun settingADriverAppendsNewPackages() {
        val updated = AngleDriverOptions.withDriver(
            listOf("com.game.a" to "angle"), "com.game.b", "native"
        )
        assertEquals(
            listOf("com.game.a" to "angle", "com.game.b" to "native"),
            updated
        )
    }

    @Test
    fun defaultRemovesTheEntry() {
        // Absent means platform-default, so "default" is stored as removal, not as a token
        // that grows the lists forever.
        val updated = AngleDriverOptions.withDriver(
            listOf("com.game.a" to "angle", "com.game.b" to "native"),
            "com.game.a",
            "default"
        )
        assertEquals(listOf("com.game.b" to "native"), updated)
    }

    @Test
    fun roundTripToCsv() {
        val (pkgs, values) = AngleDriverOptions.toCsv(
            listOf("com.game.a" to "angle", "com.game.b" to "native")
        )
        assertEquals("com.game.a,com.game.b", pkgs)
        assertEquals("angle,native", values)
    }

    @Test
    fun emptyPairsClearBothLists() {
        val (pkgs, values) = AngleDriverOptions.toCsv(emptyList())
        assertEquals("", pkgs)
        assertEquals("", values)
    }
}
