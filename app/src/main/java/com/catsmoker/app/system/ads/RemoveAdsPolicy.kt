package com.catsmoker.app.system.ads

/**
 * Shared ad-removal policy: the single source of truth for the Play-variant
 * `remove_ads` entitlement rule. Both variants compile against this; only the
 * playstore variant's [RemoveAdsRepository] evaluates real Google Play
 * purchases through it.
 *
 * The product is a Google Play Billing non-consumable one-time product: it is
 * never consumed, and the entitlement is granted only while Play reports the
 * purchase in the PURCHASED state. Pending, unspecified, unknown-product and
 * empty inputs never grant anything, so ads stay ON until Play confirms.
 */
object RemoveAdsPolicy {
    /**
     * Google Play Console one-time product id for permanent ad removal. Must
     * match the product configured in Play Console exactly.
     */
    const val PRODUCT_ID = "remove_ads"

    /**
     * Private prefs key holding the last VERIFIED entitlement. On the
     * playstore variant it is written only by `RemoveAdsRepository` after
     * Play reports PURCHASED (and cleared when Play reports no such
     * purchase); it is never set from any Settings toggle.
     */
    const val ENTITLED_PREF_KEY = "remove_ads_entitled"

    /**
     * Legacy free-toggle key. The full variant's [AdManager] honors it; the
     * playstore variant's [AdManager] deliberately ignores it so editing the
     * old preference can never disable ads there.
     */
    const val LEGACY_PREF_KEY = "ads_enabled"

    /** Purchase states that matter for the grant decision. */
    enum class VerifiedState {
        PURCHASED,
        PENDING,
        UNSPECIFIED
    }

    /**
     * Grants the ad-free entitlement only for [PRODUCT_ID] in the PURCHASED
     * state. Merely starting the purchase flow (pending/unspecified) or owning
     * any other product grants nothing.
     */
    fun grantsEntitlement(productIds: Collection<String>, state: VerifiedState): Boolean =
        state == VerifiedState.PURCHASED && PRODUCT_ID in productIds

    /**
     * Play requires every granted non-consumable purchase to be acknowledged
     * (an unacknowledged purchase is refunded and revoked). Acknowledge only
     * granted-but-not-yet-acknowledged purchases.
     */
    fun needsAcknowledgement(entitled: Boolean, isAcknowledged: Boolean): Boolean =
        entitled && !isAcknowledged
}
