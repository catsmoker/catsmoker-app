package com.catsmoker.app.system.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Play-variant ad-removal grant rule: only a Google Play purchase in
 * the PURCHASED state for the [RemoveAdsPolicy.PRODUCT_ID] product grants the
 * ad-free entitlement. Pending, unspecified, unknown-product and empty inputs
 * must never grant it (ads stay ON).
 */
class RemoveAdsPolicyTest {

    @Test
    fun purchasedRemoveAdsGrantsEntitlement() {
        assertTrue(
            RemoveAdsPolicy.grantsEntitlement(
                listOf(RemoveAdsPolicy.PRODUCT_ID),
                RemoveAdsPolicy.VerifiedState.PURCHASED
            )
        )
    }

    @Test
    fun purchasedAmongOtherProductsGrantsEntitlement() {
        assertTrue(
            RemoveAdsPolicy.grantsEntitlement(
                listOf("some_other_product", RemoveAdsPolicy.PRODUCT_ID),
                RemoveAdsPolicy.VerifiedState.PURCHASED
            )
        )
    }

    @Test
    fun pendingPurchaseDoesNotGrantEntitlement() {
        assertFalse(
            RemoveAdsPolicy.grantsEntitlement(
                listOf(RemoveAdsPolicy.PRODUCT_ID),
                RemoveAdsPolicy.VerifiedState.PENDING
            )
        )
    }

    @Test
    fun unspecifiedPurchaseDoesNotGrantEntitlement() {
        assertFalse(
            RemoveAdsPolicy.grantsEntitlement(
                listOf(RemoveAdsPolicy.PRODUCT_ID),
                RemoveAdsPolicy.VerifiedState.UNSPECIFIED
            )
        )
    }

    @Test
    fun purchasedUnknownProductDoesNotGrantEntitlement() {
        assertFalse(
            RemoveAdsPolicy.grantsEntitlement(
                listOf("some_other_product"),
                RemoveAdsPolicy.VerifiedState.PURCHASED
            )
        )
    }

    @Test
    fun emptyProductsNeverGrantEntitlement() {
        assertFalse(
            RemoveAdsPolicy.grantsEntitlement(
                emptyList(),
                RemoveAdsPolicy.VerifiedState.PURCHASED
            )
        )
        assertFalse(
            RemoveAdsPolicy.grantsEntitlement(
                emptyList(),
                RemoveAdsPolicy.VerifiedState.PENDING
            )
        )
    }

    @Test
    fun productIdMatchesPlayConsoleConfiguration() {
        assertEquals("remove_ads", RemoveAdsPolicy.PRODUCT_ID)
    }

    @Test
    fun unacknowledgedEntitlementNeedsAcknowledgement() {
        assertTrue(RemoveAdsPolicy.needsAcknowledgement(entitled = true, isAcknowledged = false))
    }

    @Test
    fun acknowledgedEntitlementNeedsNoAcknowledgement() {
        assertFalse(RemoveAdsPolicy.needsAcknowledgement(entitled = true, isAcknowledged = true))
    }

    @Test
    fun unentitledPurchaseNeedsNoAcknowledgement() {
        assertFalse(RemoveAdsPolicy.needsAcknowledgement(entitled = false, isAcknowledged = false))
    }
}
