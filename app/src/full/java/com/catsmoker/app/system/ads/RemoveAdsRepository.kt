package com.catsmoker.app.system.ads

import android.app.Activity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full-variant ad-removal gateway: paid removal does not exist here — ads
 * follow the free `ads_enabled` preference ([AdManager]) and need no
 * purchase. This stub keeps the same FQN, constructor shape and public API
 * as the playstore variant's Play Billing implementation so shared callers
 * ([SettingsViewModel]) compile against both; every member is an inert
 * default.
 */
@Singleton
class RemoveAdsRepository @Inject constructor() {

    private val _isAdFree = MutableStateFlow(false)
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    private val _displayPrice = MutableStateFlow<String?>(null)
    val displayPrice: StateFlow<String?> = _displayPrice.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Re-queries Play for the entitlement. No-op here: nothing to restore. */
    fun refresh() {
        // No-op: the full variant has no purchasable ad removal.
    }

    /** Launches the Play purchase flow. No-op here: nothing to sell. */
    fun launchPurchase(activity: Activity) {
        // No-op: the full variant keeps the free Settings toggle instead.
    }
}
