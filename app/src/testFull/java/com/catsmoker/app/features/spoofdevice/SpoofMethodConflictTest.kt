package com.catsmoker.app.features.spoofdevice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the device-spoofing method-exclusivity decision: the Xposed framework
 * path (LSPosed or Vector) and the generated Catsmoker module rewrite the same
 * device identity through different channels, so both being armed at once is
 * a conflict. The app resolves the readable case automatically through
 * Vector's own supported CLI, and the tests here pin exactly when that is
 * allowed: only a positively enabled Vector manager row plus an installed
 * Catsmoker module.
 */
class SpoofMethodConflictTest {

    @Test
    fun conflictNeedsInstalledModuleAndEnabledVectorRow() {
        assertTrue(
            SpoofMethodConflict.isConflict(
                vectorModuleEnabled = true,
                xposedLoaded = false,
                magisk = SpoofMethodConflict.MagiskModuleStatus.INSTALLED
            )
        )
    }

    @Test
    fun disabledVectorRowWinsOverAStaleObservedLoad() {
        // The loaded proof can outlive a successful disable (the target process is still
        // alive). Keying the banner on that proof would disable the module forever.
        assertFalse(
            SpoofMethodConflict.isConflict(
                vectorModuleEnabled = false,
                xposedLoaded = true,
                magisk = SpoofMethodConflict.MagiskModuleStatus.INSTALLED
            )
        )
    }

    @Test
    fun unknownVectorRowStillWarnsWhenALoadWasObserved() {
        assertTrue(
            SpoofMethodConflict.isConflict(
                vectorModuleEnabled = null,
                xposedLoaded = true,
                magisk = SpoofMethodConflict.MagiskModuleStatus.INSTALLED
            )
        )
        assertFalse(
            SpoofMethodConflict.isConflict(
                vectorModuleEnabled = null,
                xposedLoaded = false,
                magisk = SpoofMethodConflict.MagiskModuleStatus.INSTALLED
            )
        )
    }

    @Test
    fun conflictIsFalseWithoutInstalledModule() {
        val notInstalled = SpoofMethodConflict.MagiskModuleStatus.entries -
            SpoofMethodConflict.MagiskModuleStatus.INSTALLED
        for (magisk in notInstalled) {
            assertFalse(
                "$magisk with an enabled Vector row must not conflict",
                SpoofMethodConflict.isConflict(
                    vectorModuleEnabled = true,
                    xposedLoaded = true,
                    magisk = magisk
                )
            )
        }
    }

    @Test
    fun onlyAPositivelyEnabledVectorRowMayBeDisabledAutomatically() {
        assertTrue(
            SpoofMethodConflict.shouldDisableVector(
                vectorModuleEnabled = true,
                magisk = SpoofMethodConflict.MagiskModuleStatus.INSTALLED
            )
        )
        assertFalse(
            SpoofMethodConflict.shouldDisableVector(
                vectorModuleEnabled = null,
                magisk = SpoofMethodConflict.MagiskModuleStatus.INSTALLED
            )
        )
        assertFalse(
            SpoofMethodConflict.shouldDisableVector(
                vectorModuleEnabled = false,
                magisk = SpoofMethodConflict.MagiskModuleStatus.INSTALLED
            )
        )
        for (magisk in SpoofMethodConflict.MagiskModuleStatus.entries -
            SpoofMethodConflict.MagiskModuleStatus.INSTALLED
        ) {
            assertFalse(
                "$magisk must never cause a Vector change",
                SpoofMethodConflict.shouldDisableVector(
                    vectorModuleEnabled = true,
                    magisk = magisk
                )
            )
        }
    }

    @Test
    fun assignConfirmsOnlyForNewUsageAgainstInstalledModule() {
        // Clearing an assignment removes Xposed usage — never gated.
        assertFalse(
            SpoofMethodConflict.needsAssignConfirm(
                profileId = null,
                magisk = SpoofMethodConflict.MagiskModuleStatus.INSTALLED
            )
        )
        // A fresh assignment while the Magisk module is installed needs the tap.
        assertTrue(
            SpoofMethodConflict.needsAssignConfirm(
                profileId = "some-profile-id",
                magisk = SpoofMethodConflict.MagiskModuleStatus.INSTALLED
            )
        )
        // Disabled, absent, or unreadable module state never gates assignment.
        for (magisk in SpoofMethodConflict.MagiskModuleStatus.entries -
            SpoofMethodConflict.MagiskModuleStatus.INSTALLED
        ) {
            assertFalse(
                "$magisk must not gate assignment",
                SpoofMethodConflict.needsAssignConfirm(profileId = "some-profile-id", magisk = magisk)
            )
        }
    }

    @Test
    fun generateConfirmsWhenXposedMayBeActive() {
        // Live heartbeat: the other method is definitely armed.
        assertTrue(
            SpoofMethodConflict.needsGenerateConfirm(
                moduleActive = true, hasAssignments = false, hasLadders = false
            )
        )
        // Assignments without a heartbeat are latent, not live — but the next
        // reboot would arm them, so generating the second method still confirms.
        assertTrue(
            SpoofMethodConflict.needsGenerateConfirm(
                moduleActive = false, hasAssignments = true, hasLadders = false
            )
        )
        assertTrue(
            SpoofMethodConflict.needsGenerateConfirm(
                moduleActive = false, hasAssignments = false, hasLadders = true
            )
        )
        // Nothing claimed on the Xposed side: no gate.
        assertFalse(
            SpoofMethodConflict.needsGenerateConfirm(
                moduleActive = false, hasAssignments = false, hasLadders = false
            )
        )
    }

    @Test
    fun magiskPresenceMapsToHonestStates() {
        // Without privilege the directory cannot be read — Unknown, never Absent.
        assertEquals(
            SpoofMethodConflict.MagiskModuleStatus.UNKNOWN,
            SpoofMethodConflict.parseMagiskModuleStatus(
                hasPrivilege = false, dirExists = false, disableExists = false
            )
        )
        assertEquals(
            SpoofMethodConflict.MagiskModuleStatus.ABSENT,
            SpoofMethodConflict.parseMagiskModuleStatus(
                hasPrivilege = true, dirExists = false, disableExists = false
            )
        )
        assertEquals(
            SpoofMethodConflict.MagiskModuleStatus.DISABLED,
            SpoofMethodConflict.parseMagiskModuleStatus(
                hasPrivilege = true, dirExists = true, disableExists = true
            )
        )
        assertEquals(
            SpoofMethodConflict.MagiskModuleStatus.INSTALLED,
            SpoofMethodConflict.parseMagiskModuleStatus(
                hasPrivilege = true, dirExists = true, disableExists = false
            )
        )
    }
}
