package com.catsmoker.app.shared.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the capability detector: static device facts in, supported-mechanism flags out.
 * Pure logic — no Context, no device — so every branch is provable here.
 */
class DeviceCapabilitiesTest {

    private fun info(
        manufacturer: String = "Google",
        hardware: String = "google",
        board: String = "lynx",
        sdk: Int = 34
    ) = DeviceCapabilities.DeviceInfo(
        manufacturer = manufacturer,
        hardware = hardware,
        board = board,
        sdkInt = sdk
    )

    @Test
    fun detectsVivoFamily() {
        assertTrue(DeviceCapabilities.detect(info(manufacturer = "vivo")).isVivo)
        assertTrue(DeviceCapabilities.detect(info(manufacturer = "iQOO")).isVivo)
        assertFalse(DeviceCapabilities.detect(info()).isVivo)
    }

    @Test
    fun detectsSamsungAndXiaomi() {
        assertTrue(DeviceCapabilities.detect(info(manufacturer = "samsung")).isSamsung)
        assertTrue(DeviceCapabilities.detect(info(manufacturer = "Xiaomi")).isXiaomi)
        assertTrue(DeviceCapabilities.detect(info(manufacturer = "Redmi")).isXiaomi)
        assertTrue(DeviceCapabilities.detect(info(manufacturer = "POCO")).isXiaomi)
        assertFalse(DeviceCapabilities.detect(info()).isSamsung)
    }

    @Test
    fun detectsSiliconFromHardwareOrBoard() {
        assertTrue(DeviceCapabilities.detect(info(hardware = "qcom", board = "titan")).isQualcomm)
        assertTrue(DeviceCapabilities.detect(info(hardware = "google", board = "sun")).isQualcomm)
        assertTrue(DeviceCapabilities.detect(info(hardware = "mt6877")).isMediaTek)
        assertTrue(DeviceCapabilities.detect(info(hardware = "Dimensity 9200")).isMediaTek)
        assertFalse(DeviceCapabilities.detect(info()).isQualcomm)
    }

    @Test
    fun versionGatesFollowPlatformAvailability() {
        // Fixed performance mode needs Android 11 (R); thermal headroom API 30+;
        // Game Interventions need Android 12 (S).
        val old = DeviceCapabilities.detect(info(sdk = 29))
        assertFalse(old.supportsFixedPerformanceMode)
        assertFalse(old.supportsThermalHeadroom)
        assertFalse(old.supportsGameInterventions)
        val r = DeviceCapabilities.detect(info(sdk = 30))
        assertTrue(r.supportsFixedPerformanceMode)
        assertTrue(r.supportsThermalHeadroom)
        assertFalse(r.supportsGameInterventions)
        val s = DeviceCapabilities.detect(info(sdk = 31))
        assertTrue(s.supportsGameInterventions)
    }

    @Test
    fun matchingIsCaseInsensitiveAndNullSafe() {
        val caps = DeviceCapabilities.detect(info(manufacturer = "  VIVO  ", hardware = "QCOM"))
        assertTrue(caps.isVivo)
        assertTrue(caps.isQualcomm)
    }
}
