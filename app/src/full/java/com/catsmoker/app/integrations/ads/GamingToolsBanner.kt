package com.catsmoker.app.integrations.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Composable
internal fun GamingToolsBanner(modifier: Modifier = Modifier) = InlineBanner(modifier)

@Composable
internal fun AboutBanner(modifier: Modifier = Modifier) = InlineBanner(modifier)

@Composable
private fun InlineBanner(modifier: Modifier) {
    if (LocalInspectionMode.current) return
    // Wait until navigation finishes so this banner doesn't overlap Home's.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (lifecycleState != Lifecycle.State.RESUMED) return

    val viewModel: GamingToolsBannerViewModel = hiltViewModel()
    val adsEnabled by viewModel.adsEnabled.collectAsStateWithLifecycle()
    if (adsEnabled) AdBanner(modifier = modifier)
}

@HiltViewModel
internal class GamingToolsBannerViewModel @Inject constructor(adManager: AdManager) : ViewModel() {
    val adsEnabled = adManager.adsEnabled
}
