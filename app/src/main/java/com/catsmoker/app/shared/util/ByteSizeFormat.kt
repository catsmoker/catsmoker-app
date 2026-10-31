package com.catsmoker.app.shared.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.round

/**
 * Formats a measured byte count for display in binary units — `1536` reads as `1.5 KiB`.
 *
 * This is the app's single byte formatter. Gaming Tools carried two: this one (labelled KB/MB/GB
 * while dividing by 1024, so 1 MB meant 1 MiB) and a second copy inside `StorageTrim` with the
 * correct KiB/MiB labels. Two spellings of the same measurement is how a scan result and a trim
 * result could disagree about how much data the same number represents; both now call this.
 *
 * Units are the binary ones the divisor actually uses: B, KiB, MiB, GiB, TiB, PiB. Below 1 KiB
 * the exact byte count is printed with no decimals; above it, one decimal is enough to read and
 * never claims more precision than the measurement has.
 *
 * The number is formatted in `locale` so a comma decimal separator is used where that is the
 * convention (`1,5 KiB` in German), while the unit abbreviation stays untouched — translating the
 * unit would corrupt a value that is meant to be comparable across languages.
 *
 * Every caller is reporting a number the device gave back (bytes freed, bytes found by the
 * scanner), so `0` is a legitimate result and returns `"0 B"` rather than being hidden. Negative
 * input has no meaning for a byte count and is treated the same way instead of printing `-1 B`.
 *
 */
object ByteSizeFormat {

    /** Binary unit suffixes, index = power of 1024. */
    private val UNITS = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB")

    fun format(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes <= 0L) return "0 B"

        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < UNITS.lastIndex) {
            value /= 1024.0
            unit++
        }

        // Rounding to one decimal can turn 1023.98 MiB into "1024.0 MiB", which is both ugly and
        // outside the unit's own range. Step up first so the printed number always fits its unit.
        if (unit > 0) {
            val rounded = round(value * 10.0) / 10.0
            if (rounded >= 1024.0 && unit < UNITS.lastIndex) {
                value /= 1024.0
                unit++
            }
        }

        val number = NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = if (unit == 0) 0 else 1
            maximumFractionDigits = if (unit == 0) 0 else 1
        }.format(value)
        return "$number ${UNITS[unit]}"
    }
}

/** Convenience entry point for call sites that want the process locale. */
fun formatBytes(bytes: Long): String = ByteSizeFormat.format(bytes)
