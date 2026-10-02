package com.catsmoker.app.features.editgamefiles.wuwa

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the deploy-feedback retune against
 * `reference/gamingtools/WuWa-Config-Android-main/config/CvarOptimizer.kt::adjustProfile`
 * (+ its `model/DeployRecord.kt::comparison` delta shape and the two `CvarOptimizerTest`
 * shadowRes vectors), all read in full before this test was written: a stable reading leaves
 * the profile untouched, an OOM takes the escape hatch, degradation scales by x0.75/−2 and
 * re-derives shadowRes from the *new* shadow rank, improvement scales by x1.15/+1 under the
 * reference's caps. A wrong factor silently recommends hours of play on a profile the device
 * already proved it cannot hold.
 */
class WuwaCvarRetuneTest {

    private fun profile(
        screen: Int = 80,
        shadow: Int = 2,
        shadowRes: Int = 1024,
        ssr: Int = 1,
        mipbias: Int = 0,
        streaming: Double = 2.0,
        vd: Double = 1.5,
        flod: Double = 2.0,
        detail: Int = 4,
        lodBias: Int = 0,
        grasscull: Int = 15000,
    ) = WuWaConfigGenerator.PresetProfile(
        screen = screen, shadow = shadow, shadowRes = shadowRes, ssr = ssr,
        mipbias = mipbias, streaming = streaming, vd = vd, flod = flod,
        detail = detail, lod_bias = lodBias, grasscull = grasscull,
    )

    @Test
    fun stableComparisonLeavesTheProfileUntouched() {
        val current = profile()
        val flat = WuwaCvarRetune.DeployComparison(
            fpsDelta = 0f, thermalDelta = 0, oomDelta = 0, dropFramesDelta = 0,
        )
        assertEquals(current, WuwaCvarRetune.adjustProfile(current, flat))
    }

    @Test
    fun allNullDeltasMeanNoMeasuredChange() {
        val current = profile()
        val unknown = WuwaCvarRetune.DeployComparison(
            fpsDelta = null, thermalDelta = null, oomDelta = null, dropFramesDelta = null,
        )
        assertEquals(current, WuwaCvarRetune.adjustProfile(current, unknown))
    }

    @Test
    fun degradedProfileScalesShadowResByTheNewShadowRank() {
        val current = profile(
            screen = 100, shadow = 5, shadowRes = 4096, ssr = 4,
            streaming = 6.0, vd = 4.0, flod = 4.0, grasscull = 40000,
        )
        val degraded = WuwaCvarRetune.DeployComparison(
            fpsDelta = -10f, thermalDelta = 0, oomDelta = 0, dropFramesDelta = 0,
        )
        val out = WuwaCvarRetune.adjustProfile(current, degraded)
        // Shadow drops 5 -> 3, so shadowRes follows the 1024 ladder instead of being
        // forced to 256 (the old bug that collapsed high-res shadows on degradation).
        assertEquals(75, out.screen)
        assertEquals(3, out.shadow)
        assertEquals(1024, out.shadowRes)
        assertEquals(3, out.detail)
    }

    @Test
    fun degradedProfileOnLowestShadowKeepsSmallShadowRes() {
        val current = profile(
            screen = 60, shadow = 1, shadowRes = 512, ssr = 0, mipbias = 3,
            streaming = 0.3, vd = 0.3, flod = 0.4, detail = 1, lodBias = 3, grasscull = 1500,
        )
        val degraded = WuwaCvarRetune.DeployComparison(
            fpsDelta = -20f, thermalDelta = 5, oomDelta = 0, dropFramesDelta = 10,
        )
        val out = WuwaCvarRetune.adjustProfile(current, degraded)
        assertEquals(0, out.shadow)
        assertEquals(128, out.shadowRes)
    }

    @Test
    fun oomTakesTheEscapeHatch() {
        val out = WuwaCvarRetune.adjustProfile(
            profile(),
            WuwaCvarRetune.DeployComparison(
                fpsDelta = 0f, thermalDelta = 0, oomDelta = 1, dropFramesDelta = 0,
            ),
        )
        assertEquals(50, out.screen)
        assertEquals(0, out.shadow)
        assertEquals(256, out.shadowRes)
        assertEquals(0, out.ssr)
        assertEquals(3, out.mipbias)
        assertEquals(0.3, out.streaming, 0.0)
        assertEquals(0.3, out.vd, 0.0)
        assertEquals(0.4, out.flod, 0.0)
        assertEquals(0, out.detail)
        assertEquals(5, out.lod_bias)
        assertEquals(1500, out.grasscull)
    }

    @Test
    fun improvedProfileStepsUpUnderTheCaps() {
        val current = profile(detail = 1)
        val improved = WuwaCvarRetune.DeployComparison(
            fpsDelta = 10f, thermalDelta = 0, oomDelta = 0, dropFramesDelta = 0,
        )
        val out = WuwaCvarRetune.adjustProfile(current, improved)
        assertEquals(92, out.screen)
        assertEquals(3, out.shadow)
        assertEquals(1024, out.shadowRes)
        assertEquals(2, out.ssr)
        assertEquals(2, out.detail)
        assertEquals(30000, out.grasscull)
    }

    @Test
    fun improvedProfileNeverExceedsTheCeilings() {
        val current = profile(
            screen = 100, shadow = 5, shadowRes = 2048, ssr = 4,
            streaming = 4.0, vd = 3.0, flod = 3.0, detail = 2, grasscull = 30000,
        )
        val improved = WuwaCvarRetune.DeployComparison(
            fpsDelta = 30f, thermalDelta = 0, oomDelta = 0, dropFramesDelta = 0,
        )
        val out = WuwaCvarRetune.adjustProfile(current, improved)
        assertEquals(100, out.screen)
        assertEquals(5, out.shadow)
        assertEquals(2048, out.shadowRes)
        assertEquals(4, out.ssr)
        assertEquals(2, out.detail)
    }

    @Test
    fun appExtensionsRideAlongUntouched() {
        val current = WuWaConfigGenerator.PresetProfile(
            screen = 70, shadow = 0, shadowRes = 128, ssr = 0, mipbias = 3,
            streaming = 0.4, vd = 0.4, flod = 0.5, detail = 1, lod_bias = 4, grasscull = 2500,
            characterDetail = 0, postProcess = 0, staticLighting = false, cutsceneQuality = 0,
        )
        val degraded = WuwaCvarRetune.DeployComparison(
            fpsDelta = -10f, thermalDelta = 0, oomDelta = 0, dropFramesDelta = 0,
        )
        val out = WuwaCvarRetune.adjustProfile(current, degraded)
        assertEquals(0, out.characterDetail)
        assertEquals(0, out.postProcess)
        assertEquals(false, out.staticLighting)
        assertEquals(0, out.cutsceneQuality)
    }
}
