package com.catsmoker.app.application.privacy

import org.junit.Assert.*
import org.junit.Test

class PrivacyPolicyTest {
    @Test fun analyticsRequiresAcceptedTermsAndACurrentOptIn() {
        val current = PrivacyPolicy.VERSION
        assertFalse(PrivacyPolicy.analyticsAllowed(false, true, current))
        assertFalse(PrivacyPolicy.analyticsAllowed(true, true, 0))
        assertFalse(PrivacyPolicy.analyticsAllowed(true, false, current))
        assertTrue(PrivacyPolicy.analyticsAllowed(true, true, current))
    }

    @Test fun sensitiveAccessRequiresTheCurrentExplicitChoice() {
        assertFalse(PrivacyPolicy.accessAllowed(true, 0))
        assertFalse(PrivacyPolicy.accessAllowed(false, PrivacyPolicy.VERSION))
        assertTrue(PrivacyPolicy.accessAllowed(true, PrivacyPolicy.VERSION))
    }
}
