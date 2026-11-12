package com.catsmoker.app.application.privacy



/** Explicit choices are versioned: yesterday's default-on flag is not today's opt-in. */
object PrivacyPolicy {
    const val VERSION = 1
    fun analyticsAllowed(enabled: Boolean, choiceVersion: Int): Boolean =
        enabled && choiceVersion == VERSION
    fun accessAllowed(enabled: Boolean, choiceVersion: Int): Boolean =
        enabled && choiceVersion == VERSION
}
