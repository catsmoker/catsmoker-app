package com.catsmoker.app.system.ads

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.catsmoker.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play Store-variant ad-removal gateway (Google Play Billing).
 *
 * Same FQN, constructor and public API as the full variant's no-op
 * [RemoveAdsRepository] so shared callers ([SettingsViewModel]) compile
 * against both. The product is the [RemoveAdsPolicy.PRODUCT_ID] non-consumable
 * one-time product: it is never consumed, and the ad-free entitlement is
 * granted only while Play reports it PURCHASED ([RemoveAdsPolicy]).
 *
 * Lifecycle (current Play Billing guidance, client-only app without backend):
 * connect on creation, then on every connect and on every [refresh] query both
 * the product details (for the localized price) and the owned purchases (the
 * entitlement source of truth, which also restores after reinstall). Granted
 * purchases are acknowledged via [BillingClient.acknowledgePurchase] — Play
 * refunds unacknowledged purchases and revokes them. Pending, canceled,
 * failed and already-owned outcomes never grant anything on their own:
 * canceled is silent, pending shows a notice, already-owned re-queries.
 *
 * The last verified entitlement is cached in private prefs so a restart hides
 * ads immediately; only a successful Play query may change it afterwards, so
 * editing any preference can never unlock anything while Play is reachable.
 * [AdManager.isEnabled] reads that same cache and ignores `ads_enabled`.
 */
@Singleton
class RemoveAdsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isAdFree = MutableStateFlow(isEntitledCached())
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    private val _displayPrice = MutableStateFlow<String?>(null)
    val displayPrice: StateFlow<String?> = _displayPrice.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var connectionInFlight = false

    private val purchasesListener =
        com.android.billingclient.api.PurchasesUpdatedListener { result, purchases ->
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    if (purchases != null) handlePurchaseList(purchases)
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    // Silent: backing out of the Play sheet is not an error
                    // and, above all, is not ownership.
                }
                else -> emitMessage(
                    context.getString(R.string.sys_remove_ads_failed, result.debugMessage)
                )
            }
        }

    init {
        scope.launch { connect() }
    }

    /**
     * Re-checks the entitlement with Play (product price + owned purchases).
     * Called on creation, when Settings opens/resumes, and after
     * already-owned outcomes, so a purchase made on another device or before
     * a reinstall is restored without paying again.
     */
    fun refresh() {
        scope.launch {
            val client = billingClient
            if (client == null || !client.isReady) {
                connect()
                return@launch
            }
            queryProductDetails(client)
            queryPurchases(client)
        }
    }

    /**
     * Opens the Play purchase sheet for [RemoveAdsPolicy.PRODUCT_ID]. Returning
     * from this call grants nothing — ownership is decided only by the
     * purchases callback once Play reports PURCHASED.
     */
    fun launchPurchase(activity: Activity) {
        val details = productDetails
        if (details == null) {
            emitMessage(context.getString(R.string.sys_billing_unavailable))
            refresh()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        val result = ensureClient().launchBillingFlow(activity, params)
        if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            refresh()
        } else if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            emitMessage(context.getString(R.string.sys_remove_ads_failed, result.debugMessage))
        }
    }

    private fun prefs() = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private fun isEntitledCached(): Boolean =
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getBoolean(RemoveAdsPolicy.ENTITLED_PREF_KEY, false)

    private fun setEntitled(entitled: Boolean) {
        prefs().edit { putBoolean(RemoveAdsPolicy.ENTITLED_PREF_KEY, entitled) }
        _isAdFree.value = entitled
    }

    private fun emitMessage(text: String) {
        _messages.tryEmit(text)
    }

    private fun ensureClient(): BillingClient {
        billingClient?.let { return it }
        val client = BillingClient.newBuilder(context)
            .setListener(purchasesListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        billingClient = client
        return client
    }

    private fun connect() {
        val client = ensureClient()
        if (client.isReady || connectionInFlight) {
            if (client.isReady) {
                queryProductDetails(client)
                queryPurchases(client)
            }
            return
        }
        connectionInFlight = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connectionInFlight = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails(client)
                    queryPurchases(client)
                }
            }

            override fun onBillingServiceDisconnected() {
                connectionInFlight = false
                // Play asks to retry; the next refresh() reconnects.
            }
        })
    }

    private fun queryProductDetails(client: BillingClient) {
        _isWorking.value = true
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(RemoveAdsPolicy.PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            _isWorking.value = false
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val match = details.productDetailsList
                    .firstOrNull { it.productId == RemoveAdsPolicy.PRODUCT_ID }
                productDetails = match
                _displayPrice.value = match?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
    }

    private fun queryPurchases(client: BillingClient) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchaseList(purchases)
            }
            // Any other outcome (offline, service down) keeps the last verified
            // cache: only a successful query may change the entitlement, and a
            // failed check must never hide ads that were never paid for —
            // which the cache already reflects.
        }
    }

    private fun handlePurchaseList(purchases: List<Purchase>) {
        val removeAds = purchases.firstOrNull { RemoveAdsPolicy.PRODUCT_ID in it.products }
        if (removeAds == null) {
            // No purchase on record: never bought, refunded, or revoked after
            // reinstall — ads stay ON.
            setEntitled(false)
            return
        }
        val state = when (removeAds.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> RemoveAdsPolicy.VerifiedState.PURCHASED
            Purchase.PurchaseState.PENDING -> RemoveAdsPolicy.VerifiedState.PENDING
            else -> RemoveAdsPolicy.VerifiedState.UNSPECIFIED
        }
        if (!RemoveAdsPolicy.grantsEntitlement(removeAds.products, state)) {
            if (state == RemoveAdsPolicy.VerifiedState.PENDING) {
                emitMessage(context.getString(R.string.sys_remove_ads_pending))
            }
            setEntitled(false)
            return
        }
        setEntitled(true)
        if (RemoveAdsPolicy.needsAcknowledgement(
                entitled = true,
                isAcknowledged = removeAds.isAcknowledged
            )
        ) {
            acknowledge(removeAds)
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient?.acknowledgePurchase(params) {
            // Best-effort: the entitlement above was already granted from the
            // PURCHASED state, and the next successful query retries while
            // isAcknowledged stays false. Play refunds only after three days
            // without acknowledgement, so a transient failure self-heals.
        }
    }
}
