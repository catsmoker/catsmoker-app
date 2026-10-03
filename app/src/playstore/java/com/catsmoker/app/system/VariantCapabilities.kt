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
}
