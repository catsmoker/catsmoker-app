package com.catsmoker.app.shared.util

import android.os.Build

/**
 * True on vivo and iQOO builds, which several gaming tweaks special-case.
 *
 * Delegates to [DeviceCapabilities] — the single source for vendor detection —
 * with the rule this function always used: either `MANUFACTURER` or `BRAND` can
 * carry the vendor name while the other carries something else. Kept as a
 * top-level function (rather than forcing callers onto the detector) because it
 * reads nothing but [Build], so requiring anything more would be a dependency
 * that buys nothing. It used to sit on the class that is now
 * `DisplayRefreshRateProvider`, beside the refresh-rate reads — one vendor check
 * next to two display reads is what forced that class to carry a name as vague
 * as "diagnostic manager".
 */
fun isVivoOrIqoo(): Boolean {
    val caps = DeviceCapabilities.detect(
        DeviceCapabilities.DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND
        )
    )
    return caps.isVivo
}
