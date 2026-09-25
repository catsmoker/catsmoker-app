package com.catsmoker.app.features.gamingtools.tools.dns

import kotlin.math.roundToInt

/**
 * Extracts the reply time from one `ping` run's output.
 *
 * The reply's own `time=` field, as the reference reads it — the same rule the dashboard's
 * ping uses. No `time=` means no reply arrived (unreachable, blocked, or no ping binary),
 * which yields null: never 0 ms, which no network achieves.
 */
object PingParser {

    fun parseTimeMs(output: String): Int? {
        if (!output.contains("time=")) return null
        return output.substringAfter("time=")
            .trimStart()
            .takeWhile { it.isDigit() || it == '.' }
            .toFloatOrNull()
            ?.roundToInt()
    }
}
