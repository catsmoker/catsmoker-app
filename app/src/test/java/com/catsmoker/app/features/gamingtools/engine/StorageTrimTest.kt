package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for `fstrim -v` output parsing.
 *
 * Each mount prints either `<path>: <bytes> bytes trimmed` or a failure line (`FITRIM ioctl
 * failed`, `Read-only file system`). Successes add up; failures are counted, never parsed as
 * numbers — and output with no success line at all yields null, not a 0-byte trim the device
 * never reported.
 */
class StorageTrimTest {

    @Test
    fun sumsSuccessfulMounts() {
        val output = """
            /data: 1288490188 bytes trimmed
            /cache: 52428800 bytes trimmed
        """.trimIndent()
        val result = StorageTrim.parse(output)!!
        assertEquals(1288490188L + 52428800L, result.totalBytes)
        assertEquals(2, result.mounts.size)
        assertEquals(0, result.failedMounts)
    }

    @Test
    fun failuresCountButNeverParse() {
        val output = """
            /data: 1024 bytes trimmed
            fstrim: /system: FITRIM ioctl failed: Read-only file system
        """.trimIndent()
        val result = StorageTrim.parse(output)!!
        assertEquals(1024L, result.totalBytes)
        assertEquals(1, result.mounts.size)
        assertEquals(1, result.failedMounts)
    }

    @Test
    fun noSuccessLineMeansNoReading() {
        assertEquals(null, StorageTrim.parse(""))
        assertEquals(null, StorageTrim.parse("fstrim: /data: FITRIM ioctl failed: Permission denied"))
    }

    @Test
    fun formatsBytesForDisplay() {
        assertEquals("1.2 GiB", StorageTrim.formatBytes(1288490188L))
        assertEquals("50.0 MiB", StorageTrim.formatBytes(52428800L))
        assertEquals("512 B", StorageTrim.formatBytes(512L))
    }

    @Test
    fun blankLinesAreIgnored() {
        val result = StorageTrim.parse("\n  \n/data: 2048 bytes trimmed\n")!!
        assertEquals(2048L, result.totalBytes)
        assertTrue(result.mounts.contains("/data"))
    }
}
