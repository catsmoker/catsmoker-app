package com.catsmoker.app.shared.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.catsmoker.app.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Play-store branch banner (AdMob — see PLAYSTORE.md).
 *
 * The slot is reserved up front so a successful ad never shifts content, but it
 * collapses when no ad unit is configured or no ad arrives (offline, no fill),
 * or the gap would stay forever. Callers additionally gate on `ads_enabled`.
 */
@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    val adUnitId = BuildConfig.ADMOB_BANNER_ID
    if (adUnitId.isEmpty()) return

    var loadFailed by rememberSaveable { mutableStateOf(false) }
    if (loadFailed) return

    val context = LocalContext.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt()
        // Anchored adaptive size for the current width — the Play-recommended banner sizing.
        val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)

        key(widthDp, adUnitId) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(adSize.height.dp),
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(adSize)
                        setAdUnitId(adUnitId)
                        adListener = object : AdListener() {
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                loadFailed = true
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { it.destroy() }
            )
        }
    }
}
