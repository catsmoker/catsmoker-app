package com.catsmoker.app.screens.gaming.mode

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GamingAppRecoveryWiringTest {
    private val appChanges = File("src/main/java/com/catsmoker/app/screens/gaming/mode/GamingAppChanges.kt").readText()

    @Test
    fun recoveryEntriesAreCommittedBeforeAndroidCanApplyEachChange() {
        val apply = appChanges.substringAfter("suspend fun apply(").substringBefore("suspend fun restoreSuspensions(")
        assertTrue(apply.indexOf("""putStringSet("affected_pkgs", pending.toSet()).commit()""") in
            0 until apply.indexOf("PrivilegeCommands.suspendArgs("))
        assertTrue(apply.indexOf("""putStringSet("appops_prior", AppOpsRecord.encode(appOps)).commit()""") in
            0 until apply.indexOf("PrivilegeCommands.appOpsSetArgs("))
        assertTrue(apply.contains("prior[it] == false"))
    }

    @Test
    fun restoreUsesRecordedTargetsAndTheirOriginalCallerIdentity() {
        val restore = appChanges.substringAfter("suspend fun restoreSuspensions()").substringBefore("suspend fun restoreAppOps()")
        assertTrue(restore.contains("for (pkg in recorded.sorted())"))
        assertTrue(restore.contains("it.uid == savedUid"))
        assertTrue(restore.contains("unsuspendArgs(pkg), identity = identity"))
        assertTrue(restore.contains("live[it] != false"))
    }
}
