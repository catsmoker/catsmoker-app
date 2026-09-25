package com.catsmoker.app.shared.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behaviour lock for the shared memory reader (Phase 1 / C1).
 *
 * Single source of truth for `/proc/meminfo` parsing used by both
 * GamingEngine (before/after deltas) and MetricsEngine (live values).
 * Garbage in means null out — never a 0 that looks measured.
 */
class MemAvailableReaderTest {

    @Test
    fun parsesAvailableBytes() {
        val text = "MemTotal:        7856132 kB\nMemAvailable:    3928066 kB\n"
        assertEquals(3928066L * 1024L, MemAvailableReader.parseAvailableBytes(text))
    }

    @Test
    fun missingAvailableIsNull() {
        assertNull(MemAvailableReader.parseAvailableBytes("MemTotal: 100 kB\n"))
        assertNull(MemAvailableReader.parseAvailableBytes(""))
        assertNull(MemAvailableReader.parseAvailableBytes("MemAvailable: hot kB\n"))
    }

    @Test
    fun parsesRamFigures() {
        val text = "MemTotal:        8000000 kB\nMemAvailable:    2000000 kB\n"
        val figures = MemAvailableReader.parseRamFigures(text)!!
        assertEquals(8000000L * 1024L, figures.totalBytes)
        assertEquals(2000000L * 1024L, figures.availBytes)
    }

    @Test
    fun ramFiguresNeedsTotal() {
        assertNull(MemAvailableReader.parseRamFigures("MemAvailable: 10 kB\n"))
        assertNull(MemAvailableReader.parseRamFigures(""))
    }
}
