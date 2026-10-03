package com.catsmoker.app.features.spoofdevice

/**
 * One-shot inbox for profiles received via Android SHARE/VIEW intents.
 *
 * MainActivity copies the incoming content to [pendingText] (never a Uri — the
 * granting process may die before the user opens the Profiles screen); the Profiles
 * list takes it once and clears it. Plain in-memory state on purpose: a reboot
 * discards a pending import rather than importing it twice.
 */
object SpoofProfileImportInbox {
    @Volatile
    var pendingText: String? = null
        private set

    fun offer(text: String) {
        if (text.isNotBlank()) pendingText = text
    }

    /** Returns and clears the pending import, or null when there is none. */
    fun take(): String? {
        val text = pendingText
        pendingText = null
        return text
    }
}
