package com.catsmoker.app.shared.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the logcat threadtime parser against
 * `reference/debug-overlay/.../LogcatEntryParser.kt` (read before this test was written):
 * epoch/threadtime lines decode to timestamp + level + tag + pid + tid + message; anything
 * else stays unparsed (null) so the caller keeps its substring heuristics for foreign lines
 * instead of misreading them.
 */
class LogcatEntryParserTest {

    @Test
    fun parsesAThreadtimeErrorLine() {
        val entry = LogcatEntryParser.parse("09-25 14:03:11.123  1234  5678 E ActivityManager: ANR in com.game")
        assertEquals("E", entry?.level?.toString())
        assertEquals("ActivityManager", entry?.tag)
        assertEquals(1234, entry?.pid)
        assertEquals(5678, entry?.tid)
        assertEquals("ANR in com.game", entry?.message)
    }

    @Test
    fun parsesAllSixLevels() {
        for (level in "VDIWEF") {
            val entry = LogcatEntryParser.parse("09-25 14:03:11.123  1234  5678 $level SomeTag: hello")
            assertEquals("level $level", level.toString(), entry?.level?.toString())
        }
    }

    @Test
    fun briefFormatAndProseStayUnparsed() {
        assertNull(LogcatEntryParser.parse("E/ActivityManager( 1234): ANR in com.game"))
        assertNull(LogcatEntryParser.parse("beginning of main"))
        assertNull(LogcatEntryParser.parse(""))
        assertNull(LogcatEntryParser.parse("not a log line at all"))
    }

    @Test
    fun levelOfFindsThreadtimeLevels() {
        assertEquals('E', LogcatEntryParser.levelOf("09-25 14:03:11.123  1234  5678 E Tag: x"))
        assertNull(LogcatEntryParser.levelOf("E/Tag( 1234): x"))
    }
}
