package com.catsmoker.app.shared.util

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for the shared thermal-zone reader (Phase 1 / C3).
 *
 * Single owner of sysfs thermal-zone enumeration + millidegree validation.
 * KernelInfo delegates here; ShellRunner's sysfs transport reports raw text
 * parsed by ThermalServiceParser with the same plausibility bounds.
 */
class ThermalZoneReaderTest {

    @Test
    fun parsesMillidegrees() {
        assertEquals(42.5f, ThermalZoneReader.parseTempC("42500")!!, 0.01f)
    }

    @Test
    fun rejectsGarbageAndStuckSensors() {
        assertNull(ThermalZoneReader.parseTempC(""))
        assertNull(ThermalZoneReader.parseTempC("hot"))
        assertNull(ThermalZoneReader.parseTempC("-300000"))
        assertNull(ThermalZoneReader.parseTempC("500000"))
    }

    @Test
    fun readsZonesFromRoot() {
        val root = Files.createTempDirectory("thermal").toFile()
        try {
            val zone = File(root, "thermal_zone0")
            zone.mkdir()
            File(zone, "type").writeText("cpu-0-0")
            File(zone, "temp").writeText("42500")
            val zones = ThermalZoneReader.zonesFromSysfsRoot(root)
            assertEquals(1, zones.size)
            assertEquals("cpu-0-0", zones[0].name)
            assertEquals(42.5f, zones[0].tempC!!, 0.01f)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun skipsZonesWithoutType() {
        val root = Files.createTempDirectory("thermal-empty").toFile()
        try {
            val zone = File(root, "thermal_zone0")
            zone.mkdir()
            File(zone, "temp").writeText("42500")
            assertTrue(ThermalZoneReader.zonesFromSysfsRoot(root).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
