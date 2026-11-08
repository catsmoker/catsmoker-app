package com.catsmoker.app.features.editgamefiles

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RootMethodWiringTest {

    private val source = listOf(
        File("src/main/java/com/catsmoker/app/features/editgamefiles/EditGameFilesViewModel.kt"),
        File("app/src/main/java/com/catsmoker/app/features/editgamefiles/EditGameFilesViewModel.kt")
    ).firstOrNull { it.isFile }?.readText() ?: error("EditGameFilesViewModel.kt not found")

    @Test
    fun rootProfileSelectionUsesRootReadsAndDoesNotRequireShizuku() {
        assertTrue(source.contains("rootAction = { checkAndStartRootAction() }"))
        val rootAction = source.substringAfter("private fun checkAndStartRootAction()")
            .substringBefore("private fun performShizukuCopy")
        assertTrue(rootAction.contains("performPrivilegedCopy(config, rootSelected = true)"))

        val copy = source.substringAfter("private fun performPrivilegedCopy")
            .substringBefore("private fun handleZArchiverAction")
        assertTrue(copy.contains("if (rootSelected) readRootFileIfPresent(destPath)"))
        assertTrue(copy.contains("if (rootSelected) readRootFile(destPath)"))
        assertTrue(copy.contains("R.string.gf_success_root"))
    }

    @Test
    fun rootResetAndRestoreUseRootReadsWithoutShizukuPrechecks() {
        assertTrue(source.contains("resetViaShizuku(rootSelected = true)"))
        assertTrue(source.contains("restoreViaShizuku(entry, rootSelected = true)"))
        assertTrue(source.contains("if (!rootSelected && !Shizuku.pingBinder())"))
        assertTrue(source.contains("if (!rootSelected && Shizuku.checkSelfPermission()"))
        assertTrue(source.contains("if (rootSelected) readRootFileIfPresent(destPath)"))
    }
}
