package com.catsmoker.app.screens.editgamefiles.games.starrail

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One apply for an editor that keeps two working copies.
 *
 * Star Rail writes two key sets from the same file: graphics settings and QoL preferences. They
 * used to have a button each, mid-page, which asked the user to decide which of their own pending
 * edits was allowed to land first. The pinned deploy bar now has one APPLY, and the ordering that
 * makes it safe is not obvious from the call site — so it is pinned here.
 *
 * The trap this guards: a successful graphics write re-reads the file and re-baselines *both*
 * working copies, which replaces pending QoL edits with what the device already holds. They were
 * never written, yet the re-baseline would make them read as clean, so they would be lost
 * silently. Hence the QoL copy is captured before the graphics pass runs.
 */
class HsrSingleApplyTest {

    private val viewModel = File(
        "src/main/java/com/catsmoker/app/screens/editgamefiles/games/starrail/HsrGraphicsViewModel.kt"
    ).readText()

    private val screen = File(
        "src/main/java/com/catsmoker/app/screens/editgamefiles/games/starrail/HsrGraphicsScreen.kt"
    ).readText()

    @Test
    fun pendingPrefsAreCapturedBeforeTheGraphicsWrite() {
        val body = viewModel.substringAfter("fun onApplyPending()").substringBefore("fun onRestoreBackup")
        val capture = body.indexOf("val pendingPrefs = start.gamePrefs?.takeIf { start.prefsDirty }")
        val settingsWrite = body.indexOf("applySettingsNow(settings)")
        val prefsWrite = body.indexOf("applyPrefsNow(pendingPrefs)")
        assertTrue("the QoL copy must be captured", capture >= 0)
        assertTrue("the capture must precede the graphics write", capture < settingsWrite)
        assertTrue("the captured copy must be written afterwards", prefsWrite > settingsWrite)
    }

    @Test
    fun aFailedGraphicsWriteStopsTheSequence() {
        val body = viewModel.substringAfter("fun onApplyPending()").substringBefore("fun onRestoreBackup")
        assertTrue(
            "a failed first write must not be followed by a second one",
            body.contains("if (start.settingsDirty && !applySettingsNow(settings)) return@launch")
        )
    }

    @Test
    fun bothWorkingCopiesAreAppliedByTheOneAction() {
        val body = viewModel.substringAfter("fun onApplyPending()").substringBefore("fun onRestoreBackup")
        assertTrue("graphics are applied when dirty", body.contains("start.settingsDirty"))
        assertTrue("prefs are applied when dirty", body.contains("pendingPrefs != null"))
        // Nothing pending means the bar must not be able to start a write at all.
        assertTrue(body.contains("if (!start.settingsDirty && pendingPrefs == null) return"))
    }

    @Test
    fun theScreenOffersOnlyOneApply() {
        assertEquals(
            "the QoL card must not keep a second apply button",
            0,
            Regex("""R\.string\.gf_apply_prefs""").findAll(screen).count()
        )
        // One apply in the live path. The editor's inline block keeps its own copy of the button
        // for standalone routes, where no host exists to pin a bar; that is the fallback, not a
        // second button on the embedded screen.
        assertEquals(
            "the embedded editor offers exactly one apply",
            1,
            Regex("""R\.string\.gf_apply_to_game""").findAll(screen.substringBefore("inline = {")).count()
        )
        assertTrue(
            "the bar reports both working copies as one pending state",
            screen.contains("dirty = uiState.settingsDirty || uiState.prefsDirty")
        )
        assertTrue(
            "the bar counts both working copies",
            screen.contains("pendingCount = uiState.pendingSettingsCount + uiState.pendingPrefsCount")
        )
    }

    @Test
    fun barUndoFollowsTheLastEditedCopy() {
        assertTrue("the state tracks which copy was touched", viewModel.contains("val lastEdited: HsrEditArea"))
        assertTrue(viewModel.contains("lastEdited = HsrEditArea.SETTINGS"))
        assertTrue(viewModel.contains("lastEdited = HsrEditArea.PREFS"))
        // With two copies and one undo button, the fallback matters: a copy with nothing left to
        // undo must not swallow the step that the other copy still has.
        assertTrue(screen.contains("undoPrefs = if (uiState.lastEdited == HsrEditArea.PREFS)"))
        assertTrue(screen.contains("undo = if (undoPrefs) onUndoPrefs else onUndoSettings"))
    }

    @Test
    fun thereIsOneApplyEntryPointAndItsCoreStaysPrivate() {
        // onApplyPrefs and onApply each existed for one button; with one apply button there is one
        // entry point. The QoL write is reachable through it only.
        assertFalse(viewModel.contains("fun onApplyPrefs()"))
        assertFalse(viewModel.contains("fun onApply()"))
        assertTrue(viewModel.contains("fun onApplyPending()"))
        assertTrue(
            "the suspend cores must not become the public API",
            viewModel.contains("private suspend fun applySettingsNow(") &&
                viewModel.contains("private suspend fun applyPrefsNow(")
        )
        assertEquals(
            "one apply label in the live path and one in the standalone fallback",
            2,
            Regex("""R\.string\.gf_apply_to_game""").findAll(screen).count()
        )
        assertFalse(
            "the fallback must not drift back to a graphics-only apply",
            screen.contains("onClick = onApply,")
        )
    }

    @Test
    fun aGraphicsWriteCapturesThePendingQoLCopyBeforeRebaselining() {
        // The rule itself is proven by HsrGraphicsWriteStateTest against the pure transition; this
        // only checks that the write path still routes through it with the copy captured first,
        // so the two cannot drift apart.
        val body = viewModel.substringAfter("private suspend fun applySettingsNow(")
            .substringBefore("fun onApplyPending()")
        assertTrue(
            "the pending QoL copy must be captured before the re-baseline",
            body.contains("val pendingPrefs = _uiState.value.gamePrefs?.takeIf { _uiState.value.prefsDirty }")
        )
        assertTrue(
            "the state transition must go through the tested function",
            body.contains("state.afterGraphicsWrite(")
        )
        assertFalse(
            "the success branch must not clear the QoL dirty flag inline any more",
            body.contains("prefsDirty = false")
        )
    }
}
