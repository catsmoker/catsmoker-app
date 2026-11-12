package com.catsmoker.app.application.distribution



/**
 * Full (GitHub) distribution capabilities.
 *
 * Mirrored by the playstore variant's [VariantCapabilities] with the same fully
 * qualified name so shared code in `src/main` compiles against both: the full
 * build includes spoofing and the GitHub self-updater; those implementations,
 * orchestration and UI are excluded from Play's source inputs, not just hidden.
 */
object VariantCapabilities {
    /** Device-spoofing UI + LSPosed/Magisk implementation is compiled in. */
    const val HAS_SPOOF = true

    /** GitHub-release self-updater (APK download) is compiled in. */
    const val HAS_SELF_UPDATE = true

    /**
     * The free Settings "Enable Ads" toggle is available: ads follow the
     * `ads_enabled` preference ([com.catsmoker.app.integrations.ads.AdManager]).
     * The Play variant sets this to false — there ads follow only the
     * verified `remove_ads` purchase.
     */
    const val HAS_FREE_ADS_TOGGLE = true

    /** Distribution fact; the store settings implementation is selected at build time. */
    const val HAS_PLAY_STORE = false

    /**
     * The full build declares `MANAGE_EXTERNAL_STORAGE` for the cleaner's direct
     * shared-storage walk. Google Play rejected that permission for this app as
     * non-core, so the Play variant must not declare it (see the Play twin).
     */
    const val HAS_ALL_FILES_ACCESS = true

    /**
     * The local VPN network block is compiled into Full only following this
     * app's Play rejection. VPN policy has eligible-use exceptions; that does
     * not establish eligibility for this app. Play retains shared Data Saver.
     */
    const val HAS_VPN_FIREWALL = true
}
