package com.catsmoker.app.features.editgamefiles.wuwa

import com.catsmoker.app.features.editgamefiles.ConfigBackupStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for the backup-vs-generated pairing (M100 pre/post surface).
 *
 * Backup entries are timestamped `<millis>_<file>` rows; the match takes the newest
 * row for the requested file and nothing else. Bytes decode BOM-aware
 * (UTF-8/16LE/16BE); anything else yields no diff rather than a garbage full-file
 * mismatch — an unreadable backup must read as absent, because a wall of false
 * red would teach the user to ignore the real one. The diff itself stays
 * [WuwaLineDiff]'s: this file only pairs and decodes.
 */
class WuwaBackupDiffTest {

    private fun entry(name: String, timestamp: Long) =
        ConfigBackupStore.Entry(File("/tmp/$name"), timestamp, 10)

    @Test
    fun newestEntryForTheFileWins() {
        val entries = listOf(
            entry("1000_Engine.ini", 1000),
            entry("3000_Engine.ini", 3000),
            entry("2000_DeviceProfiles.ini", 2000)
        )
        assertEquals(3000L, WuwaBackupDiff.matchEntry(entries, "Engine.ini")?.timestamp)
    }

    @Test
    fun otherFilesNeverMatch() {
        val entries = listOf(entry("3000_DeviceProfiles.ini", 3000))
        assertNull(WuwaBackupDiff.matchEntry(entries, "Engine.ini"))
    }

    @Test
    fun emptyListMeansNoDiff() {
        assertNull(WuwaBackupDiff.matchEntry(emptyList(), "Engine.ini"))
    }

    @Test
    fun utf8Decodes() {
        assertEquals("a=1", WuwaBackupDiff.decodeIniText("a=1".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun utf16WithBomDecodes() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "a=1".toByteArray(Charsets.UTF_16LE)
        assertEquals("a=1", WuwaBackupDiff.decodeIniText(bytes))
    }

    @Test
    fun undecodableBytesMeanNoDiff() {
        // Lone surrogates / truncated sequences: refuse, never a false wall of red.
        assertNull(WuwaBackupDiff.decodeIniText(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41)))
    }

    @Test
    fun compareYieldsSummary() {
        val summary = WuwaBackupDiff.compare("a=1\nb=2".toByteArray(), "a=1\nb=3")
        assertTrue(summary != null)
        assertEquals(1, summary!!.added)
        assertEquals(1, summary.removed)
    }

    @Test
    fun nullBytesMeanNoDiff() {
        assertNull(WuwaBackupDiff.compare(null, "a=1"))
    }
}
