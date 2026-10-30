package com.catsmoker.app.features.spoofdevice

import com.catsmoker.app.shared.data.model.DeviceProfile
import com.catsmoker.app.shared.data.repository.SpoofRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verified-data contract for device presets. Only the values from the approved table
 * (see SPOOF_DEVICE.md "Verified preset data") may be filled; everything else must stay
 * blank/zero. Network and region fields are always empty.
 */
class PresetDeviceFieldsTest {

    // (id, cores, ram, gpuVendor, gpuRenderer, gpuVersion, vulkanModel, vulkanVersion,
    //  vulkanVendorId, vulkanDeviceId, gpuExtensions)
    private data class Expected(
        val id: String,
        val cores: Int,
        val ram: Int,
        val gpuVendor: String,
        val gpuRenderer: String,
        val gpuVersion: String,
        val vulkanModel: String,
        val vulkanVersion: String,
        val vulkanVendorId: Int,
        val vulkanDeviceId: Int,
        val gpuExtensions: String
    )

    private val expected = listOf(
        Expected("pixel_11_pro", 7, 12288, "", "", "", "", "", 0, 0, ""),
        Expected("galaxy_s26_ultra", 8, 12288, "Qualcomm", "Adreno (TM) 840", "", "Adreno (TM) 840", "1.4.295", 0x5143, 0, ""),
        Expected("xiaomi_17_ultra", 8, 12288, "Qualcomm", "Adreno (TM) 840", "", "Adreno (TM) 840", "", 0x5143, 0, ""),
        Expected("oneplus_15", 8, 12288, "Qualcomm", "Adreno (TM) 840",
            "OpenGL ES 3.2 V@0842.8 (GIT@008a698cf0, I622757abef, 1757677684) (Date:09/12/25)",
            "Adreno (TM) 840", "1.4.295", 0x5143, 0, ""),
        Expected("tab_s11_ultra", 8, 12288, "", "", "", "", "", 0, 0, ""),
        Expected("iqoo_15", 8, 12288, "Qualcomm", "Adreno (TM) 840", "", "Adreno (TM) 840", "", 0x5143, 0, ""),
        Expected("rog_phone_9_pro", 8, 16384, "Qualcomm", "Adreno (TM) 830", "", "Adreno (TM) 830", "", 0x5143, 0, ""),
        Expected("redmagic_10s_pro", 8, 16384, "Qualcomm", "Adreno (TM) 830", "", "Adreno (TM) 830", "", 0x5143, 0, "")
    )

    @Test
    fun verifiedPresetsMatchApprovedTable() {
        val presets = SpoofRepository.staticPresets().associateBy { it.id }
        expected.forEach { e ->
            val p: DeviceProfile = presets[e.id]!!.profile
            assertEquals("${e.id} hwCpuCores", e.cores, p.hwCpuCores)
            assertEquals("${e.id} hwRamMb", e.ram, p.hwRamMb)
            assertEquals("${e.id} gpuVendor", e.gpuVendor, p.gpuVendor)
            assertEquals("${e.id} gpuRenderer", e.gpuRenderer, p.gpuRenderer)
            assertEquals("${e.id} vulkanModel", e.vulkanModel, p.vulkanModel)
            assertEquals("${e.id} gpuVersion", e.gpuVersion, p.gpuVersion)
            assertEquals("${e.id} gpuExtensions", e.gpuExtensions, p.gpuExtensions)
            assertEquals("${e.id} vulkanModel", e.vulkanModel, p.vulkanModel)
            assertEquals("${e.id} vulkanVersion", e.vulkanVersion, p.vulkanVersion)
            assertEquals("${e.id} vulkanVendorId", e.vulkanVendorId, p.vulkanVendorId)
            assertEquals("${e.id} vulkanDeviceId", e.vulkanDeviceId, p.vulkanDeviceId)
            assertEquals("${e.id} vulkanDeviceId", e.vulkanDeviceId, p.vulkanDeviceId)
            // nothing else filled
            assertEquals("${e.id} hwHeapMb", 0, p.hwHeapMb)
            assertEquals("${e.id} hwLargeHeapMb", 0, p.hwLargeHeapMb)
            assertTrue("${e.id} operatorAlpha blank", p.operatorAlpha.isBlank())
            assertTrue("${e.id} timezone blank", p.timezone.isBlank())
            assertTrue("${e.id} locale blank", p.locale.isBlank())
        }
    }
}
