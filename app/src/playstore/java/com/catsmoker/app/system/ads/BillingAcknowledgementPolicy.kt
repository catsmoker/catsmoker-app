package com.catsmoker.app.system.ads

/** Google Play response codes whose acknowledgement/query can succeed on retry. */
internal object BillingAcknowledgementPolicy {
    fun shouldRetry(responseCode: Int): Boolean = responseCode in setOf(-3, -1, 2, 6, 8, 12)
}
