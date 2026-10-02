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

    /**
     * Per-cluster current frequencies in MHz: efficiency / performance / ultra, each null when
     * its cluster cannot be placed. After the reference's `readClusterState`
     * (`reference/gamingtools/booster/.../metrics/Monitors.kt`, read before this file was
     * written): policies sorted by max, first → efficiency, second-to-last → performance,
     * last → ultra — one policy reads as efficiency only, two as efficiency + performance.
     */
    data class CpuClusterState(
        val effMhz: Int?,
        val perfMhz: Int?,
        val ultraMhz: Int?
    )

    /**
     * Maps policies onto cluster tiers. Policies without a max cannot be ordered and are
     * skipped; duplicate max tiers collapse to their first policy. Reads use
     * `scaling_max_freq` (see [readPolicies]), not `cpuinfo_max_freq`, and there is no
     * per-cpu fallback — policy dirs suffice on the kernels this app supports.
     */
    fun mapClusterState(policies: List<CpuPolicy>): CpuClusterState {
        val ranked = policies
            .filter { (it.maxKhz ?: 0L) > 0L }
            .distinctBy { it.maxKhz }
            .sortedBy { it.maxKhz }
        fun mhz(khz: Long?): Int? = khz?.takeIf { it > 0L }?.let { (it / 1000L).toInt() }
        return when (ranked.size) {
            0 -> CpuClusterState(null, null, null)
            1 -> CpuClusterState(mhz(ranked[0].curKhz), null, null)
            2 -> CpuClusterState(mhz(ranked[0].curKhz), mhz(ranked[1].curKhz), null)
            else -> CpuClusterState(
                mhz(ranked[0].curKhz),
                mhz(ranked[ranked.size - 2].curKhz),
                mhz(ranked.last().curKhz)
            )
        }
    }

    fun readSnapshot(): Snapshot = Snapshot(
        policies = readPolicies(),
        thermalZones = readThermalZones(),
        memoryPressure = readFileOrNull("/proc/pressure/memory")?.let(::parsePsiMemory)
    )

    // ------------------------------------------------------------------ cpu policies

    /** The cpufreq policy dirs the kernel exposes, in policy-number order. */
    fun readPolicies(): List<CpuPolicy> = readPolicies(::readTrimmed)

    /**
     * Same read with a pluggable file reader: plain file I/O by default, or a privileged
     * `cat` on kernels that deny the app UID (SELinux) but serve root/Shizuku — observed
     * on MediaTek Android 10, where `scaling_cur_freq` reads for shell/root but not for
     * untrusted apps. A reader returning null/blank for a node behaves exactly like an
     * unreadable file: the policy keeps nulls, never guesses.
     */
    fun readPolicies(readFile: (File) -> String?): List<CpuPolicy> =
        policyDirs().map { dir -> mapPolicy(dir, readFile) }

    /** Suspend variant of [readPolicies] for readers that fork (a privileged shell `cat`). */
    suspend fun readPoliciesSuspend(readFile: suspend (File) -> String?): List<CpuPolicy> =
        readPoliciesSuspend(policyDirs(), readFile)

    /**
     * Suspend variant over caller-supplied policy dirs: on kernels that hide even the
     * directory listing from the app UID, the caller lists via the privileged shell first
     * and reads each node through it.
     */
    suspend fun readPoliciesSuspend(
        dirs: List<File>,
        readFile: suspend (File) -> String?
    ): List<CpuPolicy> =
        dirs.map { dir ->
            CpuPolicy(
                name = dir.name,
                governor = readFile(File(dir, "scaling_governor")),
                curKhz = readFile(File(dir, "scaling_cur_freq"))?.toLongOrNull()
                    ?.takeIf { it > 0 },
                minKhz = readFile(File(dir, "scaling_min_freq"))?.toLongOrNull()
                    ?.takeIf { it > 0 },
                maxKhz = readFile(File(dir, "scaling_max_freq"))?.toLongOrNull()
                    ?.takeIf { it > 0 }
            )
        }

    private fun policyDirs(): List<File> {
        val root = File("/sys/devices/system/cpu/cpufreq")
        return root.listFiles { f -> f.isDirectory && f.name.startsWith("policy") }
            ?.sortedBy { it.name.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
            .orEmpty()
    }

    private fun mapPolicy(dir: File, readFile: (File) -> String?): CpuPolicy = CpuPolicy(
        name = dir.name,
        governor = readFile(File(dir, "scaling_governor")),
        curKhz = readFile(File(dir, "scaling_cur_freq"))?.toLongOrNull()
            ?.takeIf { it > 0 },
        minKhz = readFile(File(dir, "scaling_min_freq"))?.toLongOrNull()
            ?.takeIf { it > 0 },
        maxKhz = readFile(File(dir, "scaling_max_freq"))?.toLongOrNull()
            ?.takeIf { it > 0 }
    )

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
