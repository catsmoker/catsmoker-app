package com.catsmoker.app.features.editgamefiles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wiring lock for the PUBG profile-push fix.
 *
 * The rules themselves are tested in [ProfilePushPlanTest]; this file guards the places that
 * have to *use* them, because the defect lived in the two channel bodies, not in a pure helper:
 * both the Shizuku and SAF paths rewrote the device's own `Active.sav` bytes with themselves and
 * reported success, and the Shizuku path ignored the `mkdir`/`cp` exit codes entirely.
 *
 * Source-level because the channels need a device, a SAF provider and a privileged shell — a
 * unit test cannot execute them, and a green build would otherwise prove nothing about the
 * branches that were wrong.
 */
class PubgPushWiringTest {

    private val viewModel = source("EditGameFilesViewModel.kt")
    private val screen = source("EditGameFilesScreen.kt")

    private fun source(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/catsmoker/app/features/editgamefiles/$name"),
            File("app/src/main/java/com/catsmoker/app/features/editgamefiles/$name")
        )
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("missing source under test: $name")
    }

    @Test
    fun bothDirectChannelsAskTheSharedPlanForTheirPayload() {
        val uses = Regex("""ProfilePushPlan\.resolve\(""").findAll(viewModel).count()
        assertTrue(
            "SAF and Shizuku must both resolve the payload through ProfilePushPlan (found $uses)",
            uses >= 2
        )
        assertFalse(
            "the self-copy branch that rewrote the device's bytes is back",
            viewModel.contains("else -> existingBytes")
        )
    }

    @Test
    fun theShizukuChannelChecksItsCommandsAndReadsTheFileBack() {
        assertTrue("mkdir's exit code must be inspected", viewModel.contains("mkdir.isSuccess"))
        assertTrue("cp's exit code must be inspected", viewModel.contains("copy.isSuccess"))
        assertTrue(
            "the push must be verified by reading the device's file back",
            viewModel.contains("readFileDirect(destPath, forceBind = true)")
        )
        assertTrue(
            "the read-back must be compared with the intended bytes",
            viewModel.contains("!readBack.contentEquals(inputBytes)")
        )
        assertTrue(
            "a seeded file must end up owned by the game, not by the privileged shell",
            viewModel.contains("chown")
        )
    }

    @Test
    fun aFailedBackupAbortsEveryProfileWrite() {
        val refusals = Regex("""gf_backup_failed_refused""").findAll(viewModel).count()
        assertTrue(
            "SAF, Shizuku and the save apply must all refuse when the backup cannot be written (found $refusals)",
            refusals >= 3
        )
    }

    @Test
    fun theSaveApplyComparesTheReadBackWithTheRequest() {
        assertTrue(
            "apply must compare requested values with the file's own",
            viewModel.contains("PubgSaveVerification.mismatches")
        )
        assertTrue(
            "a mismatch must reach the user",
            viewModel.contains("gf_save_verify_failed")
        )
        assertTrue(
            "chips are only cleared by a verified apply",
            viewModel.contains("saveEdits = if (verified) emptyMap()")
        )
    }

    @Test
    fun thePubgProfilesDeclareWhatTheyChange() {
        assertTrue(
            "the 120 unlock profile patches the FPS fields",
            viewModel.contains("saveEdits = PubgSavePatcher.fpsEdits(120)")
        )
        assertTrue(
            "the tablet profile patches the camera pair",
            viewModel.contains("saveEdits = PubgSavePatcher.viewEdits(PubgSavePatcher.TABLET_VIEW)")
        )
    }

    @Test
    fun theManualCopyRowIsHiddenForProfilesThatNeedTheDeviceFile() {
        assertTrue(screen.contains("selectedProfileNeedsExistingFile"))
        assertFalse(
            "the old preserve-only flag missed the patched saves",
            screen.contains("selectedProfilePreservesBackup")
        )
    }
}
