package com.catsmoker.app.shared.util

import java.io.File

/**
 * Single owner of sysfs thermal-zone enumeration + millidegree validation
 * (consolidation C3).
 *
 * KernelInfo delegates here for its pre-game glance. ShellRunner's thermal
 * transport ([system.shell.ShellRunner.readThermal]) still reports raw text for
 * `ThermalServiceParser`, which applies its own 0..150 C HAL plausibility band;
 * the sysfs millidegree rule here (-273..200 C stuck-sensor rejection) is the
 * shared file-reading contract both paths honour.
 */
object ThermalZoneReader {

    /** One thermal zone: the kernel's own name plus Celsius, or null for a stuck sensor. */
    data class Zone(val name: String, val tempC: Float?)

    /**
     * Millidegree sysfs value to Celsius, or null for blank/garbage/stuck-sensor values
     * (below -273 C or above 200 C cannot be a real zone reading).
     */
    fun parseTempC(millidegrees: String): Float? {
        val milli = millidegrees.trim().toLongOrNull() ?: return null
        val celsius = milli / 1000f
        return if (celsius in -273f..200f) celsius else null
    }

    /** Enumerates zones under the real `/sys/class/thermal` root. Never throws. */
    fun readZones(): List<Zone> =
        runCatching { zonesFromSysfsRoot(File("/sys/class/thermal")) }.getOrDefault(emptyList())

    /** Same enumeration under any root — the unit-testable seam. */
    fun zonesFromSysfsRoot(root: File): List<Zone> {
        val zones = root.listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") }
            ?.sortedBy { it.name.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
            .orEmpty()
        return zones.mapNotNull { dir ->
            val type = readTrimmed(File(dir, "type")) ?: return@mapNotNull null
            Zone(type, parseTempC(readTrimmed(File(dir, "temp")).orEmpty()))
        }
    }

    private fun readTrimmed(file: File): String? = runCatching {
        if (!file.canRead()) return null
        file.bufferedReader().use { it.readLine() }?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
