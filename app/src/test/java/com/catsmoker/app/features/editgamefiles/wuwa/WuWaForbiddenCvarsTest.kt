package com.catsmoker.app.features.editgamefiles.wuwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [WuWaForbiddenCvars] against the newer community integrity list
 * (`reference/gamingtools/Mobile-WuWa-Config-main/.github/forbidden_cvars.txt`,
 * v3.6, 51 entries), which was read before this test was written. The app's
 * original 31-key list came from `WuWa-Config-Android-main`'s `ForbiddenCvars.kt`;
 * the v3.6 list is its newer superset — drift between them must be caught
 * mechanically (TODO M102, refactor V12).
 *
 * Deliberate divergences from the v3.6 repo (never silent):
 * - Both spellings of the CppEffect(s)System key are kept even though v3.6 ships
 *   only the singular one: stripping a key the game does not watch is harmless,
 *   leaving a watched key in place is not.
 * - Matching stays key-exact + `+CVars=`-aware (TODO M103): the repo's CI script
 *   uses substring matching, which is weaker than the app stripper and is NOT
 *   adopted — only the keys are synced.
 */
class WuWaForbiddenCvarsTest {

    /** The v3.6 list verbatim (header comment excluded); casing as shipped. */
    private val v36Keys = setOf(
        "Kuro.CppEffectsystem.UseLowMemoryPlayerEffectLruCapacity",
        "r.AFME.Enable",
        "r.AsyncComputePSO",
        "r.DetailMode",
        "r.FEstimation.Option",
        "r.Kuro.SkeletalMesh.LODDistanceScale",
        "r.Kuro.TexturePool.ExtraBudgetMB",
        "r.KuroFI.Enable",
        "r.KuroMaterialQualityLevel",
        "r.LightMaxDrawDistanceScale",
        "r.MaterialQualityLevel",
        "r.MFRC.Enable",
        "r.MipMapLODBias",
        "r.Mobile.DeviceEvaluation",
        "r.MobileContentScaleFactor",
        "r.ParallelInitViews",
        "r.RayTracing.LimitDevice",
        "r.ScreenPercentage",
        "r.ScreenSizeCullRatioFactor",
        "r.SecondaryScreenPercentage.GameViewport",
        "r.Shadow.DistanceScale",
        "r.Shadow.MaxCSMResolution",
        "r.Shadow.MaxResolution",
        "r.streaming.AllowExtendedPoolSize",
        "r.Streaming.Boost",
        "r.Streaming.CPUReadback",
        "r.Streaming.DistancePriority.Texture2DArrayPriority",
        "r.streaming.ExtendedPoolSizeForceAllMipsThresholdPercentage",
        "r.streaming.ExtendedPoolSizeThresholdPercentage",
        "r.Streaming.KuroExtraPoolSize",
        "r.Streaming.LimitPoolSizeTOVRAM",
        "r.streaming.MaxExtendedPoolSizePercentage",
        "r.streaming.MaxExtendedPoolsizeVRAMPercentage",
        "r.Streaming.MaxNumTexturesTostreamPerFrame",
        "r.Streaming.MaxTempMemoryAllowed",
        "r.Streaming.MaxTempMemoryAllowedForTexture2DArray",
        "r.Streaming.MinBoost",
        "r.Streaming.MinMipForSplitRequest",
        "r.Streaming.PoolSize",
        "r.Streaming.PoolSizeExtraForTexture2DArray",
        "r.Streaming.Texture2DArrayStreamOutHysteresis",
        "r.Streaming.UseAllMips",
        "r.Streaming.UseAsyncCPUReadback",
        "r.Streaming.UseFixedPoolsize",
        "r.Streamline.DLSSG.RetainResourceswhenoff",
        "r.TextureGroup.Landscape.TextureLODBias",
        "r.ViewDistanceScale",
        "r.VolumetricFog",
        "r.VRS.EnableMaterial",
        "r.VRS.EnableMesh",
        "s.PriorityAsyncLoadingExtraTime",
    )

    @Test
    fun v36ListHas51Entries() {
        // Guards the fixture itself: if the upstream list changes, this test — not
        // a silent behavior drift — forces a conscious re-sync.
        assertEquals(51, v36Keys.size)
    }

    @Test
    fun everyV36KeyIsForbidden() {
        val missing = v36Keys.filterNot { WuWaForbiddenCvars.isForbidden(it) }
        assertTrue("keys missing from WuWaForbiddenCvars: $missing", missing.isEmpty())
    }

    @Test
    fun legacyPluralSpellingIsRetained() {
        // v3.6 ships only the singular CppEffectSystem spelling; the app keeps the
        // plural CppEffectsSystem variant too (see KDoc) — dropping it would
        // silently un-strip a possibly-watched key.
        assertTrue(
            WuWaForbiddenCvars.isForbidden(
                "Kuro.CppEffectsSystem.UseLowMemoryPlayerEffectLruCapacity"
            )
        )
        assertTrue(
            WuWaForbiddenCvars.isForbidden(
                "Kuro.CppEffectSystem.UseLowMemoryPlayerEffectLruCapacity"
            )
        )
    }

    @Test
    fun matchingStaysKeyExactNeverSubstring() {
        // M103: the repo CI matches substrings; the app must not. A key that merely
        // contains a forbidden key as a prefix is a different cvar and must survive.
        assertTrue(WuWaForbiddenCvars.isForbidden("r.Streaming.Boost"))
        assertFalse(WuWaForbiddenCvars.isForbidden("r.Streaming.Boosted"))
        assertFalse(WuWaForbiddenCvars.isForbidden("r.VolumetricFogQuality"))
        // …while established variant semantics (case, +/- prefixes) keep working.
        assertTrue(WuWaForbiddenCvars.isForbidden("R.VOLUMETRICFOG"))
        assertTrue(WuWaForbiddenCvars.isForbidden("+r.Shadow.MaxResolution"))
        assertTrue(WuWaForbiddenCvars.isForbidden("-s.PriorityAsyncLoadingExtraTime"))
    }

    @Test
    fun newV36KeysAreStrippedFromIniText() {
        val sample = """
            [SystemSettings]
            r.Shadow.MaxResolution=2048
            r.VolumetricFog=1
            s.PriorityAsyncLoadingExtraTime=0.5
            r.Mobile.DeviceEvaluation=1
            r.Kuro.AutoCoolEnable=1
        """.trimIndent()
        val out = WuWaForbiddenCvars.stripForbiddenCvars(sample)
        assertFalse(out.contains("Shadow.MaxResolution"))
        assertFalse(out.contains("VolumetricFog"))
        assertFalse(out.contains("PriorityAsyncLoadingExtraTime"))
        assertFalse(out.contains("Mobile.DeviceEvaluation"))
        assertTrue(out.contains("r.Kuro.AutoCoolEnable=1"))
    }
}
