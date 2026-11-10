package com.catsmoker.app.system.ads

import org.junit.Assert.*
import org.junit.Test

class AdConsentPolicyTest {
    @Test fun noRequestsBeforeUpdatedConsentAndInitialization() {
        assertFalse(AdConsentPolicy.canLoad(false, true, true, true, false))
        assertFalse(AdConsentPolicy.canLoad(true, false, true, true, false))
        assertFalse(AdConsentPolicy.canLoad(true, true, false, true, false))
        assertFalse(AdConsentPolicy.canLoad(true, true, true, false, false))
        assertFalse(AdConsentPolicy.canLoad(true, true, true, true, true))
        assertTrue(AdConsentPolicy.canLoad(true, true, true, true, false))
    }
}
