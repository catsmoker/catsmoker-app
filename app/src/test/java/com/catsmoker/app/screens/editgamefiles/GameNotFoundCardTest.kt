package com.catsmoker.app.screens.editgamefiles

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The install-gate card, in its two states.
 *
 * The card is the only thing standing between the user and an editor for a game the probe could
 * not find. Full size it explains the situation and offers the override; once the override is
 * in force those two jobs are done, and keeping the full card meant the editor it had just
 * unlocked started ~530 px lower — measured, one third of a swipe spent re-reading a decision
 * already made. These guards keep the compact form wired to the state that earns it, and keep
 * the override button out of the branch where it would be a no-op.
 */
class GameNotFoundCardTest {

    private val screen = File(
        "src/main/java/com/catsmoker/app/screens/editgamefiles/EditGameFilesScreen.kt"
    ).readText()

    private val card = File(
        "src/main/java/com/catsmoker/app/screens/editgamefiles/ProfileEditorControls.kt"
    ).readText()

    @Test
    fun compactFormIsTiedToTheForcedOpenState() {
        assertTrue(
            "the compact card belongs to the show-anyway branch only",
            screen.contains("compact = uiState.showEditorAnyway")
        )
        assertTrue(card.contains("compact: Boolean = false"))
        // The compact form must keep both recovery actions: it drops explanation, not capability.
        val compactBody = card.substringAfter("if (compact) {").substringBefore("return")
        assertTrue(compactBody.contains("onRecheck"))
        assertTrue(compactBody.contains("onOpenStore"))
    }

    @Test
    fun overrideButtonIsNotOfferedTwice() {
        // Inside the already-shown branch the card is constructed without an override callback,
        // so it cannot draw a button that would change nothing.
        val forcedBranch = screen.substringAfter("if (gameMissing) {").substringBefore("when (definition.implementation)")
        assertTrue(forcedBranch.contains("onShowAnyway = null"))
        assertFalse(forcedBranch.contains("onShowAnyway = onShowAnyway"))
    }

    @Test
    fun gateStillOffersTheOverrideBeforeTheEditorIsOpen() {
        assertTrue(screen.contains("onShowAnyway = onShowAnyway"))
        assertTrue(card.contains("onShowAnyway != null"))
    }

    @Test
    fun storeButtonCannotWrapIntoATallerRow() {
        assertEquals(
            "both buttons in the store row are single-line",
            1,
            Regex("""gf_open_play_store""").findAll(card).count() - 1
        )
        assertTrue(card.contains("Modifier.weight(1.4f)"))
    }
}
