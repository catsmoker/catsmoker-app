package com.catsmoker.app.screens.gaming.mode

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootRecoveryPolicyTest {

    @Test
    fun noPersistedPackageOrAppOpRecordsNeedNoRecovery() {
        assertFalse(BootRecoveryPolicy.hasRecoveryRecords(emptySet(), emptySet()))
    }

    @Test
    fun packageRecordNeedsRecoveryEvenWhenTheSessionFlagWasCleared() {
        assertTrue(BootRecoveryPolicy.hasRecoveryRecords(setOf("com.frozen"), emptySet()))
    }

    @Test
    fun appOpRecordNeedsRecoveryWithoutSuspendedPackages() {
        assertTrue(BootRecoveryPolicy.hasRecoveryRecords(emptySet(), setOf("com.restricted=ignore")))
    }

    @Test
    fun processRelaunchAndDeviceBootUseTheSameRecoveryWork() {
        val root = File("src/main/java/com/catsmoker/app")
        val app = File(root, "system/CatsmokerApp.kt").readText()
        val receiver = File(root, "screens/gaming/mode/BootRecoveryReceiver.kt").readText()
        assertTrue(app.contains("BootRecoveryReceiver.enqueueIfNeeded(this@CatsmokerApp)"))
        assertTrue(receiver.contains("Intent.ACTION_BOOT_COMPLETED) enqueueIfNeeded(context)"))
        assertTrue(receiver.contains("ExistingWorkPolicy.KEEP"))
    }
}
