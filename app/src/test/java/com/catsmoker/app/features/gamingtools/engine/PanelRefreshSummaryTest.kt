package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for the panel-refresh summary.
 *
 * `Display.Mode` lists one entry per resolution+rate combo, so the raw list repeats rates
 * (and carries near-duplicates like 59.94 vs 60.0 from timing math). The summary shown is
 * whole-Hz, distinct, highest-first — and never a stand-in: an empty or garbage input yields
 * an empty list, which the UI reports as unreadable rather than printing a 60 the panel
 * never claimed.
 */
class PanelRefreshSummaryTest {

    @Test
    fun dedupesRoundsAndSortsHighestFirst() {
        assertEquals(
            listOf(120, 90, 60),
            PanelRefreshSummary.summarizeRates(listOf(120.0f, 90.0f, 60.0f, 120.0f, 59.94f))
        )
    }

    @Test
    fun dropsNonPositiveRates() {
        assertEquals(listOf(60), PanelRefreshSummary.summarizeRates(listOf(0f, -90f, 60f)))
    }

    @Test
    fun emptyInMeansEmptyOut() {
        assertTrue(PanelRefreshSummary.summarizeRates(emptyList()).isEmpty())
    }

    @Test
    fun nanAndInfiniteAreNotRates() {
        assertTrue(
            PanelRefreshSummary.summarizeRates(listOf(Float.NaN, Float.POSITIVE_INFINITY)).isEmpty()
        )
    }
}
