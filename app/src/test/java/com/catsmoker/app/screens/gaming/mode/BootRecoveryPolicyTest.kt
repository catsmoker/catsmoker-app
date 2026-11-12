package com.catsmoker.app.screens.gaming.mode

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
}
