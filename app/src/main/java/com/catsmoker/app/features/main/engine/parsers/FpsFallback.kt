package com.catsmoker.app.features.main.engine.parsers

import kotlin.math.roundToInt

/**
 * Spike guard for the vsync-fallback FPS count, after the reference's `FpsCalculator`
 * (`reference/debug-overlay/.../FpsCalculator.kt`, read before this file was written).
 *
 * The fallback counts wall-clock callbacks, so a burst can report a rate no panel can
 * display. The count is coerced into `[0, maxFps]`, where maxFps is the panel's measured
 * maximum — never a hardcoded ceiling, and no clamp at all when the panel facts are
 * unknown (inventing a ceiling would be a cap by another name).
 */
fun clampFpsSample(sample: Int, maxHz: Float?): Int {
    val max = maxHz?.takeIf { it > 0 }?.roundToInt() ?: return sample.coerceAtLeast(0)
    return sample.coerceIn(0, max)
}
