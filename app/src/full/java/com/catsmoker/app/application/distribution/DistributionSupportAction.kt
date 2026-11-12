package com.catsmoker.app.application.distribution

import android.content.Context
import com.catsmoker.app.R

internal object DistributionSupportAction {
    fun open(context: Context): Boolean =
        SupportLink(context.getString(R.string.url_github)).open(context)
}
