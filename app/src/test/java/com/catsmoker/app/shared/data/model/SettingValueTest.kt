package com.catsmoker.app.shared.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the restore read-back comparison used by GamingEngine's setting restore path. */
class SettingValueTest {

    @Test
    fun numericProviderNormalisationStillMatches() {
        val recorded = SettingValue("120", existed = true)
        assertTrue(recorded.matchesAfterRestore(SettingValue("120", existed = true)))
        assertTrue(recorded.matchesAfterRestore(SettingValue("120.0", existed = true)))
    }

    @Test
    fun aDifferentValueIsARefusal() {
        val recorded = SettingValue("120", existed = true)
        assertFalse(recorded.matchesAfterRestore(SettingValue("60", existed = true)))
        assertFalse(recorded.matchesAfterRestore(SettingValue("", existed = false)))
    }

    @Test
    fun nonNumericValuesCompareExactly() {
        val recorded = SettingValue("adaptive_bucket", existed = true)
        assertTrue(recorded.matchesAfterRestore(SettingValue("adaptive_bucket", existed = true)))
        assertFalse(recorded.matchesAfterRestore(SettingValue("restricted_bucket", existed = true)))
    }

    @Test
    fun aMissingSettingMustComeBackMissing() {
        val recorded = SettingValue("", existed = false)
        assertTrue(recorded.matchesAfterRestore(SettingValue("", existed = false)))
        assertFalse(recorded.matchesAfterRestore(SettingValue("1", existed = true)))
        assertTrue(SettingValue(" ", existed = true).matchesAfterRestore(SettingValue("", existed = false)))
    }
}
