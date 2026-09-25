package com.catsmoker.app.features.gamingtools.tools.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behaviour lock for single-ping `time=` extraction.
 *
 * Same source and rule as the dashboard's ping: the reply's own `time=` field, read as the
 * reference does. A run with no `time=` means no reply arrived — unreachable, blocked, or no
 * ping binary — and yields null, never 0 ms, which no network achieves.
 */
class PingParserTest {

    @Test
    fun extractsWholeMilliseconds() {
        assertEquals(
            12,
            PingParser.parseTimeMs("64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=12.3 ms")
        )
    }

    @Test
    fun roundsFractionalMilliseconds() {
        assertEquals(
            13,
            PingParser.parseTimeMs("64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.7 ms")
        )
    }

    @Test
    fun noReplyMeansNull() {
        assertNull(PingParser.parseTimeMs("1 packets transmitted, 0 received, 100% packet loss"))
        assertNull(PingParser.parseTimeMs(""))
        assertNull(PingParser.parseTimeMs("ping: unknown host example.invalid"))
    }

    @Test
    fun garbageTimeIsNull() {
        assertNull(PingParser.parseTimeMs("time=abc ms"))
    }
}
