package com.catsmoker.app.shared

import com.catsmoker.app.shared.util.ByteSizeFormat
import com.catsmoker.app.shared.util.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Pins the app's one byte formatter.
 *
 * The corrupted "12 ┬╖ MB" scan lines were a resource-encoding bug, but the same screen also mixed
 * two formatters that disagreed about what `MB` meant (one divided by 1024 while saying KB/MB, the
 * other correctly said KiB/MiB). Both are gone; every size on Gaming Tools now goes through
 * [ByteSizeFormat], and these cases pin the units, the rounding and the locale handling that a
 * translated card depends on.
 */
class ByteSizeFormatTest {

    @Test
    fun zeroAndNegativeBytesAreZero() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("0 B", formatBytes(-1L))
        assertEquals("0 B", formatBytes(Long.MIN_VALUE))
    }

    @Test
    fun oneByteIsExact() {
        assertEquals("1 B", formatBytes(1L))
    }

    @Test
    fun belowOneKibibytePrintsExactBytes() {
        assertEquals("512 B", formatBytes(512L))
        assertEquals("1023 B", formatBytes(1023L))
    }

    @Test
    fun oneKibibyteUsesTheBinaryUnit() {
        assertEquals("1.0 KiB", ByteSizeFormat.format(1024L, Locale.US))
        assertEquals("1.5 KiB", ByteSizeFormat.format(1536L, Locale.US))
    }

    @Test
    fun nearOneMebibyteRoundsUpIntoTheLargerUnit() {
        // 1023.999 KiB must not print as "1024.0 KiB"; the value steps up to MiB instead.
        assertEquals("1.0 MiB", ByteSizeFormat.format(1_048_575L, Locale.US))
        assertEquals("1.0 MiB", ByteSizeFormat.format(1_048_576L, Locale.US))
    }

    @Test
    fun multipleMebibytesKeepOneDecimal() {
        assertEquals("50.0 MiB", ByteSizeFormat.format(52_428_800L, Locale.US))
        // 3 MiB + 500 000 B = 3.477 MiB
        assertEquals("3.5 MiB", ByteSizeFormat.format(3_645_728L, Locale.US))
    }

    @Test
    fun nearOneGibibyteRoundsUpIntoTheLargerUnit() {
        assertEquals("1.0 GiB", ByteSizeFormat.format(1_073_741_823L, Locale.US))
        assertEquals("1.2 GiB", ByteSizeFormat.format(1_288_490_188L, Locale.US))
    }

    @Test
    fun decimalSeparatorFollowsTheLocaleWhileTheUnitDoesNot() {
        assertEquals("1.5 KiB", ByteSizeFormat.format(1536L, Locale.US))
        assertEquals("1,5 KiB", ByteSizeFormat.format(1536L, Locale.GERMANY))
        assertEquals("1,5 KiB", ByteSizeFormat.format(1536L, Locale.FRANCE))
        // The unit abbreviation is a measurement symbol, not copy: it never translates.
        assertEquals("50,0 MiB", ByteSizeFormat.format(52_428_800L, Locale.GERMANY))
    }

    @Test
    fun veryLargeValuesStayInsideTheLargestUnit() {
        val text = ByteSizeFormat.format(Long.MAX_VALUE, Locale.US)
        assertEquals(true, text.endsWith(" PiB"))
    }
}
