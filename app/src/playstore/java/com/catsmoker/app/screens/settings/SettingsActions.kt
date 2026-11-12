package com.catsmoker.app.screens.settings

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Opens this app's Google Play Store listing so the user can rate it. Play variant only. */
fun openRateApp(context: Context) {
    val intent = Intent(
        Intent.ACTION_VIEW,
        "market://details?id=${context.packageName}".toUri()
    )
    runCatching { context.startActivity(intent) }
}
