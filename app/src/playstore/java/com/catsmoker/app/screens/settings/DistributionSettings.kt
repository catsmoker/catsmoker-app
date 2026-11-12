package com.catsmoker.app.screens.settings

import com.catsmoker.app.R

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.integrations.ads.RemoveAdsRepository
import com.catsmoker.app.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Play owns updates; no updater implementation, parser, state or preference reader is compiled. */
@Composable
internal fun rememberAvailableUpdateName(): String? = null

@Composable
internal fun DistributionUpdateSettings() = Unit

@HiltViewModel
class PlayAdsSettingsViewModel @Inject constructor(val purchases: RemoveAdsRepository) : ViewModel()

@Composable
internal fun DistributionAdsSettings(@Suppress("UNUSED_PARAMETER") onOpenDonate: () -> Unit) {
    val viewModel: PlayAdsSettingsViewModel = hiltViewModel()
    val purchases = viewModel.purchases
    val adFree by purchases.isAdFree.collectAsStateWithLifecycle()
    val price by purchases.displayPrice.collectAsStateWithLifecycle()
    val working by purchases.isWorking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { purchases.refresh() }
    LaunchedEffect(purchases) {
        purchases.messages.collect { android.widget.Toast.makeText(
            context, it, android.widget.Toast.LENGTH_SHORT
        ).show() }
    }
    if (adFree) AdFreeCard() else SettingsRowCard(
        icon = Icons.Default.WorkspacePremium,
        title = stringResource(R.string.sys_remove_ads_title),
        summary = price ?: stringResource(R.string.sys_remove_ads_summary),
        onClick = { (context as? Activity)?.let(purchases::launchPurchase) },
        trailing = if (working && price == null) {
            { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
        } else null
    )
    val privacyRequired by com.catsmoker.app.integrations.ads.AdConsentManager.privacyRequired.collectAsStateWithLifecycle()
    if (privacyRequired) SettingsRowCard(icon = Icons.Default.Check,
        title = stringResource(R.string.policy_ad_privacy),
        summary = stringResource(R.string.policy_ad_privacy_summary),
        onClick = { (context as? Activity)?.let(com.catsmoker.app.integrations.ads.AdConsentManager::showPrivacyOptions) })
}

@Composable
internal fun DistributionStoreSettings() {
    val context = LocalContext.current
    SettingsRowCard(icon = Icons.Default.Star,
        title = stringResource(R.string.sys_rate_app_title),
        summary = stringResource(R.string.sys_rate_app_summary),
        onClick = { openRateApp(context) })
}

/** Confirms a recorded purchase, rather than presenting an informational-only card. */
@Composable
private fun AdFreeCard() {
    SectionCard(contentPadding = 0.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsIconBadge(Icons.Default.Check, MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sys_ads_removed_title), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.sys_ads_removed_sub), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp))
        }
    }
}
