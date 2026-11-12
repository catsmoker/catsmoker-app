package com.catsmoker.app.integrations.ads

import android.app.Application

/**
 * Same-FQN deferred-startup adapter. Play initializes Mobile Ads only through
 * the Activity-owned consent flow; application startup is deliberately inert.
 */
object AdsInit {
    // Application startup cannot present a CMP. The Activity-owned privacy gate is
    // the only bootstrap; deferred startup must never initialize Mobile Ads itself.
    fun init(@Suppress("UNUSED_PARAMETER") app: Application) = Unit
}
