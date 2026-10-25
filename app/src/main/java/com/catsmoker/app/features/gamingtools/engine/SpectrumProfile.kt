package com.catsmoker.app.features.gamingtools.engine

/**
 * frap129's Spectrum kernel interface as SmartPack Kernel Manager exposes it
 * (`org/frap129/spectrum/Spectrum.java`, read before this file was written).
 *
 * Spectrum is a capability the kernel declares, not something this app can add: the boot image
 * must ship the interface and advertise it through `spectrum.support` or
 * `vendor.spectrum.support`. When it does, a single persistent property asks the kernel's own
 * profile machinery to retune itself. The profile vocabulary is 0 = battery, 1 = balanced,
 * 2 = performance, 3 = gaming.
 *
 * The rule is raise-only: a battery/balanced/performance profile is raised to gaming (3), a
 * kernel that clamps 3 back to 2 is accepted as a confirmed performance profile, and anything
 * already at 3 — or outside the 0..3 vocabulary — is left alone. The prior value is recorded
 * before the write and restored on deactivation; a property that did not exist cannot be deleted
 * with `setprop`, so that one bounded leftover is stated rather than hidden (the same rule as the
 * Qualcomm FPS hint). Behavior lives in [GamingEngine].
 */
object SpectrumProfile {

    /** The plain Spectrum support property. */
    const val SUPPORT_PROP = "spectrum.support"

    /** The vendor-namespaced support property some kernels ship instead. */
    const val VENDOR_SUPPORT_PROP = "vendor.spectrum.support"

    /** The plain profile property. */
    const val PROFILE_PROP = "persist.spectrum.profile"

    /** The vendor-namespaced profile property, selected by [VENDOR_SUPPORT_PROP]. */
    const val VENDOR_PROFILE_PROP = "persist.vendor.spectrum.profile"

    /** Spectrum's gaming profile. */
    const val GAMING_PROFILE = 3

    /** Spectrum's performance profile — the fallback a kernel may clamp gaming to. */
    const val PERFORMANCE_PROFILE = 2

    /**
     * Whether the device declares Spectrum support. Either property answering `1` is enough; a
     * different value is a real "no", and an unreadable one contributes nothing.
     */
    fun supported(spectrumSupport: String?, vendorSupport: String?): Boolean =
        spectrumSupport?.trim() == "1" || vendorSupport?.trim() == "1"

    /** The property this device's interface uses. */
    fun profileProperty(vendorSupport: Boolean): String =
        if (vendorSupport) VENDOR_PROFILE_PROP else PROFILE_PROP

    /**
     * The change-needed target: [GAMING_PROFILE] for a readable profile below it, else null.
     *
     * A profile of 3 is already the maximum this vocabulary defines. A value above 3 or below 0
     * has no meaning here, and an unreadable/blank value could belong to a different interface
     * version — both mean "do not write", never a guessed profile.
     */
    fun requestedProfile(current: String?): Int? {
        val value = current?.trim()?.toIntOrNull() ?: return null
        if (value !in 0..PERFORMANCE_PROFILE) return null
        return GAMING_PROFILE
    }

    /**
     * The profile a write actually confirmed: [requested] itself, or [PERFORMANCE_PROFILE] when
     * the kernel clamped a gaming request to performance. Anything lower is not a success — the
     * user asked for the strongest profile the interface defines, and a battery/balanced result
     * would be a change in the wrong direction.
     */
    fun acceptedProfile(requested: Int, actual: String?): Int? {
        val value = actual?.trim()?.toIntOrNull() ?: return null
        if (value == requested) return value
        if (requested == GAMING_PROFILE && value == PERFORMANCE_PROFILE) return value
        return null
    }
}
