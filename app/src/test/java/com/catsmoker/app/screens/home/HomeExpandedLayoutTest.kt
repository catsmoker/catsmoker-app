package com.catsmoker.app.screens.home

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the expanded-window dashboard against going back to a stretched phone layout.
 *
 * At 1920x1200 dp the four console tiles used to come out ~700x830 dp each, about four fifths of
 * which was empty gradient, because every row divided whatever height the window had. The
 * expanded branch now caps the board's width, fixes the telemetry panel and tile heights, and
 * centres the board. Those are three separate numbers that are easy to delete by accident, so
 * they are asserted here; the visual result is checked on the desktop emulator.
 */
class HomeExpandedLayoutTest {

    private val screen = File(
        "src/main/java/com/catsmoker/app/screens/home/HomeScreen.kt"
    ).readText()

    @Test
    fun expandedWindowGetsItsOwnComposition() {
        assertTrue(
            "the dashboard must branch on an expanded window, not only on landscape",
            screen.contains("val expanded = maxWidth >= 792.dp && maxHeight >= 560.dp")
        )
        assertTrue(screen.contains("ExpandedDashboard("))
        // Landscape alone is not enough: 1920x1200 is landscape too, and that is exactly the
        // window size the stretched-tile report came from.
        assertTrue(screen.contains("if (expanded) {"))
    }

    @Test
    fun expandedBoardIsBoundedInsteadOfStretched() {
        assertTrue("board width is capped", screen.contains("widthIn(max = DashboardMaxWidth)"))
        assertTrue("telemetry panel has a fixed height", screen.contains("height(DashboardTelemetryHeight)"))
        assertTrue("console tiles are content-sized", screen.contains("tileHeight = DashboardTileHeight"))
        assertTrue("the board is centred in the window", screen.contains(".align(Alignment.Center)"))
    }

    @Test
    fun tileRowsOnlyStretchWhenTheHostDividesSpace() {
        // tileHeight == null keeps the phone behaviour (rows divide the frame); a non-null value
        // is the desktop case. Both must stay supported — the phone layout depends on the weights.
        assertTrue(screen.contains("tileHeight: Dp? = null"))
        assertEquals(
            "each tile row decides between a fixed height and a weight",
            2,
            Regex("""if \(tileHeight == null\) Modifier\.weight\(1f\) else Modifier\.height\(tileHeight\)""")
                .findAll(screen).count()
        )
    }

    @Test
    fun telemetryCardIsSharedNotCopied() {
        // One declaration plus one call per dashboard branch (portrait, landscape, expanded).
        assertEquals(
            "the telemetry card must stay a single composable",
            1,
            Regex("""private fun TelemetryCard\(""").findAll(screen).count()
        )
        assertTrue(Regex("""TelemetryCard\(""").findAll(screen).count() >= 4)
    }
}
