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
     * `ads_enabled` preference ([com.catsmoker.app.system.ads.AdManager]).
     * The Play variant sets this to false — there ads follow only the
     * verified `remove_ads` purchase.
     */
    const val HAS_FREE_ADS_TOGGLE = true

    /** Google Play Store is available — used to gate Play-only entries like "Rate App". */
    const val HAS_PLAY_STORE = false

    /**
     * The full build declares `MANAGE_EXTERNAL_STORAGE` for the cleaner's direct
     * shared-storage walk. Google Play rejected that permission for this app as
     * non-core, so the Play variant must not declare it (see the Play twin).
     */
    const val HAS_ALL_FILES_ACCESS = true

    /**
     * The local VPN network block is compiled into the full build only. Play's
     * VpnService policy requires the VPN to be core functionality, which a gaming
     * optimizer is not; the Play variant keeps the Data Saver half instead.
     */
    const val HAS_VPN_FIREWALL = true
}
