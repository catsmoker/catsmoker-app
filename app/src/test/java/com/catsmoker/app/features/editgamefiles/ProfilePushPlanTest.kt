package com.catsmoker.app.features.editgamefiles

import com.catsmoker.app.features.editgamefiles.pubg.PubgSavePatcher
import com.catsmoker.app.shared.data.model.GameProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The push-payload rules every delivery channel shares.
 *
 * The defect these lock down: the SAF and Shizuku channels used to keep the file already on the
 * device for a PUBG `Active.sav` profile, so "Apply Profile" rewrote the device's bytes with
 * themselves and reported success while changing nothing. A profile now says what it changes
 * ([GameProfile.saveEdits]) and the plan patches exactly those fields — or refuses, never
 * replacing a player's save with one reference author's whole file.
 */
class ProfilePushPlanTest {

    private fun bundled(path: String): ByteArray {
        val file = File("src/main/assets/PUBG/$path")
        assumeTrue("bundled save not found at ${file.absolutePath}", file.isFile)
        return file.readBytes()
    }

    /** The small older-schema real-world save — carries the FPS fields, not the camera pair. */
    private fun smallSchema(): ByteArray {
        val file = File("src/test/resources/pubg_small_schema_fixture.sav")
        assumeTrue("small-schema save not found at ${file.absolutePath}", file.isFile)
        return file.readBytes()
    }

    private fun profile(
        edits: Map<String, Int> = emptyMap(),
        preserveTail: Boolean = false
    ) = GameProfile(
        labelRes = 0,
        assetPath = "unused",
        preserveDeviceProfileBackup = preserveTail,
        saveEdits = edits
    )

    @Test
    fun aSaveTheGameHasNotWrittenIsSeededWithTheBundledProfile() {
        val asset = bundled("MaxFPS/Active.sav")
        val plan = ProfilePushPlan.resolve(
            existing = null,
            asset = asset,
            profile = profile(PubgSavePatcher.fpsEdits(120)),
            requiresDeviceModel = false
        )
        assertTrue(plan is ProfilePushPlan.Result.Bytes)
        assertTrue("the seed is the bundled file verbatim", (plan as ProfilePushPlan.Result.Bytes).bytes.contentEquals(asset))
        assertTrue("a seed changed nothing inside a save", plan.patchedFields.isEmpty())

        val empty = ProfilePushPlan.resolve(asset, asset, profile(PubgSavePatcher.fpsEdits(120)), false)
        assertTrue("an empty file is a missing file", empty is ProfilePushPlan.Result.Bytes)
    }

    @Test
    fun anExistingSaveIsPatchedInPlaceInsteadOfReplaced() {
        // The tablet blob is the device's "own" save here: 7/7/7 with the wide camera pair.
        val existing = bundled("TabletView/Active.sav")
        val plan = ProfilePushPlan.resolve(
            existing = existing,
            asset = bundled("MaxFPS/Active.sav"),
            profile = profile(PubgSavePatcher.fpsEdits(120)),
            requiresDeviceModel = false
        )
        assertTrue(plan is ProfilePushPlan.Result.Bytes)
        val bytes = (plan as ProfilePushPlan.Result.Bytes).bytes

        assertEquals("a patch never changes the file's length", existing.size, bytes.size)
        assertEquals(PubgSavePatcher.fpsEdits(120).keys.toList(), plan.patchedFields)
        val read = PubgSavePatcher.read(bytes)
        assertEquals(8, value(read, PubgSavePatcher.FIELD_BATTLE_FPS))
        assertEquals(8, value(read, PubgSavePatcher.FIELD_LOBBY_FPS))
        assertEquals(8, value(read, PubgSavePatcher.FIELD_FPS_LEVEL))
        // Everything the profile is not about survives: the wide view, render tiers, the
        // observed settings (sensitivity, joystick, art quality...).
        assertEquals(110, value(read, PubgSavePatcher.FIELD_TP_VIEW))
        assertEquals(103, value(read, PubgSavePatcher.FIELD_FP_VIEW))
        assertEquals(1, value(read, PubgSavePatcher.FIELD_BATTLE_RENDER))
        assertEquals(1, value(read, PubgSavePatcher.FIELD_LOBBY_RENDER))
        assertEquals(value(PubgSavePatcher.read(existing), "ArtQuality"), value(read, "ArtQuality"))
    }

    @Test
    fun anUnreadableLayoutRefusesTheWholePush() {
        // The small older-schema save carries the FPS fields but not the camera pair; the
        // tablet profile must not be forced onto it by replacing the file.
        val existing = smallSchema()
        val plan = ProfilePushPlan.resolve(
            existing = existing,
            asset = bundled("TabletView/Active.sav"),
            profile = profile(PubgSavePatcher.viewEdits(PubgSavePatcher.TABLET_VIEW)),
            requiresDeviceModel = false
        )
        assertTrue(plan is ProfilePushPlan.Result.Refused)
        assertTrue(
            "the refusal names the field the file lacks",
            (plan as ProfilePushPlan.Result.Refused).fieldReasons.keys.containsAll(listOf("TpViewValue", "FpViewValue"))
        )
    }

    @Test
    fun aPreserveTailProfileMergesTheDeviceSection() {
        val device = (
            "[UserCustom DeviceProfile]\n+CVar=old\n" +
                BACKUP_SECTION + "\n+CVar=backup\n"
            ).toByteArray()
        val template = "[UserCustom DeviceProfile]\n+CVar=new\n".toByteArray()
        val plan = ProfilePushPlan.resolve(
            existing = device,
            asset = template,
            profile = profile(preserveTail = true),
            requiresDeviceModel = false
        )
        assertTrue(plan is ProfilePushPlan.Result.Bytes)
        val text = String((plan as ProfilePushPlan.Result.Bytes).bytes)
        assertTrue("the template's active section lands", text.startsWith("[UserCustom DeviceProfile]\n+CVar=new"))
        assertTrue("the device's own backup tail survives", text.endsWith("[BackUp DeviceProfile]\n+CVar=backup\n"))
    }

    @Test
    fun aModelNamedTemplateAlwaysReplacesTheStockFile() {
        val template = "{\"model\": \"THIS_DEVICE\"}".toByteArray()
        val plan = ProfilePushPlan.resolve(
            existing = "stock config".toByteArray(),
            asset = template,
            profile = profile(),
            requiresDeviceModel = true
        )
        assertTrue(plan is ProfilePushPlan.Result.Bytes)
        assertTrue((plan as ProfilePushPlan.Result.Bytes).bytes.contentEquals(template))
    }

    @Test
    fun anExistingFileWithNothingToChangeIsNeverRewrittenAsASuccess() {
        val existing = bundled("MaxFPS/Active.sav")
        val plan = ProfilePushPlan.resolve(existing, existing, profile(), requiresDeviceModel = false)
        assertTrue(
            "rewriting the device's bytes with themselves is the defect this rule bans",
            plan is ProfilePushPlan.Result.NothingToApply
        )
    }

    @Test
    fun manualCopyIsHiddenForProfilesThatMustReadTheDeviceFile() {
        assertTrue(ProfilePushPlan.needsExistingFile(profile(PubgSavePatcher.fpsEdits(120))))
        assertTrue(ProfilePushPlan.needsExistingFile(profile(preserveTail = true)))
        assertFalse("a whole-file template can be exported manually", ProfilePushPlan.needsExistingFile(profile()))
        assertFalse("no profile list means no per-profile rule", ProfilePushPlan.needsExistingFile(null))
    }

    private fun value(read: PubgSavePatcher.ReadResult, field: String): Int =
        (read.fields[field] as PubgSavePatcher.FieldOutcome.Value).value

    private companion object {
        const val BACKUP_SECTION = "[BackUp DeviceProfile]"
    }
}
