package com.catsmoker.app.application.distribution



/**
 * Play Store distribution capabilities.
 *
 * Same fully qualified name as the full variant's [VariantCapabilities]:
 * spoofing and the GitHub self-updater are NOT compiled into this variant,
 * along with their orchestration and UI. Selected adapters, not visibility
 * flags, provide the build-time boundary; common capability checks are facts.
 */
object VariantCapabilities {
    /** Device-spoofing UI + LSPosed/Magisk implementation is absent. */
    const val HAS_SPOOF = false

    /** GitHub-release self-updater is absent (Play policy). */
    const val HAS_SELF_UPDATE = false

    /**
     * No free Settings "Enable Ads" toggle on this variant: ads follow only
     * the verified `remove_ads` purchase ([com.catsmoker.app.integrations.ads.AdManager]
     * ignores `ads_enabled`). The free-toggle implementation is Full-only.
     */
    const val HAS_FREE_ADS_TOGGLE = false

    /** Distribution fact; store settings are compiled from this flavor. */
    const val HAS_PLAY_STORE = true

    /**
     * Play policy rejected All files access as non-core for this app, so this
     * variant does not declare `MANAGE_EXTERNAL_STORAGE`. File work goes through
     * SAF/MediaStore, and the cleaner still scans `Android/data` through
     * root/Shizuku when a privileged channel exists.
     */
    const val HAS_ALL_FILES_ACCESS = false

    /**
     * This app's rejected VPN implementation, consent UI and service are not
     * compiled here; there is no VpnFirewall stub. Shared Data Saver remains.
     * VPN policy's eligible-use exceptions do not establish this app's approval.
     */
    const val HAS_VPN_FIREWALL = false
}
