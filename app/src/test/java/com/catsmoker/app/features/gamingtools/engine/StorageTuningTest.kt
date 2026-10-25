package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [StorageTuning]: the `/data` block device is discovered from the kernel's own
 * mount table, and its read-ahead target is the queue's own declared maximum request size — never
 * a copied "gaming" constant.
 */
class StorageTuningTest {

    // --- /data device discovery ------------------------------------------------------------

    @Test
    fun `scsi and virtio partitions resolve to their whole disk`() {
        assertEquals("sda", StorageTuning.dataBlockDeviceName("/dev/block/sda16"))
        assertEquals("vda", StorageTuning.dataBlockDeviceName("/dev/block/vda1"))
        assertEquals("hda", StorageTuning.dataBlockDeviceName("/dev/block/hda2"))
        assertEquals("sda", StorageTuning.dataBlockDeviceName("/dev/block/sda"))
    }

    @Test
    fun `mmc and nvme partitions resolve to their whole disk`() {
        assertEquals("mmcblk0", StorageTuning.dataBlockDeviceName("/dev/block/mmcblk0p42"))
        assertEquals("mmcblk0", StorageTuning.dataBlockDeviceName("/dev/block/mmcblk0"))
        assertEquals("nvme0n1", StorageTuning.dataBlockDeviceName("/dev/block/nvme0n1p3"))
        assertEquals("nvme0n1", StorageTuning.dataBlockDeviceName("/dev/block/nvme0n1"))
    }

    @Test
    fun `device mapper keeps its own name`() {
        assertEquals("dm-0", StorageTuning.dataBlockDeviceName("/dev/block/dm-0"))
        assertEquals("dm-50", StorageTuning.dataBlockDeviceName("/dev/block/dm-50"))
    }

    @Test
    fun `a mount source that is not a real storage disk writes nothing`() {
        assertNull(StorageTuning.dataBlockDeviceName(null))
        assertNull(StorageTuning.dataBlockDeviceName(""))
        assertNull(StorageTuning.dataBlockDeviceName("tmpfs"))
        assertNull(StorageTuning.dataBlockDeviceName("overlay"))
        assertNull(StorageTuning.dataBlockDeviceName("/dev/block/by-name/userdata"))
        assertNull(StorageTuning.dataBlockDeviceName("/dev/block/loop0"))
        assertNull(StorageTuning.dataBlockDeviceName("/dev/block/zram0"))
        assertNull(StorageTuning.dataBlockDeviceName("/dev/block/ram0"))
    }

    @Test
    fun `queue path is the block device's own sysfs queue`() {
        assertEquals("/sys/block/sda/queue", StorageTuning.queueDir("sda"))
    }

    // --- /data discovery from the kernel's mount table -------------------------------------

    @Test
    fun `data mount source is the second field of the line that mounts data`() {
        val mounts = """
            tmpfs /dev tmpfs rw,seclabel 0 0
            /dev/block/sda16 /data f2fs rw,lazytime,seclabel 0 0
            /dev/block/sda16 /data_mirror/data_ce/null f2fs rw 0 0
        """.trimIndent()
        assertEquals("/dev/block/sda16", StorageTuning.dataMountSource(mounts))
        // The whole chain: mount source -> whole disk -> sda's queue.
        assertEquals(
            "/sys/block/sda/queue",
            StorageTuning.queueDir(StorageTuning.dataBlockDeviceName(StorageTuning.dataMountSource(mounts))!!)
        )
    }

    @Test
    fun `a mount table without a real data mount writes nothing`() {
        assertNull(StorageTuning.dataMountSource(null))
        assertNull(StorageTuning.dataMountSource(""))
        assertNull(
            StorageTuning.dataMountSource("/dev/block/sda16 /data_mirror f2fs rw 0 0")
        )
        assertNull(StorageTuning.dataMountSource("malformed"))
    }

    // --- read-ahead target -----------------------------------------------------------------

    @Test
    fun `read-ahead is raised to the queue's declared maximum request size`() {
        assertEquals("512", StorageTuning.readAheadTarget("128", "512"))
        assertEquals("1280", StorageTuning.readAheadTarget("128", "1280"))
    }

    @Test
    fun `read-ahead is never lowered or invented`() {
        assertNull(StorageTuning.readAheadTarget("512", "512"))
        assertNull(StorageTuning.readAheadTarget("2048", "512"))
        assertNull(StorageTuning.readAheadTarget("128", "0"))
        assertNull(StorageTuning.readAheadTarget("128", null))
        assertNull(StorageTuning.readAheadTarget(null, "512"))
        assertNull(StorageTuning.readAheadTarget("abc", "512"))
        assertNull(StorageTuning.readAheadTarget("128", "abc"))
    }

    @Test
    fun `desired read-ahead keeps an already-at-target value reportable`() {
        assertEquals("512", StorageTuning.desiredReadAhead("512", "512"))
        // A window someone else already raised above the target is never narrowed back to it.
        assertEquals("2048", StorageTuning.desiredReadAhead("2048", "512"))
        assertEquals("512", StorageTuning.desiredReadAhead("128", "512"))
        assertNull(StorageTuning.desiredReadAhead("128", "0"))
        assertNull(StorageTuning.desiredReadAhead(null, "512"))
        assertNull(StorageTuning.desiredReadAhead("128", null))
    }
}
