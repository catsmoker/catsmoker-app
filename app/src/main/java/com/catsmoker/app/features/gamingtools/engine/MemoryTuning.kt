package com.catsmoker.app.features.gamingtools.engine

/**
 * The memory-watermark half of SmartPack Kernel Manager's `VM`/`LMK` vocabulary
 * (`utils/kernel/vm/VM.java`, `utils/kernel/lmk/LMK.java`; read before this file was written),
 * narrowed to two raise-only rules whose targets come from the device itself.
 *
 * The reference exposes every `/proc/sys/vm` knob and every legacy lowmemorykiller parameter as a
 * free-form field. Most have no performance direction that can be derived from device state, so
 * this object writes only:
 *
 *  - `min_free_kbytes` — raised toward **1 % of `MemTotal`**, the low-watermark guideline for a
 *    large-RAM device. It keeps reclaim headroom, which reduces direct-reclaim stalls when a game
 *    asks for a large allocation. A device already above the guideline is never lowered.
 *  - `extra_free_kbytes` (CAF only) — raised only toward the device's own `min_free_kbytes`, so
 *    the extra reserve can never exceed the base reserve the kernel itself chose. A value already
 *    above it is left where the vendor put it.
 *
 * Deliberately not written, with the reason: `swappiness` (the gaming direction is a *lower*
 * value, which this raise-only family cannot express), `vfs_cache_pressure`, `dirty_*`,
 * `watermark_scale_factor` (no device-declared target; a "tuning" constant would be invented),
 * and the legacy `lowmemorykiller/parameters/minfree` (modern Android runs `lmkd`, which owns
 * process killing; raising the legacy table would kill background apps earlier without a
 * declared target). Behavior — snapshot priors, read-back and restore — lives in [GamingEngine].
 */
object MemoryTuning {

    /** `min_free_kbytes`: the VM's minimum free-page reserve. */
    const val MIN_FREE_KBYTES = "/proc/sys/vm/min_free_kbytes"

    /** `extra_free_kbytes`: CAF's additional reserve above the minimum. */
    const val EXTRA_FREE_KBYTES = "/proc/sys/vm/extra_free_kbytes"

    /** `/proc/meminfo`, read only for `MemTotal`. */
    const val MEM_INFO = "/proc/meminfo"

    /** The reserve guideline: one percent of the device's total RAM, in kilobytes. */
    private const val MIN_FREE_PERCENT = 100L

    /**
     * `MemTotal` from a `/proc/meminfo` dump, in kilobytes, or null when the line is absent,
     * unparseable or zero. Never a guessed RAM size.
     */
    fun memTotalKb(memInfo: String?): Long? {
        val match = MEM_TOTAL.find(memInfo ?: return null) ?: return null
        val value = match.groupValues[1].toLongOrNull() ?: return null
        return value.takeIf { it > 0L }
    }

    /**
     * The change-needed `min_free_kbytes` target: 1 % of [memTotalKb] when that is strictly above
     * the current value, else null. A RAM size too small to yield a non-zero percentage, an
     * unreadable current value, or an already-higher reserve writes nothing.
     */
    fun minFreeTarget(currentKb: String?, memTotalKb: Long?): String? {
        val now = currentKb?.trim()?.toLongOrNull() ?: return null
        val total = memTotalKb ?: return null
        if (now < 0L || total <= 0L) return null
        val target = total / MIN_FREE_PERCENT
        if (target <= 0L) return null
        return if (target > now) target.toString() else null
    }

    /**
     * The change-needed `extra_free_kbytes` target: the device's own `min_free_kbytes` when that
     * is strictly above the current extra reserve, else null. The extra reserve is a second,
     * vendor-specific headroom knob; the kernel's own base reserve is its only declared target.
     */
    fun extraFreeTarget(currentExtraKb: String?, minFreeKb: String?): String? {
        val extra = currentExtraKb?.trim()?.toLongOrNull() ?: return null
        val base = minFreeKb?.trim()?.toLongOrNull() ?: return null
        if (extra < 0L || base <= 0L) return null
        return if (base > extra) base.toString() else null
    }

    /**
     * The desired `min_free_kbytes` for an idempotent re-apply (recovery): the larger of the
     * current value and the 1 % guideline. Null when either side is unreadable.
     */
    fun desiredMinFree(currentKb: String?, memTotalKb: Long?): String? {
        val now = currentKb?.trim()?.toLongOrNull() ?: return null
        val total = memTotalKb ?: return null
        if (now < 0L || total <= 0L) return null
        val guideline = total / MIN_FREE_PERCENT
        if (guideline <= 0L) return null
        return maxOf(now, guideline).toString()
    }

    /**
     * The desired `extra_free_kbytes` for an idempotent re-apply (recovery): the larger of the
     * current extra reserve and the device's own `min_free_kbytes`. Null when either is unreadable.
     */
    fun desiredExtraFree(currentExtraKb: String?, minFreeKb: String?): String? {
        val extra = currentExtraKb?.trim()?.toLongOrNull() ?: return null
        val base = minFreeKb?.trim()?.toLongOrNull() ?: return null
        if (extra < 0L || base <= 0L) return null
        return maxOf(extra, base).toString()
    }

    private val MEM_TOTAL = Regex("""(?m)^\s*MemTotal:\s*(\d+)\s*kB\s*$""", RegexOption.IGNORE_CASE)
}
