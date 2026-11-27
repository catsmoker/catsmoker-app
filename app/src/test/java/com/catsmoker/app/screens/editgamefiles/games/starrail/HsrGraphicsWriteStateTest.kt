package com.catsmoker.app.screens.editgamefiles.games.starrail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that used to lose data, tested as a plain state transition.
 *
 * A successful graphics write re-reads the file and re-baselines both working copies. When that
 * ran over a QoL copy with pending edits, those edits were replaced by what the device held and the
 * dirty flag was cleared — unapplied work disappeared with no failure shown. [afterGraphicsWrite]
 * now keeps the working copy and moves only its baseline.
 *
 * No device, file or coroutine is involved, which is the point: the previous version of this rule
 * could only be checked by reading the code.
 */
class HsrGraphicsWriteStateTest {

    private val devicePrefs = HsrGamePreferences(textLanguage = 2, audioLanguage = 2)
    private val pendingPrefs = HsrGamePreferences(textLanguage = 9, audioLanguage = 2)

    private fun state(
        prefs: HsrGamePreferences? = devicePrefs,
        prefsDirty: Boolean = false,
        canUndoPrefs: Boolean = false,
        canRedoPrefs: Boolean = false
    ) = HsrGraphicsUiState(
        loading = false,
        settings = HsrGraphicsSettings(),
        gamePrefs = prefs,
        settingsDirty = true,
        pendingSettingsCount = 3,
        settingsExternalChanged = true,
        prefsDirty = prefsDirty,
        pendingPrefsCount = if (prefsDirty) 1 else 0,
        prefsExternalChanged = prefsDirty,
        canUndoSettings = true,
        canUndoPrefs = canUndoPrefs,
        canRedoPrefs = canRedoPrefs
    )

    @Test
    fun aPendingQoLCopySurvivesTheGraphicsWrite() {
        val before = state(prefs = pendingPrefs, prefsDirty = true, canUndoPrefs = true)
        val after = before.afterGraphicsWrite(
            freshSettings = HsrGraphicsSettings(),
            freshDevicePrefs = devicePrefs,
            pendingPrefs = pendingPrefs,
            prefsStillDirty = true,
            hasBackup = true
        )

        assertSame("the unapplied working copy must be kept", pendingPrefs, after.gamePrefs)
        assertTrue("it must still read as pending", after.prefsDirty)
        assertTrue("its undo history must stay reachable", after.canUndoPrefs)
        assertTrue("the file really did move under it", after.prefsExternalChanged)
        // The graphics side is genuinely applied, so it clears.
        assertFalse(after.settingsDirty)
        assertFalse(after.canUndoSettings)
    }

    @Test
    fun aCleanQoLCopyFollowsTheDevice() {
        val after = state(prefs = devicePrefs).afterGraphicsWrite(
            freshSettings = HsrGraphicsSettings(),
            freshDevicePrefs = HsrGamePreferences(textLanguage = 7),
            pendingPrefs = null,
            prefsStillDirty = false,
            hasBackup = true
        )

        assertEquals("a clean copy is replaced by what the device now holds", 7, after.gamePrefs?.textLanguage)
        assertFalse(after.prefsDirty)
        assertFalse(after.canUndoPrefs)
        assertFalse(after.canRedoPrefs)
        assertFalse(after.prefsExternalChanged)
    }

    @Test
    fun undoAvailabilityIsNotInventedByTheWrite() {
        // A copy with pending edits but nothing left to undo keeps exactly that: the write must not
        // hand it an undo step it never recorded.
        val before = state(prefs = pendingPrefs, prefsDirty = true, canUndoPrefs = false, canRedoPrefs = false)
        val after = before.afterGraphicsWrite(
            freshSettings = HsrGraphicsSettings(),
            freshDevicePrefs = devicePrefs,
            pendingPrefs = pendingPrefs,
            prefsStillDirty = true,
            hasBackup = true
        )
        assertFalse(after.canUndoPrefs)
        assertFalse(after.canRedoPrefs)
    }
}
