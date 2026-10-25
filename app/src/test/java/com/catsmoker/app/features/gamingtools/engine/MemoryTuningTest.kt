package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [MemoryTuning]: VM watermarks are raised toward the device's own RAM size and
 * its own declared reserve, never toward a copied kernel-tuning constant.
 *
 * `min_free_kbytes` is raised to at most 1% of `MemTotal` — the low-watermark guideline for a
 * large-RAM device — and `extra_free_kbytes` (CAF) is raised only toward the device's own
 * `min_free_kbytes`, so the family can only ever reserve more, never reserve less.
 */
class MemoryTuningTest {

    @Test
    fun `MemTotal is parsed from the kernel's own meminfo`() {
        assertEquals(
            5_691_532L,
            MemoryTuning.memTotalKb(
                """
                MemTotal:        5691532 kB
                MemFree:          123456 kB
                """.trimIndent()
            )
        )
        assertNull(MemoryTuning.memTotalKb(null))
        assertNull(MemoryTuning.memTotalKb(""))
        assertNull(MemoryTuning.memTotalKb("MemFree: 123 kB"))
        assertNull(MemoryTuning.memTotalKb("MemTotal: zero kB"))
        assertNull(MemoryTuning.memTotalKb("MemTotal: 0 kB"))
    }

    @Test
    fun `min free is raised to one percent of RAM`() {
        assertEquals("56915", MemoryTuning.minFreeTarget("9515", 5_691_532L))
        assertEquals("10240", MemoryTuning.minFreeTarget("9515", 1_024_000L))
    }

    @Test
    fun `min free is never lowered or written without a real RAM size`() {
        assertNull(MemoryTuning.minFreeTarget("56915", 5_691_532L))
        assertNull(MemoryTuning.minFreeTarget("60000", 5_691_532L))
        assertNull(MemoryTuning.minFreeTarget("9515", null))
        assertNull(MemoryTuning.minFreeTarget("9515", 0L))
        assertNull(MemoryTuning.minFreeTarget("9515", 99L))
        assertNull(MemoryTuning.minFreeTarget(null, 5_691_532L))
        assertNull(MemoryTuning.minFreeTarget("abc", 5_691_532L))
    }

    @Test
    fun `extra free is raised only toward the device's own min free`() {
        assertEquals("9515", MemoryTuning.extraFreeTarget("4096", "9515"))
        assertNull(MemoryTuning.extraFreeTarget("9515", "9515"))
        assertNull(MemoryTuning.extraFreeTarget("30375", "9515"))
        assertNull(MemoryTuning.extraFreeTarget("4096", "0"))
        assertNull(MemoryTuning.extraFreeTarget("4096", null))
        assertNull(MemoryTuning.extraFreeTarget(null, "9515"))
        assertNull(MemoryTuning.extraFreeTarget("abc", "9515"))
    }

    @Test
    fun `desired values keep an already-at-target node reportable`() {
        assertEquals("56915", MemoryTuning.desiredMinFree("56915", 5_691_532L))
        // A reserve already above the guideline is someone else's higher choice, never lowered.
        assertEquals("60000", MemoryTuning.desiredMinFree("60000", 5_691_532L))
        assertEquals("56915", MemoryTuning.desiredMinFree("9515", 5_691_532L))
        assertNull(MemoryTuning.desiredMinFree(null, 5_691_532L))
        assertNull(MemoryTuning.desiredMinFree("9515", null))

        assertEquals("9515", MemoryTuning.desiredExtraFree("9515", "9515"))
        assertEquals("9515", MemoryTuning.desiredExtraFree("4096", "9515"))
        // An extra reserve already above the base reserve is left where the vendor put it.
        assertEquals("30375", MemoryTuning.desiredExtraFree("30375", "9515"))
        assertNull(MemoryTuning.desiredExtraFree(null, "9515"))
        assertNull(MemoryTuning.desiredExtraFree("4096", null))
    }
}
