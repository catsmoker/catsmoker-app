package com.catsmoker.app.screens.settings

import android.content.Context
import com.catsmoker.app.application.distribution.DistributionSupportAction

/** Opens this app's Google Play Store listing so the user can rate it. Play variant only. */
fun openRateApp(context: Context) {
    DistributionSupportAction.open(context)
}
