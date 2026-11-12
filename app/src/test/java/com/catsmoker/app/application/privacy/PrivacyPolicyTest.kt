package com.catsmoker.app.application.privacy

import org.junit.Assert.*
import org.junit.Test

class PrivacyPolicyTest {
    @Test fun oldDefaultIsNotExplicitAnalyticsConsent() {
        assertFalse(PrivacyPolicy.analyticsAllowed(true, 0))
        assertFalse(PrivacyPolicy.analyticsAllowed(false, PrivacyPolicy.VERSION))
        assertTrue(PrivacyPolicy.analyticsAllowed(true, PrivacyPolicy.VERSION))
    }

    @Test fun sensitiveAccessRequiresTheCurrentExplicitChoice() {
        assertFalse(PrivacyPolicy.accessAllowed(true, 0))
        assertFalse(PrivacyPolicy.accessAllowed(false, PrivacyPolicy.VERSION))
        assertTrue(PrivacyPolicy.accessAllowed(true, PrivacyPolicy.VERSION))
    }
}
