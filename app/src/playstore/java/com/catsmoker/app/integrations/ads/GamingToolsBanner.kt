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
import com.catsmoker.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Composable
internal fun GamingToolsBanner(modifier: Modifier = Modifier) =
    InlineBanner(BuildConfig.ADMOB_GAMING_TOOLS_BANNER_ID, modifier)

@Composable
internal fun AboutBanner(modifier: Modifier = Modifier) =
    InlineBanner(BuildConfig.ADMOB_ABOUT_BANNER_ID, modifier)

@Composable
private fun InlineBanner(adUnitId: String, modifier: Modifier) {
    if (LocalInspectionMode.current) return
    // Navigation resumes the destination after its transition, not while Home is still visible.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (lifecycleState != Lifecycle.State.RESUMED) return

    val viewModel: GamingToolsBannerViewModel = hiltViewModel()
    val isAdFree by viewModel.isAdFree.collectAsStateWithLifecycle()
    if (!isAdFree) {
        AdBanner(adUnitId = adUnitId, modifier = modifier, inline = true)
    }
}

@HiltViewModel
internal class GamingToolsBannerViewModel @Inject constructor(purchases: RemoveAdsRepository) : ViewModel() {
    val isAdFree = purchases.isAdFree
}
