package com.catsmoker.app.screens.gaming.mode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour lock for the read-only kernel facts.
 *
 * sysfs/proc layouts differ per vendor and kernel, so every parser is total: garbage in means
 * an empty or null reading out — never a throw, and never a 0 that looks measured. Frequencies
 * stay in kHz until display time so no rounding happens inside the parser.
 */
class KernelInfoTest {

    @Test
    fun freqTableSplitsOnWhitespace() {
        assertEquals(
            listOf(300000L, 1500000L, 2800000L),
            KernelInfo.parseFreqTable("300000 1500000 2800000\n")
        )
    }

    @Test
    fun freqTableDropsGarbageTokens() {
        assertEquals(listOf(1500000L), KernelInfo.parseFreqTable("1500000 bogus -5"))
    }

    @Test
    fun freqTableBlankIsEmpty() {
        assertTrue(KernelInfo.parseFreqTable("").isEmpty())
        assertTrue(KernelInfo.parseFreqTable("   \n").isEmpty())
    }

    @Test fun cpufreqPolicyCpuListIsNotACpusetRange() {
        assertEquals(setOf(0, 1, 4, 7), KernelInfo.parseRelatedCpus("0 1  4\t7\n"))
        assertTrue(KernelInfo.parseRelatedCpus("0-4,7").isEmpty())
        assertTrue(KernelInfo.parseRelatedCpus("0 garbage 7").isEmpty())
        assertTrue(KernelInfo.parseRelatedCpus("").isEmpty())
    }

    @Test fun policyDetailsNeverGuessFrequencyTableOrOnlineMembership() {
        val nodes = mapOf("related_cpus" to "4 5", "affected_cpus" to "4", "scaling_driver" to "test",
            "scaling_governor" to "schedutil", "scaling_cur_freq" to "-1",
            "scaling_min_freq" to "300000", "cpuinfo_max_freq" to "2500000\n")
        val policy = KernelInfo.policyDetails(File("policy4")) { nodes[it.name] }
        assertEquals("policy4", policy.scaling.name)
        assertEquals(setOf(4, 5), policy.relatedCpus)
        assertEquals(setOf(4), policy.affectedCpus)
        assertNull(policy.scaling.curKhz)
        assertEquals(300000L, policy.scaling.minKhz)
        assertEquals(2500000L, policy.hardwareMaxKhz)
        assertTrue(policy.frequenciesKhz.isEmpty())
    }

    @Test
    fun thermalTempConvertsMillidegrees() {
        assertEquals(42.5f, KernelInfo.parseThermalTemp("42500").let { it!! }, 0.01f)
    }

    @Test
    fun thermalTempRejectsGarbage() {
        assertNull(KernelInfo.parseThermalTemp(""))
        assertNull(KernelInfo.parseThermalTemp("hot"))
        // Below absolute zero or above silicon limits: a stuck sensor, not a reading.
        assertNull(KernelInfo.parseThermalTemp("-300000"))
        assertNull(KernelInfo.parseThermalTemp("500000"))
    }

    @Test
    fun psiMemoryParsesSomeAndAvgLines() {
        val text = """
            some avg10=2.50 avg60=1.20 avg300=0.40 total=123456
            full avg10=0.10 avg60=0.05 avg300=0.01 total=7890
        """.trimIndent()
        val pressure = KernelInfo.parsePsiMemory(text)!!
        assertEquals(2.50f, pressure.someAvg10.let { it!! }, 0.001f)
        assertEquals(1.20f, pressure.someAvg60.let { it!! }, 0.001f)
        assertEquals(0.40f, pressure.someAvg300.let { it!! }, 0.001f)
        assertEquals(0.10f, pressure.fullAvg10.let { it!! }, 0.001f)
    }

    @Test
    fun psiMemoryGarbageIsNull() {
        assertNull(KernelInfo.parsePsiMemory(""))
        assertNull(KernelInfo.parsePsiMemory("some avg10=oops"))
    }
}
