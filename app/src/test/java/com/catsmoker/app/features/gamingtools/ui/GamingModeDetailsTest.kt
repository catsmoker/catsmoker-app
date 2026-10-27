package com.catsmoker.app.features.gamingtools.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The folded Gaming Mode report: closed by default, opened and closed by review only, and its
 * summary derived from the same rows the card renders.
 *
 * The important product rule is that the disclosure is pure state. There is no activation hook in
 * [GamingModeDetails] to call, so opening the list cannot repeat an optimization; the source-level
 * placement test pins that the composable has no such callback either.
 */
class GamingModeDetailsTest {

    @Test
    fun startsCollapsed() {
        assertFalse(GamingModeDetails.DEFAULT_EXPANDED)
    }

    @Test
    fun toggleOpensAndClosesTheSameList() {
        val opened = GamingModeDetails.toggle(GamingModeDetails.DEFAULT_EXPANDED)
        assertTrue(opened)
        assertFalse(GamingModeDetails.toggle(opened))
    }

    @Test
    fun summaryCountsOnlyConfirmedRows() {
        val rows = listOf(
            line(applied = true),
            line(applied = false),
            line(applied = true)
        )

        assertEquals(GamingModeDetailsSummary(confirmed = 2, total = 3), GamingModeDetails.summarize(rows))
    }

    @Test
    fun emptyReportHasAnEmptySummary() {
        assertEquals(GamingModeDetailsSummary(0, 0), GamingModeDetails.summarize(emptyList()))
    }

    private fun line(applied: Boolean) = GamingModeResultLine(
        label = "CPU governor",
        value = "Performance",
        applied = applied
    )
}
