package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CpuGovernorMode].
 *
 * The only governor this channel will write is the device's own `performance` governor, and only
 * when that policy's `scaling_available_governors` lists it. No flavour-of-the-month governor
 * name is assumed: a kernel that does not offer it is simply left alone.
 */
class CpuGovernorModeTest {

    @Test
    fun `performance is chosen when the policy offers it and is not on it already`() {
        assertEquals(
            "performance",
            CpuGovernorMode.performanceTarget("schedutil", "userspace powersave performance schedutil")
        )
        assertEquals(
            "performance",
            CpuGovernorMode.performanceTarget("powersave", "powersave performance")
        )
    }

    @Test
    fun `a policy already on performance is left alone`() {
        assertNull(CpuGovernorMode.performanceTarget("performance", "performance schedutil"))
    }

    @Test
    fun `a policy without performance writes nothing`() {
        assertNull(CpuGovernorMode.performanceTarget("schedutil", "userspace powersave schedutil"))
        assertNull(CpuGovernorMode.performanceTarget("schedutil", null))
        assertNull(CpuGovernorMode.performanceTarget("schedutil", ""))
        assertNull(CpuGovernorMode.performanceTarget("schedutil", "garbage"))
    }

    @Test
    fun `governor list parsing tolerates padding and blank tokens`() {
        assertEquals(
            listOf("userspace", "performance", "schedutil"),
            CpuGovernorMode.availableGovernors("  userspace   performance\tschedutil \n")
        )
        assertTrue(CpuGovernorMode.availableGovernors(null).isEmpty())
    }

    @Test
    fun `desired governor keeps an already-at-target policy reportable`() {
        // Recovery re-runs this against a governor the previous session already set: the desired
        // state is still performance, not "nothing to do", or the report would forget it.
        assertEquals("performance", CpuGovernorMode.desiredGovernor("performance", null))
        assertEquals(
            "performance",
            CpuGovernorMode.desiredGovernor("schedutil", "schedutil performance")
        )
        assertNull(CpuGovernorMode.desiredGovernor("schedutil", "schedutil powersave"))
        assertNull(CpuGovernorMode.desiredGovernor(null, "performance"))
    }
}
