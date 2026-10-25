package com.catsmoker.app.features.gamingtools.engine

/**
 * The storage half of SmartPack Kernel Manager's I/O vocabulary
 * (`utils/kernel/io/IO.java` and `IOAdvanced.java`, read before this file was written), narrowed
 * to one device-derived rule: raise the read-ahead window of the block device that actually
 * backs `/data` to that queue's own declared maximum request size.
 *
 * The reference picks a fixed candidate list (`/sys/block/sda`, `vda`, `mmcblk0`) and lets the
 * user type any read-ahead value. This object instead asks the kernel which device carries
 * `/data` (from `/proc/mounts`), so it can never tune an external SD card or a loop device by
 * accident, and takes the target from `queue/max_sectors_kb` — the largest request that queue
 * says it accepts. A "gaming" constant such as 2048 KB would be inexpressible here.
 *
 * Deliberately not written: the I/O scheduler (best is device- and workload-specific; switching
 * away from the vendor's choice is not a raise), `nr_requests`, `iostats`, `rq_affinity`,
 * `nomerges`, `add_random`, `rotational` (queue-shape toggles with no declared performance
 * direction), and every `dm-*`/`loop*`/`zram*` queue (the `/data` device is resolved first, and
 * the ROM's own tuning of the others is left alone). Behavior — snapshot priors, read-back and
 * restore — lives in [GamingEngine].
 */
object StorageTuning {

    /** `read_ahead_kb`: the queue's read-ahead window, in kilobytes. */
    const val READ_AHEAD_KB = "read_ahead_kb"

    /** `max_sectors_kb`: the largest request the queue declares it accepts, in kilobytes. */
    const val MAX_SECTORS_KB = "max_sectors_kb"

    /**
     * The block device name behind a `/proc/mounts` source (`/dev/block/sda16` → `sda`).
     *
     * Partitions are folded onto their whole disk (`sda16`, `mmcblk0p42`, `nvme0n1p3`), because
     * the queue lives on the disk. Device-mapper names (`dm-0`) are kept as they are. Anything
     * that is not a real storage disk — tmpfs, overlay, an unresolved `by-name` symlink, a loop,
     * zram or ram device — yields null, so the caller writes nothing rather than guessing.
     */
    fun dataBlockDeviceName(mountSource: String?): String? {
        val name = mountSource?.trim()?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: return null
        SCSI_PARTITION.find(name)?.let { return it.groupValues[1] }
        MMC_PARTITION.find(name)?.let { return it.groupValues[1] }
        return name.takeIf { it.matches(WHOLE_DISK) }
    }

    /**
     * The mount source behind `/data` in a `/proc/mounts` dump, or null when no line mounts
     * `/data`. Split on whitespace, as the kernel formats every line; the second field is the
     * mount point, so `/data_mirror` and the other mirrors never match.
     */
    fun dataMountSource(mounts: String?): String? =
        mounts?.lineSequence()
            ?.map { it.trim().split(WHITESPACE) }
            ?.firstOrNull { it.size >= 2 && it[1] == "/data" }
            ?.get(0)

    /** The sysfs queue directory for a whole-disk name. */
    fun queueDir(deviceName: String): String = "/sys/block/$deviceName/queue"

    /**
     * The change-needed target: the queue's own `max_sectors_kb` when it is a real size strictly
     * above the current read-ahead, else null. A zero/absent/unparseable maximum means the device
     * declared no target, and a current value already at or above it is never lowered.
     */
    fun readAheadTarget(current: String?, maxSectorsKb: String?): String? {
        val now = current?.trim()?.toLongOrNull() ?: return null
        val declaredMax = maxSectorsKb?.trim()?.toLongOrNull() ?: return null
        if (now < 0L || declaredMax <= 0L) return null
        return if (declaredMax > now) declaredMax.toString() else null
    }

    /**
     * The desired state for an idempotent re-apply (recovery): the larger of the current value
     * and the queue's declared maximum. Null when either side is unreadable or the maximum is
     * not a real size.
     */
    fun desiredReadAhead(current: String?, maxSectorsKb: String?): String? {
        val now = current?.trim()?.toLongOrNull() ?: return null
        val declaredMax = maxSectorsKb?.trim()?.toLongOrNull() ?: return null
        if (now < 0L || declaredMax <= 0L) return null
        return maxOf(now, declaredMax).toString()
    }

    /** `sda16` → `sda`, `vda1` → `vda`, `hda2` → `hda`. */
    private val SCSI_PARTITION = Regex("""^((?:sd|vd|hd)[a-z]+)\d+$""")

    /** `mmcblk0p42` → `mmcblk0`, `nvme0n1p3` → `nvme0n1`. */
    private val MMC_PARTITION = Regex("""^((?:mmcblk\d+)|(?:nvme\d+n\d+))p\d+$""")

    /** Names that are already a whole disk (or a mapper device) and need no folding. */
    private val WHOLE_DISK = Regex("""^(?:sd[a-z]+|vd[a-z]+|hd[a-z]+|mmcblk\d+|nvme\d+n\d+|dm-\d+)$""")

    private val WHITESPACE = Regex("\\s+")
}
