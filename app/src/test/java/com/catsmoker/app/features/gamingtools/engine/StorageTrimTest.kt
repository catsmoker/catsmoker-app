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
    fun blankLinesAreIgnored() {
        val result = StorageTrim.parse("\n  \n/data: 2048 bytes trimmed\n")!!
        assertEquals(2048L, result.totalBytes)
        assertTrue(result.mounts.contains("/data"))
    }

    @Test
    fun measuredDirectTrimWins() {
        // A successful fstrim is the only channel that reports bytes, so the platform fallback
        // must not run and the measurement is reported as-is.
        val outcome = StorageTrim.outcome(
            direct = StorageTrim.Attempt("/data: 4096 bytes trimmed", 0),
            platform = null
        )
        val measured = outcome as StorageTrim.Outcome.Measured
        assertEquals(4096L, measured.trimmed.totalBytes)
    }

    @Test
    fun missingDirectCommandFallsBackToPlatformTrim() {
        // This device's toybox has no fstrim, so the direct command exits 127. `sm fstrim` trims
        // through vold and succeeds, but prints only a PersistableBundle line: no byte count.
        val outcome = StorageTrim.outcome(
            direct = StorageTrim.Attempt(
                "/system/bin/sh: <stdin>[15587]: fstrim: inaccessible or not found",
                127
            ),
            platform = StorageTrim.Attempt("PersistableBundle[{}]", 0)
        )
        assertEquals(StorageTrim.Outcome.TrimmedWithoutReport, outcome)
    }

    @Test
    fun bothChannelsFailingCarriesThePlatformRefusal() {
        val outcome = StorageTrim.outcome(
            direct = StorageTrim.Attempt("fstrim: inaccessible or not found", 127),
            platform = StorageTrim.Attempt("Failure calling service vold", 1)
        )
        val failed = outcome as StorageTrim.Outcome.Failed
        assertEquals("Failure calling service vold", failed.detail)
    }

    @Test
    fun silentPlatformFailureCarriesTheDirectMessage() {
        val outcome = StorageTrim.outcome(
            direct = StorageTrim.Attempt("fstrim: inaccessible or not found", 127),
            platform = StorageTrim.Attempt("", 1)
        )
        val failed = outcome as StorageTrim.Outcome.Failed
        assertEquals("fstrim: inaccessible or not found", failed.detail)
    }
}
