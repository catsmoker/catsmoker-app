package com.catsmoker.app.features.gamingtools.engine

import java.util.Locale

/**
 * Parses `fstrim -v` output into trimmed bytes per mount.
 *
 * Filesystem trim tells flash storage which blocks are free again, which keeps writes fast —
 * the storage-side counterpart to the RAM boost. Each successfully trimmed mount prints
 * `<path>: <bytes> bytes trimmed`; anything else on a line (FITRIM failures, read-only
 * mounts) counts as a failed mount and never as a number. Output with no success line yields
 * null: a trim that freed nothing measurable is "no reading", not a 0-byte success.
 */
object StorageTrim {

    /** Bytes trimmed per mount, plus how many mounts refused. */
    data class Result(val mounts: Map<String, Long>, val failedMounts: Int) {
        val totalBytes: Long get() = mounts.values.sum()
    }

    private val SUCCESS = Regex("""^(\S+):\s+(\d+)\s+bytes trimmed\s*$""")
    private val FAILURE_HINT = Regex("""\b(failed|denied|read-only|Read-only|error)\b""")

    fun parse(output: String): Result? {
        val mounts = linkedMapOf<String, Long>()
        var failed = 0
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            SUCCESS.find(line)?.let { match ->
                mounts[match.groupValues[1]] = match.groupValues[2].toLong()
            } ?: run {
                if (FAILURE_HINT.containsMatchIn(line)) failed++
            }
        }
        return if (mounts.isEmpty()) null else Result(mounts, failed)
    }

    /** `1.2 GiB`, `50.0 MiB`, `512 B` — the units `du`/`df` users already read. */
    fun formatBytes(bytes: Long): String {
        val gib = bytes.toDouble() / (1024 * 1024 * 1024)
        if (gib >= 1.0) return String.format(Locale.US, "%.1f GiB", gib)
        val mib = bytes.toDouble() / (1024 * 1024)
        if (mib >= 1.0) return String.format(Locale.US, "%.1f MiB", mib)
        val kib = bytes.toDouble() / 1024
        if (kib >= 1.0) return String.format(Locale.US, "%.1f KiB", kib)
        return "$bytes B"
    }
}
