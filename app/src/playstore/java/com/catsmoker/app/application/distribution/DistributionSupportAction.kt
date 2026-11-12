package com.catsmoker.app.application.distribution

import android.content.Context
import com.catsmoker.app.BuildConfig

internal object DistributionSupportAction {
    internal val link = SupportLink(
        url = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}",
        preferredPackage = "com.android.vending",
    )

    fun open(context: Context): Boolean = link.open(context)
}
