package com.catsmoker.app.system

/**
 * Play Store distribution capabilities.
 *
 * Same fully qualified name as the full variant's [VariantCapabilities]:
 * spoofing and the GitHub self-updater are NOT compiled into this variant,
 * so shared code must consult these flags instead of referencing the
 * implementations directly.
 */
object VariantCapabilities {
    /** Device-spoofing UI + LSPosed/Magisk implementation is absent. */
    const val HAS_SPOOF = false

    /** GitHub-release self-updater is absent (Play policy). */
    const val HAS_SELF_UPDATE = false

    /**
     * No free Settings "Enable Ads" toggle on this variant: ads follow only
     * the verified `remove_ads` purchase ([com.catsmoker.app.system.ads.AdManager]
     * ignores `ads_enabled`), so the toggle must stay hidden and unwired here.
     */
    const val HAS_FREE_ADS_TOGGLE = false

    /** Google Play Store is available — used to gate Play-only entries like "Rate App". */
    const val HAS_PLAY_STORE = true

    /**
     * Play policy rejected All files access as non-core for this app, so this
     * variant does not declare `MANAGE_EXTERNAL_STORAGE`. File work goes through
     * SAF/MediaStore, and the cleaner still scans `Android/data` through
     * root/Shizuku when a privileged channel exists.
     */
    const val HAS_ALL_FILES_ACCESS = false

    /**
     * Play's VpnService policy requires the VPN to be core functionality. This
     * variant therefore compiles the `VpnFirewall` stub instead of the real
     * service; the Data Saver half of network blocking remains available.
     */
    const val HAS_VPN_FIREWALL = false
}
