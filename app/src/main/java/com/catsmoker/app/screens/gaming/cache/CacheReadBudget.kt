package com.catsmoker.app.screens.gaming.cache

import java.io.InputStream

/** One cumulative budget across all files. Never evicts or locks pages. */
class CacheReadBudget(
    val maxBytes: Long,
    private val deadlineMillis: Long,
    private val clock: () -> Long,
    private val memorySafe: () -> Boolean,
    private val checkCancelled: () -> Unit
) {
    var bytesRead = 0L
        private set
    var stopReason: String? = null
        private set

    fun read(input: InputStream, length: Long): Long {
        val start = bytesRead
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0) {
            checkCancelled()
            stopReason = when {
                bytesRead >= maxBytes -> "byte_budget"
                clock() >= deadlineMillis -> "time_budget"
                !memorySafe() -> "memory_pressure"
                else -> null
            }
            if (stopReason != null) break
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining, maxBytes - bytesRead).toInt())
            if (count < 0) break
            if (count == 0) { stopReason = "no_progress"; break }
            bytesRead += count
            remaining -= count
        }
        if (bytesRead >= maxBytes && stopReason == null) stopReason = "byte_budget"
        return bytesRead - start
    }
}
