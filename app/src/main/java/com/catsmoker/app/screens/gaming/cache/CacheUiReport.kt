package com.catsmoker.app.screens.gaming.cache

import org.json.JSONObject

/** Never present another game's last run as evidence for the currently displayed game. */
data class CacheUiReport(val result: String, val bytes: Long, val elapsedMs: Long, val skipped: Int) {
    companion object {
        fun forGame(raw: String, pkg: String): CacheUiReport? = runCatching {
            val json = JSONObject(raw)
            if (json.optString("game") != pkg) return@runCatching null
            val files = json.optJSONArray("files")
            val skipped = (0 until (files?.length() ?: 0)).count {
                files?.optJSONObject(it)?.optString("result") !in setOf("read", "completed")
            }
            CacheUiReport(json.optString("result", "idle"), json.optLong("bytesRead").coerceAtLeast(0),
                json.optLong("elapsedMs").coerceAtLeast(0), skipped)
        }.getOrNull()
    }
}
