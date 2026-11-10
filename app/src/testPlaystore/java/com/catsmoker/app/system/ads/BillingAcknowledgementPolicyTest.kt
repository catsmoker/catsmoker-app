package com.catsmoker.app.system.ads

import org.junit.Assert.*
import org.junit.Test

class BillingAcknowledgementPolicyTest {
    @Test fun transientAndStaleOwnershipFailuresRetryButConfigurationErrorsDoNot() {
        listOf(-3, -1, 2, 6, 8, 12).forEach { assertTrue(BillingAcknowledgementPolicy.shouldRetry(it)) }
        listOf(0, -2, 3, 4, 5).forEach { assertFalse(BillingAcknowledgementPolicy.shouldRetry(it)) }
    }
}
