package com.catsmoker.app.shared.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Snapshot compatibility for the Gaming Mode restore contract.
 *
 * Every field is either a measured fact or "do not touch" (null / empty):
 * adding the Snapdragon CPU-node map must keep old snapshots restoring
 * exactly what they recorded, and a snapshot that never saw the new field
 * must behave as "no Snapdragon nodes" rather than failing the whole
 * restore. Partial application is expressed per-node — one refused node
 * never blocks the restore of the others.
 */
class GamingOptimizationSnapshotTest {

    @Test
    fun snapdragonNodesRoundTripThroughJson() {
        val snapshot = GamingOptimizationSnapshot(
            activeGamePackage = null,
            activeGameUid = null,
            timestamp = 1L,
            minRefreshRate = null,
            peakRefreshRate = null,
            touchResponseSpeed = null,
            userPreferredDisplayModeId = null,
            affectedPackages = emptySet(),
            snapdragonCpuNodes = mapOf(
                "/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq" to "2169600",
                "/dev/cpuset/foreground/cpus" to "0-5,7"
            )
        )
        val parsed = GamingOptimizationSnapshot.fromJson(snapshot.toJson())
        assertEquals(snapshot.snapdragonCpuNodes, parsed?.snapdragonCpuNodes)
    }

    @Test
    fun snapshotWithoutTheNewFieldReadsAsNoSnapdragonNodes() {
        // An old snapshot predates the Snapdragon map: the missing key must
        // behave as "nothing to restore", never as a parse failure.
        val oldJson = """
            {"activeGamePackage":null,"activeGameUid":null,"timestamp":1,
             "affectedPackages":[],"mediatekGameNodes":{}}
        """.trimIndent()
        val parsed = GamingOptimizationSnapshot.fromJson(oldJson)
        assertTrue(parsed != null)
        assertTrue(parsed!!.snapdragonCpuNodes.orEmpty().isEmpty())
        // The five SmartPack-derived families are newer still: an old snapshot must read as
        // "this lever was never used", not crash a revert that runs after an app update.
        assertTrue(parsed.cpuInputBoostNodes.orEmpty().isEmpty())
        assertTrue(parsed.cpuGovernorNodes.orEmpty().isEmpty())
        assertTrue(parsed.storageTuningNodes.orEmpty().isEmpty())
        assertTrue(parsed.memoryTuningNodes.orEmpty().isEmpty())
        assertNull(parsed.spectrumProfileProperty)
        assertNull(parsed.spectrumProfile)
    }

    @Test
    fun kernelTuningFieldsRoundTripThroughJson() {
        val snapshot = GamingOptimizationSnapshot(
            activeGamePackage = null,
            activeGameUid = null,
            timestamp = 1L,
            minRefreshRate = null,
            peakRefreshRate = null,
            touchResponseSpeed = null,
            userPreferredDisplayModeId = null,
            affectedPackages = emptySet(),
            cpuInputBoostNodes = mapOf(
                "/sys/kernel/cpu_input_boost/enabled" to "0",
                "/sys/kernel/cpu_input_boost/ib_freqs" to "0:576000 4:1400000"
            ),
            cpuGovernorNodes = mapOf(
                "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor" to "schedutil",
                "/sys/devices/system/cpu/cpufreq/policy6/scaling_governor" to "schedutil"
            ),
            spectrumProfileProperty = "persist.spectrum.profile",
            spectrumProfile = "1",
            storageTuningNodes = mapOf("/sys/block/sda/queue/read_ahead_kb" to "128"),
            memoryTuningNodes = mapOf(
                "/proc/sys/vm/min_free_kbytes" to "9515",
                "/proc/sys/vm/extra_free_kbytes" to "4096"
            )
        )
        val parsed = GamingOptimizationSnapshot.fromJson(snapshot.toJson())
        assertEquals(snapshot.cpuInputBoostNodes, parsed?.cpuInputBoostNodes)
        assertEquals(snapshot.cpuGovernorNodes, parsed?.cpuGovernorNodes)
        assertEquals(snapshot.spectrumProfileProperty, parsed?.spectrumProfileProperty)
        assertEquals(snapshot.spectrumProfile, parsed?.spectrumProfile)
        assertEquals(snapshot.storageTuningNodes, parsed?.storageTuningNodes)
        assertEquals(snapshot.memoryTuningNodes, parsed?.memoryTuningNodes)
    }

    @Test
    fun garbageJsonStillParsesToNull() {
        assertNull(GamingOptimizationSnapshot.fromJson("not json"))
    }
}
