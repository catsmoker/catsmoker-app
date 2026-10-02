package com.catsmoker.app.features.editgamefiles.hsr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the HSR one-tap graphics presets and the pending-change diff against
 * `reference/gamingtools/hsrgraphicdroid-main/.../ui/viewmodel/GraphicsViewModel.kt`
 * (`applyPreset`, read in full before this test was written): the five tiers' exact values,
 * `graphicsQuality = 0` (Custom, so the per-slider values survive the game's own preset
 * system), and the pending/modified-fields accounting behind the dirty dots.
 *
 * Deliberate divergence: preset names stay code-English like the existing `qualityName()`
 * family, not string resources — the sliders already name every step in English in all
 * locales, so translated preset names would be the inconsistent ones.
 */
class HsrGraphicsPresetTest {

    @Test
    fun presetNamesAreTheReferenceFive() {
        assertEquals(listOf("Low", "Medium", "High", "Ultra", "Max"), HsrGraphicsSettings.GRAPHICS_PRESET_NAMES)
    }

    @Test
    fun lowPresetMatchesTheReference() {
        val out = HsrGraphicsSettings().withGraphicsPreset(0)
        assertEquals(0, out.graphicsQuality)
        assertEquals(30, out.fps)
        assertEquals(true, out.enableVSync)
        assertEquals(0.6, out.renderScale, 1e-9)
        assertEquals(0, out.resolutionQuality)
        assertEquals(0, out.shadowQuality)
        assertEquals(0, out.lightQuality)
        assertEquals(0, out.characterQuality)
        assertEquals(0, out.envDetailQuality)
        assertEquals(0, out.reflectionQuality)
        assertEquals(1, out.sfxQuality)
        assertEquals(0, out.bloomQuality)
        assertEquals(0, out.aaMode)
        assertEquals(0, out.enableSelfShadow)
        assertEquals(0, out.dlssQuality)
        assertEquals(0, out.particleTrailSmoothness)
        assertEquals(false, out.enableMetalFXSU)
        assertEquals(false, out.enableHalfResTransparent)
    }

    @Test
    fun maxPresetMatchesTheReference() {
        val out = HsrGraphicsSettings().withGraphicsPreset(4)
        assertEquals(0, out.graphicsQuality)
        assertEquals(120, out.fps)
        assertEquals(false, out.enableVSync)
        assertEquals(2.0, out.renderScale, 1e-9)
        assertEquals(5, out.resolutionQuality)
        assertEquals(5, out.shadowQuality)
        assertEquals(5, out.sfxQuality)
        assertEquals(1, out.aaMode)
        assertEquals(2, out.enableSelfShadow)
        assertEquals(true, out.enableMetalFXSU)
        assertEquals(1, out.dlssQuality)
        assertEquals(3, out.particleTrailSmoothness)
    }

    @Test
    fun middlePresetsMatchTheReference() {
        val medium = HsrGraphicsSettings().withGraphicsPreset(1)
        assertEquals(60, medium.fps)
        assertEquals(0.8, medium.renderScale, 1e-9)
        assertEquals(1, medium.resolutionQuality)
        assertEquals(2, medium.sfxQuality)
        assertEquals(1, medium.aaMode)
        assertEquals(1, medium.particleTrailSmoothness)

        val high = HsrGraphicsSettings().withGraphicsPreset(2)
        assertEquals(60, high.fps)
        assertEquals(1.0, high.renderScale, 1e-9)
        assertEquals(2, high.resolutionQuality)
        assertEquals(1, high.enableSelfShadow)
        assertEquals(1, high.dlssQuality)

        val ultra = HsrGraphicsSettings().withGraphicsPreset(3)
        assertEquals(120, ultra.fps)
        assertEquals(false, ultra.enableVSync)
        assertEquals(1.2, ultra.renderScale, 1e-9)
        assertEquals(3, ultra.resolutionQuality)
        assertEquals(4, ultra.sfxQuality)
        assertEquals(2, ultra.enableSelfShadow)
        assertEquals(3, ultra.particleTrailSmoothness)
    }

    @Test
    fun presetPreservesResolutionAndSiblings() {
        // A preset retunes quality, never the display: resolution siblings and the PSO
        // warmup flag ride through untouched.
        val base = HsrGraphicsSettings(screenWidth = 2560, screenHeight = 1440, enablePsoShaderWarmup = false)
        val out = base.withGraphicsPreset(4)
        assertEquals(2560, out.screenWidth)
        assertEquals(1440, out.screenHeight)
        assertEquals(false, out.enablePsoShaderWarmup)
    }

    @Test
    fun unknownPresetLevelLeavesTheCopyUntouched() {
        val base = HsrGraphicsSettings(fps = 60)
        assertEquals(base, base.withGraphicsPreset(99))
        assertEquals(base, base.withGraphicsPreset(-1))
    }

    @Test
    fun pendingGraphicsFieldsListsOnlyWhatChanged() {
        val base = HsrGraphicsSettings()
        assertTrue(pendingGraphicsFields(base, base.copy()).isEmpty())
        val changed = base.copy(fps = 120, shadowQuality = 5)
        assertEquals(setOf("fps", "shadowQuality"), pendingGraphicsFields(base, changed).toSet())
    }

    @Test
    fun pendingPrefsFieldsListsOnlyWhatChanged() {
        val base = HsrGamePreferences()
        assertTrue(pendingPrefsFields(base, base.copy()).isEmpty())
        val changed = base.copy(textLanguage = 5, autoBattleOpen = 1)
        assertEquals(setOf("textLanguage", "autoBattleOpen"), pendingPrefsFields(base, changed).toSet())
    }
}
