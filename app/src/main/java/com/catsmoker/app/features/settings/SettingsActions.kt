package com.catsmoker.app.features.settings

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens this app's Google Play Store listing so the user can rate it. Play variant only. */
fun openRateApp(context: Context) {
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=${context.packageName}")
    )
    runCatching { context.startActivity(intent) }
}
