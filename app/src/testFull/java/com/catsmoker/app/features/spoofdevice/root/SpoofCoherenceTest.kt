package com.catsmoker.app.features.spoofdevice.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the spoof-coherence helpers: tablet form-factor derivation (the single signal every
 * coherence hook reads — telephony hiding, feature gating), MCC/MNC parsing from the
 * profile's operator numeric, and the PackageManager feature override.
 *
 * Adapted from `reference/spoofdevice/example-1` (`TelephonyHooks`, `PackageManagerHooks`,
 * read in full): the reference keys these off its own per-field toggles, which this app
 * does not have — here the profile's own `ro.build.characteristics` decides, so a tablet
 * profile can never answer a phone's telephony and a phone profile never hides its own.
 */
class SpoofCoherenceTest {

    @Test
    fun tabletDerivesFromBuildCharacteristics() {
        assertTrue(SpoofCoherence.isTabletProfile(mapOf("ro.build.characteristics" to "tablet")))
        assertTrue(SpoofCoherence.isTabletProfile(mapOf("ro.build.characteristics" to "Tablet")))
        assertFalse(SpoofCoherence.isTabletProfile(mapOf("ro.build.characteristics" to "nosdcard")))
        assertFalse(SpoofCoherence.isTabletProfile(emptyMap()))
    }

    @Test
    fun mccMncSplitOperatorNumeric() {
        assertEquals("310" to "260", SpoofCoherence.splitMccMnc("310260"))
        // MNC is the remainder after the 3-digit MCC (2-3 digits in practice).
        assertEquals("724" to "31", SpoofCoherence.splitMccMnc("72431"))
        assertEquals(null to null, SpoofCoherence.splitMccMnc(""))
        assertEquals(null to null, SpoofCoherence.splitMccMnc("31"))
    }

    @Test
    fun emulatorFeaturesAreAlwaysDenied() {
        assertEquals(false, SpoofCoherence.overrideFeature("android.hardware.sensor.emulator", tablet = false))
        assertEquals(false, SpoofCoherence.overrideFeature("goldfish_foo", tablet = true))
    }

    @Test
    fun telephonyFeaturesFollowFormFactor() {
        assertEquals(false, SpoofCoherence.overrideFeature("android.hardware.telephony", tablet = true))
        assertEquals(false, SpoofCoherence.overrideFeature("android.hardware.telephony.gsm", tablet = true))
        // Phone profiles explicitly expose telephony (the reference answers true, not
        // pass-through — the profile claims a phone, so the feature is claimed too).
        assertEquals(true, SpoofCoherence.overrideFeature("android.hardware.telephony", tablet = false))
        assertEquals(true, SpoofCoherence.overrideFeature("android.hardware.telephony.gsm", tablet = false))
    }

    @Test
    fun pcTypeDeniedOnTabletOnly() {
        assertEquals(false, SpoofCoherence.overrideFeature("android.hardware.type.pc", tablet = true))
        assertNull(SpoofCoherence.overrideFeature("android.hardware.type.pc", tablet = false))
    }

    @Test
    fun unrelatedFeaturesPassThrough() {
        assertNull(SpoofCoherence.overrideFeature("android.hardware.camera", tablet = true))
        assertNull(SpoofCoherence.overrideFeature("android.hardware.camera", tablet = false))
        assertNull(SpoofCoherence.overrideFeature(null, tablet = true))
    }
}
