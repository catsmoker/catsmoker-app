package com.catsmoker.app.features.editgamefiles.pubg

import com.catsmoker.app.shared.data.model.GameUploadFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the PUBG import contract: exactly two supported files, a destination resolved from the
 * selected package, real format validation, and the INI merge that must never overwrite the
 * device's own `[BackUp DeviceProfile]` section.
 *
 * The bug this replaced: one untyped picker always wrote `Active.sav`, so importing
 * `UserCustom.ini` silently replaced the save with text, and a bad pick was copied verbatim.
 */
class PubgUploadPlanTest {

    private val external = "/storage/emulated/0"

    @Test
    fun eachPubgPackageResolvesItsOwnSaveAndIniDestinations() {
        val variants = listOf(
            "com.tencent.ig",
            "com.pubg.krmobile",
            "com.vng.pubgmobile",
            "com.pubg.imobile",
            "com.rekoo.pubgm"
        )
        variants.forEach { pkg ->
            val targets = PubgUploadPlan.targets(pkg)
            assertEquals(2, targets.size)
            assertEquals("Active.sav", targets[0].fileName)
            assertEquals(GameUploadFormat.GVAS_SAVE, targets[0].format)
            assertEquals(
                "$external/Android/data/$pkg/files/UE4Game/ShadowTrackerExtra/" +
                    "ShadowTrackerExtra/Saved/SaveGames/Active.sav",
                targets[0].destination(external)
            )
            assertEquals("UserCustom.ini", targets[1].fileName)
            assertEquals(GameUploadFormat.USER_CUSTOM_INI, targets[1].format)
            assertEquals(
                "$external/Android/data/$pkg/files/UE4Game/ShadowTrackerExtra/" +
                    "ShadowTrackerExtra/Saved/Config/Android/UserCustom.ini",
                targets[1].destination(external)
            )
        }
    }

    @Test
    fun gvasValidationAcceptsOnlyTheSaveMagic() {
        assertNull(PubgUploadPlan.validate("GVAS".toByteArray() + ByteArray(8), GameUploadFormat.GVAS_SAVE))
        assertEquals(
            PubgUploadPlan.Invalid.NOT_GVAS,
            PubgUploadPlan.validate("[UserCustom DeviceProfile]".toByteArray(), GameUploadFormat.GVAS_SAVE)
        )
        assertEquals(
            PubgUploadPlan.Invalid.EMPTY,
            PubgUploadPlan.validate(ByteArray(0), GameUploadFormat.GVAS_SAVE)
        )
    }

    @Test
    fun iniValidationRequiresTheDeviceProfileHeader() {
        assertNull(
            PubgUploadPlan.validate(
                "[UserCustom DeviceProfile]\n+CVars=AA\n".toByteArray(),
                GameUploadFormat.USER_CUSTOM_INI
            )
        )
        assertEquals(
            PubgUploadPlan.Invalid.NOT_USER_CUSTOM_INI,
            PubgUploadPlan.validate("GVAS....".toByteArray(), GameUploadFormat.USER_CUSTOM_INI)
        )
        assertEquals(
            PubgUploadPlan.Invalid.EMPTY,
            PubgUploadPlan.validate(ByteArray(0), GameUploadFormat.USER_CUSTOM_INI)
        )
    }

    @Test
    fun iniValidationRefusesAFileThatOnlyCarriesABackupSection() {
        // Importing this would truncate to an empty active section and rewrite the destination
        // with nothing — a silent wipe reported as a successful import.
        val backupOnly = (
            "[BackUp DeviceProfile]\n" +
                "SOME-OTHER-DEVICE\n" +
                "[UserCustom DeviceProfile]\n"
            ).toByteArray()
        assertEquals(
            PubgUploadPlan.Invalid.NOT_USER_CUSTOM_INI,
            PubgUploadPlan.validate(backupOnly, GameUploadFormat.USER_CUSTOM_INI)
        )
    }

    @Test
    fun saveImportWritesTheUsersBytesVerbatim() {
        val imported = "GVAS".toByteArray() + ByteArray(16) { it.toByte() }
        assertArrayEquals(
            imported,
            PubgUploadPlan.payload(imported, existing = "GVAS-old".toByteArray(), GameUploadFormat.GVAS_SAVE)
        )
    }

    @Test
    fun iniImportKeepsTheDeviceBackupAndDropsTheImportedOne() {
        val imported = (
            "[UserCustom DeviceProfile]\n" +
                "+CVars=AA\n" +
                "[BackUp DeviceProfile]\n" +
                "IMPORTED-TAIL\n"
            ).toByteArray()
        val existing = (
            "[UserCustom DeviceProfile]\n" +
                "OLD\n" +
                "[BackUp DeviceProfile]\n" +
                "DEVICE-TAIL\n"
            ).toByteArray()

        val payload = String(
            PubgUploadPlan.payload(imported, existing, GameUploadFormat.USER_CUSTOM_INI)
        )
        assertTrue("active section must be the imported one", payload.contains("+CVars=AA"))
        assertTrue("device backup tail must survive", payload.contains("DEVICE-TAIL"))
        assertTrue("an imported backup tail must not overwrite the device's", !payload.contains("IMPORTED-TAIL"))
        assertTrue(
            "the device tail must start at its own marker",
            payload.indexOf("[BackUp DeviceProfile]") < payload.indexOf("DEVICE-TAIL")
        )
    }

    @Test
    fun iniImportToAMissingFileUsesOnlyTheActiveSection() {
        val imported = "[UserCustom DeviceProfile]\n+CVars=AA".toByteArray()
        val payload = String(
            PubgUploadPlan.payload(imported, existing = null, GameUploadFormat.USER_CUSTOM_INI)
        )
        assertTrue(payload.startsWith("[UserCustom DeviceProfile]"))
        assertTrue(payload.contains("+CVars=AA"))
        assertTrue("a fresh file has no backup section to invent", !payload.contains("[BackUp DeviceProfile]"))
    }
}
