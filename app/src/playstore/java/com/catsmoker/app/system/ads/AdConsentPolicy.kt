package com.catsmoker.app.system.ads

internal object AdConsentPolicy {
    fun canLoad(onboardingComplete: Boolean, updated: Boolean, canRequest: Boolean,
                initialized: Boolean, adFree: Boolean): Boolean =
        onboardingComplete && updated && canRequest && initialized && !adFree
}
