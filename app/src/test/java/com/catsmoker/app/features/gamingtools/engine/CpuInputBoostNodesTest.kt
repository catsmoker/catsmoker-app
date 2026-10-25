package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CpuInputBoostNodes]: the SmartPack vocabulary for `cpu_input_boost` and the
 * `/dev/stune` top-app knobs, expressed as raise-only / enable-only rules.
 *
 * Every target must come from the device itself — the boost window the node declares and the
 * top-app boost the device already carries — so a foreign constant can never enter a plan.
 */
class CpuInputBoostNodesTest {

    // --- the declared boost window ---------------------------------------------------------

    @Test
    fun `boost window is the first positive duration the device declares`() {
        assertEquals(
            500L,
            CpuInputBoostNodes.boostWindowMs(mapOf("ib_duration_ms" to "500"))
        )
        assertEquals(
            40L,
            CpuInputBoostNodes.boostWindowMs(mapOf("input_boost_duration" to "40"))
        )
        assertEquals(
            1000L,
            CpuInputBoostNodes.boostWindowMs(mapOf("input_boost_duration_ms" to "1000"))
        )
    }

    @Test
    fun `boost window is null when every declared duration is zero or unparseable`() {
        assertNull(CpuInputBoostNodes.boostWindowMs(emptyMap()))
        assertNull(CpuInputBoostNodes.boostWindowMs(mapOf("ib_duration_ms" to "0")))
        assertNull(CpuInputBoostNodes.boostWindowMs(mapOf("ib_duration_ms" to "-1")))
        assertNull(CpuInputBoostNodes.boostWindowMs(mapOf("ib_duration_ms" to "abc")))
        assertNull(
            CpuInputBoostNodes.boostWindowMs(
                mapOf("ib_duration_ms" to "0", "input_boost_duration" to "garbage")
            )
        )
    }

    @Test
    fun `boost window skips a zero first candidate and keeps looking`() {
        // Some kernels ship more than one duration spelling; only a real window enables a boost.
        assertEquals(
            120L,
            CpuInputBoostNodes.boostWindowMs(
                linkedMapOf("ib_duration_ms" to "0", "input_boost_duration" to "120")
            )
        )
    }

    // --- dynamic stune boost ---------------------------------------------------------------

    @Test
    fun `dynamic stune target is the device's own static top-app boost`() {
        assertEquals("10", CpuInputBoostNodes.stuneBoostTarget("0", "10"))
        assertEquals("10", CpuInputBoostNodes.stuneBoostTarget("5", "10"))
    }

    @Test
    fun `dynamic stune never lowers a boost already at or above the target`() {
        assertNull(CpuInputBoostNodes.stuneBoostTarget("10", "10"))
        assertNull(CpuInputBoostNodes.stuneBoostTarget("20", "10"))
        // A zero static boost means top-app is not boosted at all: nothing to copy.
        assertNull(CpuInputBoostNodes.stuneBoostTarget("0", "0"))
    }

    @Test
    fun `dynamic stune writes nothing when either side is unreadable or negative`() {
        assertNull(CpuInputBoostNodes.stuneBoostTarget(null, "10"))
        assertNull(CpuInputBoostNodes.stuneBoostTarget("0", null))
        assertNull(CpuInputBoostNodes.stuneBoostTarget("abc", "10"))
        assertNull(CpuInputBoostNodes.stuneBoostTarget("0", "abc"))
        assertNull(CpuInputBoostNodes.stuneBoostTarget("-1", "10"))
        assertNull(CpuInputBoostNodes.stuneBoostTarget("0", "-10"))
    }

    // --- the enable-only top-app flags -----------------------------------------------------

    @Test
    fun `top-app flags are turned on only when they declare zero`() {
        assertEquals("1", SnapdragonKernelNodes.enableIfZero("0"))
        assertNull(SnapdragonKernelNodes.enableIfZero("1"))
        assertNull(SnapdragonKernelNodes.enableIfZero(null))
        assertNull(SnapdragonKernelNodes.enableIfZero("abc"))
    }

    @Test
    fun `recovery re-asserts an already-on flag but never over garbage`() {
        assertEquals("1", CpuInputBoostNodes.desiredFlag("0"))
        assertEquals("1", CpuInputBoostNodes.desiredFlag("1"))
        assertNull(CpuInputBoostNodes.desiredFlag(null))
        assertNull(CpuInputBoostNodes.desiredFlag("2"))
        assertNull(CpuInputBoostNodes.desiredFlag("abc"))
    }

    @Test
    fun `recovery keeps the larger stune boost`() {
        assertEquals("10", CpuInputBoostNodes.desiredStuneBoost("0", "10"))
        assertEquals("10", CpuInputBoostNodes.desiredStuneBoost("10", "10"))
        assertEquals("20", CpuInputBoostNodes.desiredStuneBoost("20", "10"))
        assertNull(CpuInputBoostNodes.desiredStuneBoost("0", "0"))
        assertNull(CpuInputBoostNodes.desiredStuneBoost(null, "10"))
        assertNull(CpuInputBoostNodes.desiredStuneBoost("0", null))
    }

    @Test
    fun `the family names no GPU thermal or voltage node`() {
        val nodes = CpuInputBoostNodes.writableNodeNames()
        val banned = listOf("kgsl", "pwrlevel", "thermal", "gpu", "voltage", "screen")
        for (node in nodes) {
            for (fragment in banned) {
                assertTrue(
                    "writable node '$node' names '$fragment'",
                    !node.lowercase().contains(fragment)
                )
            }
        }
    }
}
