package com.catsmoker.app.features.spoofdevice.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for the refresh-rate mode-list filter.
 *
 * Games that enumerate `Display.getSupportedModes()` / `getSupportedRefreshRates()` never call
 * `getRefreshRate()`, so the current-rate hook alone leaves them reading the real panel. The
 * filter keeps the entries at the spoofed tier; rates compare with a small tolerance because
 * panels report nominal rates with jitter (`119.999985` is a 120 Hz mode). An empty result
 * means "leave the list alone" — handing a game zero display modes would break it, so the
 * hook returns the original list in that case.
 */
class DisplayRefreshSpoofTest {

    @Test
    fun keepsModesAtTheSpoofedTier() {
        assertEquals(
            listOf(120.0f),
            filterRefreshRates(listOf(60.0f, 90.0f, 120.0f), 120f)
        )
    }

    @Test
    fun toleratesPanelJitter() {
        assertEquals(
            listOf(119.999985f),
            filterRefreshRates(listOf(59.99999f, 119.999985f), 120f)
        )
    }

    @Test
    fun noMatchMeansLeaveAlone() {
        // A 120 Hz spoof on a 90 Hz panel: filtering would hand the game an empty mode list,
        // so the hook keeps the real one instead.
        assertTrue(filterRefreshRates(listOf(60.0f, 90.0f), 120f).isEmpty())
    }

    @Test
    fun emptyInMeansEmptyOut() {
        assertTrue(filterRefreshRates(emptyList(), 120f).isEmpty())
    }

    @Test
    fun nonPositiveSpoofDisables() {
        assertTrue(filterRefreshRates(listOf(60.0f, 120.0f), 0f).isEmpty())
        assertTrue(filterRefreshRates(listOf(60.0f, 120.0f), -120f).isEmpty())
    }
}
