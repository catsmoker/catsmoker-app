package com.catsmoker.app.screens.gaming.automation

/**
 * Receiver-side burst control before posting notifications or starting a service. Give restore its
 * own lane so a game-exit broadcast isn't suppressed by the game-entry broadcast.
 */
internal class BroadcastAdmission {
    private val seen = linkedMapOf<String, Long>()
    private var lastApply: Long? = null
    private var lastRestore: Long? = null
    private var lastSelection: Long? = null

    fun admit(request: AutomationProtocol.Request, now: Long): String? {
        if (request.isQuery) return null
        seen.entries.removeAll { now - it.value >= RETENTION_MS }
        val id = request.requestId
        if (id != null && id in seen) return "duplicate"
        val selecting = request.operation == AutomationProtocol.Operation.SELECT_GAME
        val last = when {
            request.isRestore -> lastRestore
            selecting -> lastSelection
            else -> lastApply
        }
        if (last != null && now - last < INTERVAL_MS) return "rate_limited"
        when {
            request.isRestore -> lastRestore = now
            selecting -> lastSelection = now
            else -> lastApply = now
        }
        if (id != null) {
            if (seen.size == CAPACITY) seen.remove(seen.keys.first())
            seen[id] = now
        }
        return null
    }

    private companion object {
        const val INTERVAL_MS = 1_000L
        const val RETENTION_MS = 5 * 60_000L
        const val CAPACITY = 64
    }
}
