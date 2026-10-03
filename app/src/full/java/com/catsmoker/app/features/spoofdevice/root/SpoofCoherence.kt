package com.catsmoker.app.features.spoofdevice.root

/**
 * Spoof-coherence derivations shared by the telephony, PackageManager and display hooks.
 *
 * The reference keys these off its own per-field toggles (`shouldExposeTelephony`,
 * `isTabletProfile`, per-field `isSpoofEnabled`), which this app does not have. Here the
 * profile's own `ro.build.characteristics` decides form factor — a profile that says
 * "tablet" while answering a phone's telephony (or vice versa) is the self-contradiction
 * these hooks exist to close — and every value hook otherwise passes the device's real
 * answer through when the profile carries nothing.
 */
object SpoofCoherence {

    /** Tablet iff the profile's build characteristics say so (the renderer's contract). */
    fun isTabletProfile(props: Map<String, String>): Boolean =
        props["ro.build.characteristics"]?.contains("tablet", ignoreCase = true) == true

    /**
     * Splits an MCC+MNC numeric (`310260` → MCC `310`, MNC `260`) for the `getMcc`/`getMnc`
     * family. Short/invalid input splits to nulls — never a fabricated 310/260 default.
     */
    fun splitMccMnc(numeric: String): Pair<String?, String?> {
        val digits = numeric.trim().filter { it.isDigit() }
        if (digits.length < 4) return null to null
        return digits.substring(0, 3) to digits.substring(3)
    }

    private val emulatorFeatures = setOf(
        "android.hardware.sensor.emulator",
        "goldfish"
    )

    private val telephonyFeatures = setOf(
        "android.hardware.telephony",
        "android.hardware.telephony.gsm",
        "android.hardware.telephony.cdma",
        "android.hardware.telephony.ims",
        "android.hardware.telephony.euicc",
        "android.hardware.telephony.mbms"
    )

    /**
     * PackageManager feature override: false denies, true exposes, null passes the real
     * answer through. Emulator tells are always denied; telephony follows the form factor
     * (denied on tablet builds, exposed on phone builds — the profile claims the form
     * factor, so the feature is claimed with it); the PC type is denied on tablet builds
     * only. Adapted from the reference `PackageManagerHooks.overrideFeature`.
     */
    fun overrideFeature(feature: String?, tablet: Boolean): Boolean? {
        if (feature == null) return null
        val normalized = feature.lowercase()
        for (denied in emulatorFeatures) {
            if (normalized.contains(denied)) return false
        }
        if (feature in telephonyFeatures) return !tablet
        if (tablet && feature == "android.hardware.type.pc") return false
        return null
    }
}
