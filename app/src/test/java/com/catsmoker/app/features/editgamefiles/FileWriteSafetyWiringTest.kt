package com.catsmoker.app.features.editgamefiles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level locks for the write-safety fixes in the WuWa, GRID, HSR and backup-store paths.
 *
 * These classes need a device, a privileged shell or a SAF provider to execute, so a JVM test
 * cannot drive the branches. What it can do is make sure the branches exist: each assertion
 * below names a defect that used to report success over a write that never happened, or that
 * overwrote a file whose current bytes were never read.
 */
class FileWriteSafetyWiringTest {

    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("missing source under test: $relative")
    }

    private val wuwaManager by lazy {
        source("src/main/java/com/catsmoker/app/features/editgamefiles/wuwa/WuwaConfigManager.kt")
    }
    private val wuwaViewModel by lazy {
        source("src/main/java/com/catsmoker/app/features/editgamefiles/wuwa/WuwaConfigViewModel.kt")
    }
    private val gridManager by lazy {
        source("src/main/java/com/catsmoker/app/features/editgamefiles/grid/GridPreferencesManager.kt")
    }
    private val gridViewModel by lazy {
        source("src/main/java/com/catsmoker/app/features/editgamefiles/grid/GridViewModel.kt")
    }
    private val hsrManager by lazy {
        source("src/main/java/com/catsmoker/app/features/editgamefiles/hsr/HsrGameManager.kt")
    }
    private val backupStore by lazy {
        source("src/main/java/com/catsmoker/app/features/editgamefiles/ConfigBackupStore.kt")
    }

    @Test
    fun wuwaNeverDeletesOrOverwritesAFileItCouldNotRead() {
        assertTrue(
            "an unreadable SAF file must be left untouched",
            wuwaManager.contains("the existing file could not be read, so it was left untouched")
        )
        assertFalse(
            "delete-then-create destroyed the live config when the create was refused",
            wuwaManager.contains("existing?.delete()")
        )
        assertTrue(
            "a failed backup must skip the file, not push over the only copy",
            wuwaManager.contains("the current file could not be backed up, so it was left untouched")
        )
    }

    @Test
    fun wuwaRestoresTheBackupsOwnBytesAndVerifiesTheHashMonitor() {
        assertTrue(
            "a restore must not round-trip through String (invalid UTF-8 became U+FFFD)",
            wuwaManager.contains("deployBytesOver(ShellChannel(), configs)")
        )
        assertTrue(
            "empty read-back is not a verified hash sync",
            wuwaManager.contains("read-back was empty, so it could not be verified")
        )
        assertTrue(
            "a file without a verified digest must not write an empty Hash section",
            wuwaManager.contains("if (hash.isBlank()) continue")
        )
        assertTrue(
            "the restore toast must follow a verified read-back, not the write call",
            wuwaViewModel.contains("s.files.all { it.pushed && it.verified }")
        )
        assertTrue(
            "a failed hash sync makes the deploy report an issue, not a clean success",
            wuwaViewModel.contains("s.hashSynced != false")
        )
    }

    @Test
    fun gridDistinguishesAbsentFromUnreadableAndBacksUpItsRestores() {
        assertTrue(
            "a shell pull failure must be a failed backup, not 'no prior file'",
            gridManager.contains("test\", \"-f\", absolutePath")
        )
        assertTrue(
            "the SAF apply must refuse a file it could not read",
            gridManager.contains("could not read the current file to back it up — refusing to overwrite")
        )
        assertTrue(
            "a restore must stash the live file and stop the game first",
            gridManager.contains("pushBytes(bytes, restoring = true)")
        )
        assertTrue(
            "a restore's success toast must be gated on the read-back",
            gridViewModel.contains("R.string.gf_backup_restored else R.string.gf_grid_applied_unverified")
        )
    }

    @Test
    fun hsrAbortsUnbackedWritesAndVerifiesEverythingItWrote() {
        assertTrue(
            "a null pre-write read must abort instead of backing up nothing",
            hsrManager.contains("could not read \$path to back it up — refusing to overwrite")
        )
        assertTrue(
            "an unreadable backup is not 'no backup yet'",
            hsrManager.contains("the newest backup is unreadable")
        )
        assertTrue(
            "a restore must stop the game before reading the live file",
            hsrManager.contains("shellRunner.execSafeResult(\"am\", \"force-stop\", pkg)")
        )
        assertTrue(
            "graphics verification must cover every sibling key, not six hand-picked ones",
            hsrManager.contains("for ((field, key) in HsrGraphicsSettings.SIBLING_INT_KEYS)")
        )
        assertTrue(
            "the QoL verification must cover the blacklists it writes",
            hsrManager.contains("video blacklist \$videoBlacklist")
        )
    }

    @Test
    fun twoBackupsInTheSameMillisecondDoNotOverwriteEachOther() {
        assertTrue(
            "the backup name must walk forward instead of reusing a taken name",
            backupStore.contains("while (file.exists())")
        )
    }
}
