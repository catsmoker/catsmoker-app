package com.catsmoker.app.features.editgamefiles.pubg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the save editor's initial-selection and dirty-state rules.
 *
 * The regression this covers: after READ, `saveEdits` was empty, so every chip rendered
 * unselected even though the save carried 120/120/120, HDR and a phone camera. The user could
 * not tell what the file held, and re-selecting the same value created a bogus pending edit.
 */
class PubgSaveSelectionTest {

    private fun read(values: Map<String, Int>): PubgSavePatcher.ReadResult =
        PubgSavePatcher.ReadResult(
            fields = values.mapValues { PubgSavePatcher.FieldOutcome.Value(it.value) },
            sizeBytes = 128,
            isGvas = true
        )

    private fun fullSave(
        battleFps: Int = 8,
        lobbyFps: Int = 8,
        fpsLevel: Int = 8,
        render: Int = 4,
        tp: Int = 90,
        fp: Int = 103
    ) = read(
        mapOf(
            PubgSavePatcher.FIELD_BATTLE_FPS to battleFps,
            PubgSavePatcher.FIELD_LOBBY_FPS to lobbyFps,
            PubgSavePatcher.FIELD_FPS_LEVEL to fpsLevel,
            PubgSavePatcher.FIELD_BATTLE_RENDER to render,
            PubgSavePatcher.FIELD_LOBBY_RENDER to render,
            PubgSavePatcher.FIELD_TP_VIEW to tp,
            PubgSavePatcher.FIELD_FP_VIEW to fp
        )
    )

    @Test
    fun savedValuesSelectChipsWithNoPendingEdits() {
        val save = fullSave()
        assertEquals(120, PubgSaveSelection.selectedFps(save, emptyMap()))
        assertEquals(4, PubgSaveSelection.selectedRender(save, emptyMap()))
        assertEquals(
            PubgSavePatcher.VIEW_PRESETS.first(),
            PubgSaveSelection.selectedView(save, emptyMap())
        )
        assertTrue(PubgSaveSelection.fpsEditable(save))
        assertTrue(PubgSaveSelection.renderEditable(save))
        assertTrue(PubgSaveSelection.viewEditable(save))
    }

    @Test
    fun selectingTheSavedTierCreatesNoPendingEdit() {
        val save = fullSave()
        assertTrue(PubgSaveSelection.toggleFps(save, emptyMap(), 120).isEmpty())
        assertTrue(PubgSaveSelection.toggleRender(save, emptyMap(), 4).isEmpty())
        assertTrue(
            PubgSaveSelection.toggleView(save, emptyMap(), PubgSavePatcher.VIEW_PRESETS.first())
                .isEmpty()
        )
    }

    @Test
    fun changingTiersStoresOnlyRealDifferencesAndTogglesBackOff() {
        val save = fullSave()
        val fps = PubgSaveSelection.toggleFps(save, emptyMap(), 120)
        assertTrue("the saved 120 tier must not become a pending edit", fps.isEmpty())

        val lower = fullSave(battleFps = 6, lobbyFps = 6, fpsLevel = 6)
        val selected = PubgSaveSelection.toggleFps(lower, emptyMap(), 120)
        assertEquals(
            mapOf(
                PubgSavePatcher.FIELD_BATTLE_FPS to 8,
                PubgSavePatcher.FIELD_LOBBY_FPS to 8,
                PubgSavePatcher.FIELD_FPS_LEVEL to 8
            ),
            selected
        )
        assertTrue(PubgSaveSelection.toggleFps(lower, selected, 120).isEmpty())
    }

    @Test
    fun cameraPresetMovesBothFieldsAndTogglesBothOff() {
        val save = fullSave(tp = 90, fp = 103)
        val tablet = PubgSavePatcher.VIEW_PRESETS.first { it.tpView == 110 }
        val edits = PubgSaveSelection.toggleView(save, emptyMap(), tablet)
        // FP already holds the preset's 103, so only the real difference is pending. The patch
        // itself still writes the whole pair (PubgSavePatcher.VIEW_FIELDS).
        assertEquals(mapOf(PubgSavePatcher.FIELD_TP_VIEW to 110), edits)
        assertTrue(PubgSaveSelection.toggleView(save, edits, tablet).isEmpty())
    }

    @Test
    fun unsupportedFieldsAreNotOfferedAndAreNeverInvented() {
        val save = read(
            mapOf(
                PubgSavePatcher.FIELD_BATTLE_FPS to 8
                // LobbyFPS, FPSLevel and both camera fields are absent.
            )
        )
        assertFalse(PubgSaveSelection.fpsEditable(save))
        assertFalse(PubgSaveSelection.viewEditable(save))
        assertNull(PubgSaveSelection.selectedFps(save, emptyMap()))
        assertNull(PubgSaveSelection.selectedView(save, emptyMap()))
    }

    @Test
    fun aNonGvasFileOffersNoControls() {
        val save = PubgSavePatcher.ReadResult(emptyMap(), sizeBytes = 4, isGvas = false)
        assertFalse(PubgSaveSelection.fpsEditable(save))
        assertFalse(PubgSaveSelection.renderEditable(save))
        assertFalse(PubgSaveSelection.viewEditable(save))
    }

    @Test
    fun pendingEditWinsForDisplayAndDoesNotTouchOtherPendingFields() {
        val save = fullSave()
        val renderEdit = PubgSaveSelection.toggleRender(save, emptyMap(), 5)
        val fpsEdit = PubgSaveSelection.toggleFps(save, renderEdit, 120)
        assertEquals(5, PubgSaveSelection.effectiveValue(save, fpsEdit, PubgSavePatcher.FIELD_BATTLE_RENDER))
        assertEquals(8, PubgSaveSelection.effectiveValue(save, fpsEdit, PubgSavePatcher.FIELD_FPS_LEVEL))
        assertEquals(renderEdit, fpsEdit)
    }
}
