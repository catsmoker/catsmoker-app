package com.catsmoker.app.system.ads

import android.content.Context
import androidx.work.*
import com.android.billingclient.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Durable acknowledgement reconciliation, without storing tokens in WorkManager or logs.
 * Each attempt queries Play again; pending/refunded/already acknowledged purchases are not
 * acknowledged blindly. Ordinary work can be delayed by offline/force-stop/system limits,
 * so this reduces failures but cannot guarantee the three-day deadline on every device.
 */
class BillingAcknowledgementWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        val client = BillingClient.newBuilder(applicationContext)
            .setListener { _, _ -> }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        try {
            withTimeoutOrNull(45_000L) {
                val setup = suspendCancellableCoroutine<Int> { continuation ->
                    val answered = AtomicBoolean(false)
                    client.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (answered.compareAndSet(false, true) && continuation.isActive) {
                                continuation.resume(result.responseCode)
                            }
                        }
                        override fun onBillingServiceDisconnected() {
                            if (answered.compareAndSet(false, true) && continuation.isActive) {
                                continuation.resume(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                            }
                        }
                    })
                }
                if (setup != BillingClient.BillingResponseCode.OK) return@withTimeoutOrNull outcome(setup)
                val (queryCode, purchases) = suspendCancellableCoroutine<Pair<Int, List<Purchase>>> { continuation ->
                    client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP).build()) { result, found ->
                        if (continuation.isActive) continuation.resume(result.responseCode to found)
                    }
                }
                if (queryCode != BillingClient.BillingResponseCode.OK) return@withTimeoutOrNull outcome(queryCode)
                var retry = false
                var failed = false
                for (purchase in purchases) {
                    if (RemoveAdsPolicy.PRODUCT_ID !in purchase.products || purchase.isAcknowledged ||
                        purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
                    val code = suspendCancellableCoroutine<Int> { continuation ->
                        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken).build()) { result ->
                            if (continuation.isActive) continuation.resume(result.responseCode)
                        }
                    }
                    retry = retry || BillingAcknowledgementPolicy.shouldRetry(code)
                    failed = failed || (code != BillingClient.BillingResponseCode.OK &&
                        !BillingAcknowledgementPolicy.shouldRetry(code))
                }
                when {
                    retry -> Result.retry()
                    failed -> Result.failure()
                    else -> Result.success()
                }
            } ?: Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        } finally {
            client.endConnection()
        }
    }

    private fun outcome(code: Int) = if (BillingAcknowledgementPolicy.shouldRetry(code)) Result.retry() else Result.failure()

    companion object {
        fun enqueue(context: Context, token: String) {
            val key = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val request = OneTimeWorkRequestBuilder<BillingAcknowledgementWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("billing_ack_$key", ExistingWorkPolicy.KEEP, request)
        }
    }
}
