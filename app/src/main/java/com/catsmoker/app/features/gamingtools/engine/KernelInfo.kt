package com.catsmoker.app.features.gamingtools.engine

import com.catsmoker.app.shared.util.ThermalZoneReader
import java.io.File

/**
 * Read-only kernel facts for the pre-game bottleneck glance: CPU frequency policies
 * (governor + current/min/max), thermal zones, and memory PSI pressure.
 *
 * Everything here only *reads* world-readable sysfs/proc nodes with plain file I/O — no
 * privilege, no shell fork — so on a locked-down kernel each read simply yields null and the
 * UI says unreadable. Nothing here writes: applying governor/frequency tweaks is a future,
 * root-gated feature with snapshot/restore, deliberately out of this cycle. All parsers are
 * total (garbage in, empty-or-null out), because vendor layouts differ and a diagnostics read
 * is never worth a throw.
 */
object KernelInfo {

    /** One cpufreq policy (usually one per CPU cluster). Frequencies in kHz, as the kernel reports. */
    data class CpuPolicy(
        val name: String,
        val governor: String?,
        val curKhz: Long?,
        val minKhz: Long?,
        val maxKhz: Long?
    )

    /** One thermal zone: the kernel's own name plus Celsius, or null for a stuck sensor. */
    data class ThermalZone(val name: String, val tempC: Float?)

    /** `/proc/pressure/memory` some/full stall averages (percent) — the "is RAM the hitch" answer. */
    data class MemoryPressure(
        val someAvg10: Float?,
        val someAvg60: Float?,
        val someAvg300: Float?,
        val fullAvg10: Float?
    )

    /** All three reads, each independently nullable — one locked-down node must not cost the others. */
    data class Snapshot(
        val policies: List<CpuPolicy>,
        val thermalZones: List<ThermalZone>,
        val memoryPressure: MemoryPressure?
    )

    fun readSnapshot(): Snapshot = Snapshot(
        policies = readPolicies(),
        thermalZones = readThermalZones(),
        memoryPressure = readFileOrNull("/proc/pressure/memory")?.let(::parsePsiMemory)
    )

    // ------------------------------------------------------------------ cpu policies

    private fun readPolicies(): List<CpuPolicy> {
        val root = File("/sys/devices/system/cpu/cpufreq")
        val policies = root.listFiles { f -> f.isDirectory && f.name.startsWith("policy") }
            ?.sortedBy { it.name.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
            .orEmpty()
        return policies.map { dir ->
            CpuPolicy(
                name = dir.name,
                governor = readTrimmed(File(dir, "scaling_governor")),
                curKhz = readTrimmed(File(dir, "scaling_cur_freq"))?.toLongOrNull()
                    ?.takeIf { it > 0 },
                minKhz = readTrimmed(File(dir, "scaling_min_freq"))?.toLongOrNull()
                    ?.takeIf { it > 0 },
                maxKhz = readTrimmed(File(dir, "scaling_max_freq"))?.toLongOrNull()
                    ?.takeIf { it > 0 }
            )
        }
    }

    // ---------------------------------------------------------------- thermal zones

    /**
     * Thermal zones via the shared [ThermalZoneReader] (consolidation C3).
     * Same sysfs root, same -273..200 C stuck-sensor rule — one enumeration home.
     */
    private fun readThermalZones(): List<ThermalZone> =
        ThermalZoneReader.readZones().map { ThermalZone(it.name, it.tempC) }

    // ------------------------------------------------------------------ pure parsers

    /** Splits a `scaling_available_frequencies`-style list; garbage tokens are dropped. */
    fun parseFreqTable(text: String): List<Long> =
        text.trim().split(Regex("\\s+"))
            .mapNotNull { it.toLongOrNull()?.takeIf { khz -> khz > 0 } }

    /**
     * Millidegree sysfs value to Celsius, or null for blank/garbage/stuck-sensor values
     * (below -273 °C or above 200 °C cannot be a real zone reading).
     *
     * Delegates to [ThermalZoneReader.parseTempC]: one validation rule (C3).
     */
    fun parseThermalTemp(millidegrees: String): Float? =
        ThermalZoneReader.parseTempC(millidegrees)

    /** Parses `/proc/pressure/memory`; null when neither a `some` nor a `full` line parses. */
    fun parsePsiMemory(text: String): MemoryPressure? {
        fun lineAvg(prefix: String, key: String): Float? {
            val line = text.lineSequence()
                .firstOrNull { it.trimStart().startsWith(prefix) } ?: return null
            return Regex("""$key=([0-9.]+)""").find(line)?.groupValues?.get(1)?.toFloatOrNull()
        }
        val some10 = lineAvg("some", "avg10")
        val some60 = lineAvg("some", "avg60")
        val some300 = lineAvg("some", "avg300")
        val full10 = lineAvg("full", "avg10")
        return if (some10 == null && some60 == null && some300 == null && full10 == null) {
            null
        } else {
            MemoryPressure(some10, some60, some300, full10)
        }
    }

    private fun readTrimmed(file: File): String? = runCatching {
        if (!file.canRead()) return null
        file.bufferedReader().use { it.readLine() }?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun readFileOrNull(path: String): String? = runCatching {
        val file = File(path)
        if (!file.canRead()) return null
        file.readText()
    }.getOrNull()
}
