package com.catsmoker.app.features.gamingtools.tools.glyph

import com.nothing.ketchum.Glyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `Build.MODEL` → GDK device table.
 *
 * The mapping is the one thing in the Glyph feature that can silently pick the wrong hardware:
 * `displayProgress` validates the channel against the registered device, so a wrong id would
 * either be refused (no bar) or fill a segment on a different model. These cases pin the table
 * to the GDK's own constants, so a future SDK bump that renames a device fails here instead of
 * on a phone.
 */
class GlyphHardwareTest {

    @Test
    fun phoneOneUsesTheD1Channel() {
        assertEquals(
            GlyphHardware.Strip(Glyph.DEVICE_20111, Glyph.Code_20111.D1_1),
            GlyphHardware.forModel("A063")
        )
    }

    @Test
    fun phoneTwoFractionsShareOneRegistration() {
        val expected = GlyphHardware.Strip(Glyph.DEVICE_22111, Glyph.Code_22111.C1_1)
        assertEquals(expected, GlyphHardware.forModel("A065"))
        // AIN065 is the India build; the GDK registers it as the same DEVICE_22111.
        assertEquals(expected, GlyphHardware.forModel("AIN065"))
    }

    @Test
    fun phoneTwoAUsesTheCChannel() {
        assertEquals(
            GlyphHardware.Strip(Glyph.DEVICE_23111, Glyph.Code_23111.C_1),
            GlyphHardware.forModel("A142")
        )
    }

    @Test
    fun phoneTwoAPlusRegistersItsOwnIdOnTheTwoAArray() {
        // The GDK's is23113() accepts only DEVICE_23113, so registering the 2a's id would be
        // refused; displayProgress itself has no 23113 branch, hence the lit-segment fallback.
        assertEquals(
            GlyphHardware.Strip(Glyph.DEVICE_23113, Glyph.Code_23111.C_1, supportsProgress = false),
            GlyphHardware.forModel("A142P")
        )
    }

    @Test
    fun phoneThreeAVariantsResolveThroughTheSuffix() {
        // Build.MODEL carries a variant suffix on some units, which is why the GDK's own check is
        // a contains(); the resolver mirrors that instead of demanding the bare code.
        assertEquals(
            GlyphHardware.Strip(Glyph.DEVICE_24111, Glyph.Code_24111.C_1),
            GlyphHardware.forModel("A059")
        )
        assertEquals(
            GlyphHardware.Strip(Glyph.DEVICE_24111, Glyph.Code_24111.C_1),
            GlyphHardware.forModel("A059P")
        )
    }

    @Test
    fun phoneThreeIsAMatrixDevice() {
        val hardware = GlyphHardware.forModel("A024")
        assertEquals(
            GlyphHardware.Matrix(Glyph.DEVICE_23112, Glyph.DEVICE_23112_MATRIX_LENGTH),
            hardware
        )
        // The matrix edge is what the frame builder lays text into; a wrong one crops the digits.
        assertEquals(25, (hardware as GlyphHardware.Matrix).size)
    }

    @Test
    fun phoneFourAAndItsProUseDifferentHardware() {
        assertEquals(
            GlyphHardware.Strip(Glyph.DEVICE_25111, Glyph.Code_25111.A_1),
            GlyphHardware.forModel("A069")
        )
        val pro = GlyphHardware.forModel("A069P")
        assertEquals(
            GlyphHardware.Matrix(Glyph.DEVICE_25111p, Glyph.DEVICE_25111p_MATRIX_LENGTH),
            pro
        )
        assertEquals(13, (pro as GlyphHardware.Matrix).size)
    }

    @Test
    fun phoneFourBResolvesThroughTheSuffix() {
        assertEquals(
            GlyphHardware.Strip(Glyph.DEVICE_25131, Glyph.Code_25131.A_1),
            GlyphHardware.forModel("A009P")
        )
        assertEquals(
            GlyphHardware.Strip(Glyph.DEVICE_25131, Glyph.Code_25131.A_1),
            GlyphHardware.forModel("A009PX")
        )
    }

    @Test
    fun unknownAndEmptyModelsResolveToNothing() {
        // Everything the app is not allowed to drive: other OEMs, a Nothing model newer than the
        // bundled GDK, and the no-model case. Null must stay the answer — it is what makes every
        // controller call a no-op.
        assertNull(GlyphHardware.forModel(null))
        assertNull(GlyphHardware.forModel(""))
        assertNull(GlyphHardware.forModel("Pixel 9 Pro"))
        assertNull(GlyphHardware.forModel("SM-S928B"))
        assertNull(GlyphHardware.forModel("A999"))
    }

    @Test
    fun everySupportedModelHasAUsableRegistration() {
        val models = listOf("A063", "A065", "AIN065", "A142", "A142P", "A059", "A024", "A069", "A069P", "A009P")
        models.forEach { model ->
            val hardware = GlyphHardware.forModel(model)
            assertTrue("$model did not resolve", hardware != null)
            assertTrue("$model has a blank device id", hardware!!.deviceId.isNotBlank())
        }
    }
}
