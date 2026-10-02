package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the per-cluster MHz mapping against
 * `reference/gamingtools/booster/.../metrics/Monitors.kt#readClusterState` (read before this
 * test was written): policies sorted by max frequency, then first → efficiency, second-to-last
 * → performance, last → ultra (one policy → efficiency only, two → efficiency + performance).
 *
 * Deliberate divergences: the kernel here is read through `scaling_max_freq`
 * ([KernelInfo.readPolicies]), not `cpuinfo_max_freq`, and there is no per-cpu fallback —
 * policy dirs suffice on the kernels this app supports, and a second channel would double
 * the sysfs reads every poll for the same answer.
 */
class CpuClusterStateTest {

    private fun policy(name: String, curKhz: Long?, maxKhz: Long?) =
        KernelInfo.CpuPolicy(name = name, governor = "schedutil", curKhz = curKhz, minKhz = 300000L, maxKhz = maxKhz)

    @Test
    fun emptyPoliciesMapToNothing() {
        val state = KernelInfo.mapClusterState(emptyList())
        assertNull(state.effMhz)
        assertNull(state.perfMhz)
        assertNull(state.ultraMhz)
    }

    @Test
    fun singlePolicyIsEfficiencyOnly() {
        val state = KernelInfo.mapClusterState(listOf(policy("policy0", 1800000L, 2000000L)))
        assertEquals(1800, state.effMhz)
        assertNull(state.perfMhz)
        assertNull(state.ultraMhz)
    }

    @Test
    fun labDeviceSinglePolicyMapsToEfficiency() {
        // Observed on the lab device (Lenovo TB-8505X, mt6761, 4xA53, one policy0):
        // scaling_cur_freq 850000, max 2001000, related_cpus 0-3. The governor node is
        // unreadable to the shell there, which the mapper never needs.
        val state = KernelInfo.mapClusterState(
            listOf(
                KernelInfo.CpuPolicy(
                    name = "policy0",
                    governor = null,
                    curKhz = 850000L,
                    minKhz = null,
                    maxKhz = 2001000L
                )
            )
        )
        assertEquals(850, state.effMhz)
        assertNull(state.perfMhz)
        assertNull(state.ultraMhz)
    }

    @Test
    fun twoPoliciesAreEfficiencyPlusPerformance() {
        val state = KernelInfo.mapClusterState(
            listOf(
                policy("policy6", 2800000L, 3000000L),
                policy("policy0", 1700000L, 1800000L)
            )
        )
        assertEquals(1700, state.effMhz)
        assertEquals(2800, state.perfMhz)
        assertNull(state.ultraMhz)
    }

    @Test
    fun threePoliciesMapEffPerfUltraByMaxOrder() {
        val state = KernelInfo.mapClusterState(
            listOf(
                policy("policy7", 3200000L, 3500000L),
                policy("policy0", 1500000L, 1800000L),
                policy("policy4", 2400000L, 2600000L)
            )
        )
        assertEquals(1500, state.effMhz)
        assertEquals(2400, state.perfMhz)
        assertEquals(3200, state.ultraMhz)
    }

    @Test
    fun policiesWithoutMaxAreUnplaceable() {
        val state = KernelInfo.mapClusterState(listOf(policy("policy0", 1500000L, null)))
        assertNull(state.effMhz)
        assertNull(state.perfMhz)
        assertNull(state.ultraMhz)
    }

    @Test
    fun duplicateMaxTiersCollapse() {
        val state = KernelInfo.mapClusterState(
            listOf(
                policy("policy0", 1500000L, 1800000L),
                policy("policy1", 1600000L, 1800000L),
                policy("policy6", 2800000L, 3000000L)
            )
        )
        assertEquals(1500, state.effMhz)
        assertEquals(2800, state.perfMhz)
        assertNull(state.ultraMhz)
    }

    @Test
    fun shellReaderPlumbingMapsTheSameWay() {
        // The privileged-shell fallback path (directory listing + per-node cat hidden from
        // the app UID): values arrive as strings, mapping is identical.
        val dirs = listOf(java.io.File("/sys/devices/system/cpu/cpufreq/policy0"))
        val table = mapOf(
            "scaling_governor" to "schedutil",
            "scaling_cur_freq" to "850000",
            "scaling_min_freq" to "300000",
            "scaling_max_freq" to "2001000"
        )
        val policies = kotlinx.coroutines.runBlocking {
            KernelInfo.readPoliciesSuspend(dirs) { file -> table[file.name] }
        }
        assertEquals(1, policies.size)
        assertEquals("schedutil", policies[0].governor)
        val state = KernelInfo.mapClusterState(policies)
        assertEquals(850, state.effMhz)
    }
}
