package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SpectrumProfile], the capability-gated hook into frap129's Spectrum kernel
 * interface (via SmartPack's `org.frap129.spectrum.Spectrum`).
 *
 * The profile property is only touched when the device itself says Spectrum is supported, and the
 * target is the highest profile the interface defines (3 = gaming, 2 = performance). A kernel that
 * clamps 3 to 2 is accepted as a confirmed performance profile rather than reported as a lie.
 */
class SpectrumProfileTest {

    @Test
    fun `support is declared by either the plain or the vendor property`() {
        assertTrue(SpectrumProfile.supported("1", null))
        assertTrue(SpectrumProfile.supported(null, "1"))
        assertTrue(SpectrumProfile.supported("1", "1"))
        assertFalse(SpectrumProfile.supported("0", "0"))
        assertFalse(SpectrumProfile.supported(null, null))
        assertFalse(SpectrumProfile.supported("", "2"))
    }

    @Test
    fun `vendor support selects the vendor profile property`() {
        assertEquals(SpectrumProfile.VENDOR_PROFILE_PROP, SpectrumProfile.profileProperty(true))
        assertEquals(SpectrumProfile.PROFILE_PROP, SpectrumProfile.profileProperty(false))
    }

    @Test
    fun `a battery or balanced profile is raised to gaming`() {
        assertEquals(3, SpectrumProfile.requestedProfile("0"))
        assertEquals(3, SpectrumProfile.requestedProfile("1"))
    }

    @Test
    fun `a performance profile is raised to gaming when the interface allows it`() {
        assertEquals(3, SpectrumProfile.requestedProfile("2"))
    }

    @Test
    fun `an already-gaming profile writes nothing`() {
        assertNull(SpectrumProfile.requestedProfile("3"))
    }

    @Test
    fun `an unreadable or out-of-range profile writes nothing`() {
        // 4 has no meaning in Spectrum's 0..3 vocabulary; negative is not a profile either.
        assertNull(SpectrumProfile.requestedProfile(null))
        assertNull(SpectrumProfile.requestedProfile(""))
        assertNull(SpectrumProfile.requestedProfile("abc"))
        assertNull(SpectrumProfile.requestedProfile("4"))
        assertNull(SpectrumProfile.requestedProfile("-1"))
    }

    @Test
    fun `a kernel that clamps gaming to performance is confirmed at performance`() {
        assertEquals(3, SpectrumProfile.acceptedProfile(3, "3"))
        assertEquals(2, SpectrumProfile.acceptedProfile(3, "2"))
        assertEquals(2, SpectrumProfile.acceptedProfile(2, "2"))
    }

    @Test
    fun `a write that leaves a lower profile is not a success`() {
        assertNull(SpectrumProfile.acceptedProfile(3, "1"))
        assertNull(SpectrumProfile.acceptedProfile(3, "0"))
        assertNull(SpectrumProfile.acceptedProfile(2, "1"))
        assertNull(SpectrumProfile.acceptedProfile(2, null))
        assertNull(SpectrumProfile.acceptedProfile(3, "abc"))
    }
}
