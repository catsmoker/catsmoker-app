package com.catsmoker.app.features.editgamefiles.wuwa

/**
 * Deploy-feedback retune for WuWa profiles: given the profile a deploy shipped and the
 * measured before/after gameplay deltas from two decrypted Client.log analyses, returns the
 * profile the next deploy should ship.
 *
 * Ported from `reference/gamingtools/WuWa-Config-Android-main/config/CvarOptimizer.kt::
 * adjustProfile` with its `model/DeployRecord.kt::comparison` delta shape (both read in full
 * before this file was written, plus the two `CvarOptimizerTest` shadowRes vectors the tests
 * pin): stable stays, an OOM takes the escape hatch, degradation scales screen x0.75 with
 * −2 shadow / −1 detail / −1 ssr and re-derives shadowRes from the *new* shadow rank,
 * improvement steps screen x1.15 with +1 shadow / detail / ssr under the reference's caps
 * (100 / 5 / 2 / 4). Thresholds are the reference's own (±5 FPS, >2 thermals, >5 drops).
 *
 * Deliberate divergences, each documented:
 *  - It operates on [WuWaConfigGenerator.PresetProfile] rather than a parallel
 *    `OptimizedProfile`: the first eleven fields are the reference's shape in order, so the
 *    math ports line-for-line while the app's four extension fields (characterDetail,
 *    postProcess, staticLighting, cutsceneQuality) ride along untouched through `copy`.
 *  - The reference's `optimizeProfile` first-guess picker is NOT ported: [WuwaSmartBrain]
 *    already scores that decision from the same signals, and a second competing picker
 *    would be a fork, not a port.
 *  - A null delta means no measured change (0 for the comparison), never a zero reading:
 *    deltas compare two analyses, so "unknown difference" is honestly "no proven change".
 *    The all-null comparison therefore returns the profile untouched.
 *  - The OOM escape hatch keeps the reference's exact values, which differ from the app's
 *    "potato" preset (screen 50 vs 60, shadowRes 256 vs 128): it is their tested emergency
 *    floor, not this app's tier table.
 *
 * Mechanism only: the loop wiring that captures a baseline at deploy time and asks for an
 * outcome log after play belongs to the deploy history / log pipeline, not here — this
 * function must never be fed deltas nobody measured.
 */
object WuwaCvarRetune {

    /**
     * Before/after gameplay deltas between two Client.log analyses. null = that axis was
     * not measured on one side, which counts as no proven change (see class KDoc).
     */
    data class DeployComparison(
        val fpsDelta: Float?,
        val thermalDelta: Int?,
        val oomDelta: Int?,
        val dropFramesDelta: Int?,
    )

    fun adjustProfile(
        current: WuWaConfigGenerator.PresetProfile,
        comparison: DeployComparison,
    ): WuWaConfigGenerator.PresetProfile {
        val fpsDelta = comparison.fpsDelta ?: 0f
        val oomDelta = comparison.oomDelta ?: 0
        val thermalDelta = comparison.thermalDelta ?: 0
        val dropDelta = comparison.dropFramesDelta ?: 0

        val wasStable = fpsDelta in -5f..5f && oomDelta <= 0 && thermalDelta <= 2 && dropDelta <= 5
        val degraded = fpsDelta < -5f || oomDelta > 0 || thermalDelta > 2 || dropDelta > 5
        val improved = fpsDelta > 5f && oomDelta <= 0 && thermalDelta <= 0 && dropDelta <= 0

        if (wasStable) return current

        if (oomDelta > 0) {
            return WuWaConfigGenerator.PresetProfile(
                50, 0, 256, 0, 3, 0.3, 0.3, 0.4, 0, 5, 1500,
            )
        }

        if (degraded) {
            val newScreen = (current.screen * 0.75f).toInt().coerceIn(50, 100)
            val newShadow = (current.shadow - 2).coerceAtLeast(0)
            val newDetail = (current.detail - 1).coerceAtLeast(0)
            val newSsr = (current.ssr - 1).coerceAtLeast(0)
            val newMipbias = (current.mipbias + 1).coerceAtMost(3)
            val newVd = (current.vd * 0.6).coerceAtMost(current.vd)
            val newFlod = (current.flod * 0.6).coerceAtMost(current.flod)
            return current.copy(
                screen = newScreen,
                shadow = newShadow,
                shadowRes =
                    when (newShadow) {
                        3, 4, 5 -> 1024 // reduced from 2048 on degrade to save VRAM
                        1, 2 -> 256
                        else -> 128
                    },
                ssr = newSsr,
                mipbias = newMipbias,
                vd = newVd,
                flod = newFlod,
                detail = newDetail,
                lod_bias = if (newDetail == 0) 3 else current.lod_bias,
                grasscull = if (newDetail == 0) 4500 else current.grasscull,
            )
        }

        if (improved) {
            val newScreen = (current.screen * 1.15f).toInt().coerceIn(50, 100)
            val newShadow = (current.shadow + 1).coerceAtMost(5)
            val newDetail = (current.detail + 1).coerceAtMost(2)
            val newSsr = (current.ssr + 1).coerceAtMost(4)
            return current.copy(
                screen = newScreen,
                shadow = newShadow,
                shadowRes =
                    when (newShadow) {
                        4, 5 -> 2048
                        2, 3 -> 1024
                        else -> 256
                    },
                ssr = newSsr,
                detail = newDetail,
                grasscull =
                    when {
                        newDetail == 2 && current.grasscull < 20000 -> 30000
                        else -> current.grasscull
                    },
            )
        }

        return current
    }
}
