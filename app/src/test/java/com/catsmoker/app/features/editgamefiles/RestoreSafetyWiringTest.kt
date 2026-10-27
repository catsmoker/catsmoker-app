package com.catsmoker.app.features.editgamefiles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wiring lock for the restore and root-only write paths.
 *
 * These channels need SAF providers and a privileged shell, so a unit test cannot execute them —
 * but the defects they fix were *missing* branches, not wrong arithmetic: a restore announced
 * success without reading the file back, a root-only apply overwrote the game's INI with the
 * backup result discarded, and an unreadable SAF file was treated as absent and seeded over.
 * A source-level check is the only thing that stops those branches from quietly disappearing
 * again in a refactor.
 *
 * The user-visible promise these lock down is `gf_restore_backup_note` ("A copy of the current
 * file is saved before the restore"): every restore channel must actually take that copy.
 */
class RestoreSafetyWiringTest {

    private val viewModel = source("EditGameFilesViewModel.kt")

    private fun source(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/catsmoker/app/features/editgamefiles/$name"),
            File("app/src/main/java/com/catsmoker/app/features/editgamefiles/$name")
        )
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("missing source under test: $name")
    }

    private fun region(start: String, end: String): String {
        val body = viewModel.substringAfter(start, "")
        assertTrue("missing function: $start", body.isNotEmpty())
        return body.substringBefore(end)
    }

    @Test
    fun theSafRestoreBacksUpWhatItReplacesAndReadsTheResultBack() {
        val body = region("private fun performSafRestore", "// ------------------------------------------------------ root-only INI")
        assertTrue(
            "a present but unreadable file must be refused, not overwritten",
            body.contains("gf_existing_unreadable")
        )
        assertTrue(
            "the replaced bytes must be stashed before the write",
            body.contains("backupStore.save(config.packageName, targetName, current)")
        )
        assertTrue(
            "a failed write must not read as a restore",
            body.contains("gf_backup_failed_refused")
        )
        assertTrue(
            "the provider's bytes must be compared with the backup being restored",
            body.contains("readBack.contentEquals(bytes)")
        )
        assertTrue(
            "an unverified write must say so instead of claiming success",
            body.contains("gf_written_no_readback")
        )
    }

    @Test
    fun theRootOnlyApplyRefusesWithoutABackupAndChecksTheMode() {
        val body = region("private fun applyRootOnlyIni", "/** Restores one of a root-only config")
        assertTrue(
            "the INI must not be overwritten when its backup could not be written",
            body.contains("backupStore.save(config.packageName, config.saveFile, original) == null")
        )
        assertTrue(body.contains("gf_backup_failed_refused"))
        assertTrue(
            "chmod 0440 is the reference lock; its refusal must be reported",
            body.contains("chmod.isSuccess")
        )
    }

    @Test
    fun theRootOnlyRestoreIsReversibleAndChecksOwnershipAndBytes() {
        val body = region("private fun restoreRootOnlyIni", "/**\n     * Reads a root-only file")
        assertTrue(
            "a present but unreadable INI must be refused, not replaced",
            body.contains("gf_existing_unreadable")
        )
        assertTrue(
            "the replaced bytes must be stashed before the restore",
            body.contains("backupStore.save(config.packageName, config.saveFile, current)")
        )
        assertTrue("a refused chown must not read as success", body.contains("chown.isSuccess"))
        assertTrue("a refused chmod must not read as success", body.contains("chmod.isSuccess"))
        assertTrue(
            "the bytes the device now holds must be compared with the backup",
            body.contains("readBack.contentEquals(bytes)")
        )
    }

    @Test
    fun anUnreadableSafFileIsNeverTreatedAsASeedPath() {
        val body = region("private fun performSafFileCopy", "fun onStoragePermissionResult")
        assertTrue(
            "the profile push must refuse an existing file it cannot read",
            body.contains("gf_existing_unreadable")
        )
        assertFalse(
            "length() > 0 collapsed unreadable into absent and seeded over the user's file",
            body.contains("existingFile.length() > 0L")
        )
    }
}
