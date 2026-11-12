package com.catsmoker.app.integrations.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fail closed until this launch's CMP update/form finishes and SDK initialization completes. */
object AdConsentManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _canLoad = MutableStateFlow(false)
    val canLoad = _canLoad.asStateFlow()
    private val _privacyRequired = MutableStateFlow(false)
    val privacyRequired = _privacyRequired.asStateFlow()
    private var initialized = false
    private var initializing = false
    private var updated = false
    private var formOpen = false
    private var requestGeneration = 0

    fun gather(activity: Activity) {
        val generation = ++requestGeneration
        updated = false
        formOpen = false
        _canLoad.value = false
        try {
            val info = UserMessagingPlatform.getConsentInformation(activity)
            info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
                if (generation != requestGeneration || activity.isFinishing || activity.isDestroyed) return@requestConsentInfoUpdate
                try {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                        if (generation == requestGeneration && !activity.isFinishing && !activity.isDestroyed) {
                            updated = error == null
                            refresh(activity)
                        }
                    }
                } catch (_: Exception) {
                    if (generation == requestGeneration) { updated = false; _canLoad.value = false }
                }
            }, { _ ->
                // Do not assume an old consent record is current when this update failed.
                if (generation == requestGeneration) { updated = false; refresh(activity) }
            })
        } catch (_: Exception) { updated = false; _canLoad.value = false }
    }

    fun showPrivacyOptions(activity: Activity) {
        if (formOpen || activity.isFinishing || activity.isDestroyed) return
        val generation = ++requestGeneration
        formOpen = true
        _canLoad.value = false // Dispose banners before changing or withdrawing a choice.
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                if (generation == requestGeneration) {
                    formOpen = false
                    updated = error == null && !activity.isFinishing && !activity.isDestroyed
                    refresh(activity)
                }
            }
        } catch (_: Exception) { formOpen = false; updated = false; _canLoad.value = false }
    }

    fun refresh(context: Context) {
        try {
            val info = UserMessagingPlatform.getConsentInformation(context)
            _privacyRequired.value = info.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val eligible = AdConsentPolicy.canLoad(!prefs.getBoolean("is_first_run", true),
                updated, info.canRequestAds(), true,
                prefs.getBoolean(RemoveAdsPolicy.ENTITLED_PREF_KEY, false)) && !formOpen
            if (eligible && !initialized && !initializing) {
                initializing = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            MobileAds.initialize(context.applicationContext) {
                                scope.launch { initialized = true; initializing = false; refresh(context) }
                            }
                        }
                    } catch (_: Exception) { initializing = false; _canLoad.value = false }
                }
            }
            _canLoad.value = eligible && initialized
        } catch (_: Exception) { _canLoad.value = false }
    }
}
