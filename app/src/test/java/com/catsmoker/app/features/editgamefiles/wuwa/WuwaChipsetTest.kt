package com.catsmoker.app.features.editgamefiles.wuwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for the on-device chipset read.
 *
 * Ported from the reference WuWa app's `ChipsetDetector.detect` (read in full): the same
 * Build-field reads and the same family checks. The result is an unscored note for the
 * recommendation card — a hint about what the silicon suggests, weighed at zero — because
 * the GPU tier the SmartBrain scores comes from the log's measured GL_RENDERER string, and
 * a guess must never outrank a measurement.
 */
class WuwaChipsetTest {

    @Test
    fun snapdragonIsRecognized() {
        val info = WuwaChipset.detect(soc = "sun", board = "kalama", manufacturer = "Xiaomi")
        assertTrue(info.isSnapdragon)
        assertEquals("SUN", info.socName)
    }

    @Test
    fun mediatekIsRecognized() {
        val info = WuwaChipset.detect(soc = "mt6896", board = "unknown", manufacturer = "vivo")
        assertTrue(info.isMediatek)
    }

    @Test
    fun exynosIsRecognized() {
        val info = WuwaChipset.detect(soc = "exynos2200", board = "exynos", manufacturer = "samsung")
        assertTrue(info.isExynos)
    }

    @Test
    fun tensorIsRecognized() {
        val info = WuwaChipset.detect(soc = "gs201", board = "gscaler", manufacturer = "Google")
        assertTrue(info.isTensor)
    }

    @Test
    fun unknownStaysUnknown() {
        val info = WuwaChipset.detect(soc = "mystery", board = "mystery", manufacturer = "mystery")
        assertEquals(false, info.isSnapdragon)
        assertEquals(false, info.isMediatek)
        assertEquals(false, info.isExynos)
        assertEquals(false, info.isTensor)
        assertEquals("MYSTERY", info.socName)
    }

    @Test
    fun familySummarizesForDisplay() {
        assertEquals(
            "Snapdragon",
            WuwaChipset.detect(soc = "sm8650", board = "pineapple", manufacturer = "oneplus").family
        )
        assertEquals(
            "MediaTek",
            WuwaChipset.detect(soc = "mt", board = "x", manufacturer = "mediatek").family
        )
        assertEquals(
            "Unknown",
            WuwaChipset.detect(soc = "?", board = "?", manufacturer = "?").family
        )
    }
}
