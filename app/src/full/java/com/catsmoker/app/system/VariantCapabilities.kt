package com.catsmoker.app.system

/**
 * Full (GitHub) distribution capabilities.
 *
 * Mirrored by the playstore variant's [VariantCapabilities] with the same fully
 * qualified name so shared code in `src/main` compiles against both: the full
 * build keeps spoofing and the GitHub self-updater, the Play build hides them
 * and ships Play-safe replacements instead.
 */
object VariantCapabilities {
    /** Device-spoofing UI + LSPosed/Magisk implementation is compiled in. */
    const val HAS_SPOOF = true

    /** GitHub-release self-updater (APK download) is compiled in. */
    const val HAS_SELF_UPDATE = true

    /**
     * The free Settings "Enable Ads" toggle is available: ads follow the
     * `ads_enabled` preference ([AdManager]). The Play variant sets this to
     * false — there ads follow only the verified `remove_ads` purchase.
     */
    const val HAS_FREE_ADS_TOGGLE = true
}
