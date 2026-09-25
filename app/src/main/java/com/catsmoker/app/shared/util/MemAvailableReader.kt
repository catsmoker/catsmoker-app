package com.catsmoker.app.shared.util

import android.app.ActivityManager
import android.content.Context
import java.io.File

/**
 * Single source of truth for `/proc/meminfo` memory readings (consolidation C1).
 *
 * Both GamingEngine (before/after reclaim deltas) and MetricsEngine (live dashboard
 * values) read the same kernel source with the same ActivityManager fallback.
 * Parsers are total: garbage in means null out — never a 0 that looks measured.
 */
object MemAvailableReader {

    /** Available bytes from a meminfo dump, or null when absent/unparseable. */
    fun parseAvailableBytes(meminfoText: String): Long? {
        val line = meminfoText.lineSequence().firstOrNull { it.startsWith("MemAvailable:") }
            ?: return null
        return line.substringAfter(':').trim().split(Regex("\\s+")).firstOrNull()
            ?.toLongOrNull()?.takeIf { it >= 0 }?.times(1024L)
    }

    /** Total + available bytes from a meminfo dump. Null when no usable total. */
    data class RamFigures(val totalBytes: Long, val availBytes: Long)

    fun parseRamFigures(meminfoText: String): RamFigures? {
        var totalKb: Long? = null
        var availKb: Long? = null
        meminfoText.lineSequence().forEach { line ->
            when {
                line.startsWith("MemTotal:") ->
                    totalKb = line.substringAfter(':').trim().split(Regex("\\s+"))
                        .firstOrNull()?.toLongOrNull()?.takeIf { it > 0 }
                line.startsWith("MemAvailable:") ->
                    availKb = line.substringAfter(':').trim().split(Regex("\\s+"))
                        .firstOrNull()?.toLongOrNull()?.takeIf { it >= 0 }
            }
        }
        val total = totalKb ?: return null
        val avail = availKb ?: return null
        return RamFigures(total * 1024L, avail * 1024L)
    }

    /** Reads `/proc/meminfo` available bytes directly, or null when unreadable. */
    fun readAvailableBytes(): Long? = runCatching {
        File("/proc/meminfo").useLines { lines ->
            lines.firstOrNull { it.startsWith("MemAvailable:") }
                ?.split(Regex("\\s+"))
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?.times(1024L)
        }
    }.getOrNull()

    /** File read with ActivityManager fallback — the GamingEngine before/after contract. */
    fun readAvailableBytesWithFallback(context: Context): Long? {
        readAvailableBytes()?.let { return it }
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.availMem
        }.getOrNull()
    }
}
