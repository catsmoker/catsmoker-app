package com.catsmoker.app.application.privacy

/**
 * Policy rules behind the persisted privacy choices.
 *
 * Neither an onboarding preview nor a default-true value left by an older build is a
 * saved analytics choice. Gaming access is independently versioned.
 */
object PrivacyPolicy {
    const val VERSION = 1

    fun analyticsAllowed(termsAccepted: Boolean, enabled: Boolean, choiceVersion: Int): Boolean =
        termsAccepted && enabled && choiceVersion == VERSION

    fun accessAllowed(enabled: Boolean, choiceVersion: Int): Boolean =
        enabled && choiceVersion == VERSION
}
