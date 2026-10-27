package com.catsmoker.app.features.editgamefiles.pubg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The apply path's comparison between "what was requested" and "what the file holds now".
 *
 * The editor used to print the read-back summary next to "applied" without comparing it, so a
 * write that never committed (refused by the storage layer, reverted by a running game) still
 * read as success and the chips were cleared. These tests pin the distinction between a
 * verified value, a different value, and a field the file does not carry at all.
 */
class PubgSaveVerificationTest {

    private fun fixture(): ByteArray {
        val file = File("src/test/resources/pubg_test_fixture.sav")
        assumeTrue("fixture not found at ${file.absolutePath}", file.isFile)
        return file.readBytes()
    }

    @Test
    fun aMatchingReadBackIsVerified() {
        val read = PubgSavePatcher.read(fixture())
        // The fixture holds 6/6/4 — request exactly that.
        val requested = mapOf(
            PubgSavePatcher.FIELD_BATTLE_FPS to 6,
            PubgSavePatcher.FIELD_LOBBY_FPS to 6,
            PubgSavePatcher.FIELD_FPS_LEVEL to 4
        )
        assertTrue(PubgSaveVerification.mismatches(read, requested).isEmpty())
    }

    @Test
    fun aDifferentValueIsNamedWithBothNumbers() {
        val read = PubgSavePatcher.read(fixture())
        val mismatches = PubgSaveVerification.mismatches(read, PubgSavePatcher.fpsEdits(120))
        assertEquals(3, mismatches.size)
        assertTrue(mismatches.any {
            it.field == PubgSavePatcher.FIELD_BATTLE_FPS && it.expected == 8 && it.actual == 6
        })
    }

    @Test
    fun anAbsentFieldIsNeverReassuredAsZero() {
        val read = PubgSavePatcher.read(fixture())
        // TpViewValue is absent from the fixture; the mismatch carries null, not 0.
        val mismatch = PubgSaveVerification.mismatches(
            read,
            mapOf(PubgSavePatcher.FIELD_TP_VIEW to 110)
        ).single()
        assertEquals(PubgSavePatcher.FIELD_TP_VIEW, mismatch.field)
        assertEquals(110, mismatch.expected)
        assertEquals(null, mismatch.actual)
    }

    @Test
    fun aPatchedSaveAlwaysVerifies() {
        val patched = PubgSavePatcher.patch(fixture(), PubgSavePatcher.fpsEdits(120))
        val bytes = (patched as PubgSavePatcher.PatchResult.Ok).data
        val readBack = PubgSavePatcher.read(bytes)
        assertTrue(PubgSaveVerification.mismatches(readBack, PubgSavePatcher.fpsEdits(120)).isEmpty())
    }
}
