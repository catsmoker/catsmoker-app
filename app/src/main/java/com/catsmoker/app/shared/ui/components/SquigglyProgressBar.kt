package com.catsmoker.app.shared.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos

/**
 * Pure math behind the Android 13 media-player squiggle, ported from AOSP
 * SystemUI `SquigglyProgress` (`platform/frameworks/base/.../SquigglyProgress.kt`).
 *
 * Kept free of Compose types so JVM unit tests can pin it: the wave-endpoint
 * stretch (a visible squiggle even near zero progress) and the tapered
 * amplitude envelope around the progress head. The composable below only turns
 * these numbers into a `Path`.
 */
object SquigglyMath {
    /** Distance over which amplitude drops to zero, measured in wavelengths. */
    const val TRANSITION_PERIODS = 1.5f

    /** Wave endpoint as a fraction of the bar when progress is zero. */
    const val MIN_WAVE_ENDPOINT = 0.2f

    /** Wave endpoint as a fraction of the bar once progress catches up. */
    const val MATCHED_WAVE_ENDPOINT = 0.6f

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun lerpInv(a: Float, b: Float, v: Float): Float =
        if (a == b) 0f else (v - a) / (b - a)

    fun lerpInvSat(a: Float, b: Float, v: Float): Float =
        lerpInv(a, b, v).coerceIn(0f, 1f)

    /**
     * Stretched wave endpoint (0..1) for a given progress. Small progress values
     * map onto a wider wave so the squiggle stays visible, exactly like AOSP;
     * above [MATCHED_WAVE_ENDPOINT] the wave follows progress exactly.
     */
    fun waveEndpoint(progress: Float, transitionEnabled: Boolean = true): Float {
        val p = progress.coerceIn(0f, 1f)
        if (!transitionEnabled || p > MATCHED_WAVE_ENDPOINT) return p
        return lerp(MIN_WAVE_ENDPOINT, MATCHED_WAVE_ENDPOINT, lerpInv(0f, MATCHED_WAVE_ENDPOINT, p))
    }

    /**
     * Amplitude coefficient (0..1) at horizontal position [x], tapering linearly
     * across [TRANSITION_PERIODS] wavelengths centred on [waveEndPx].
     *
     * @param heightFraction 0 = flat line (paused), 1 = full wave (playing).
     */
    fun amplitudeCoeff(
        x: Float,
        waveEndPx: Float,
        waveLength: Float,
        heightFraction: Float = 1f
    ): Float {
        if (waveLength <= 0f) return 0f
        val length = TRANSITION_PERIODS * waveLength
        return lerpInvSat(waveEndPx + length / 2f, waveEndPx - length / 2f, x) * heightFraction
    }
}

private const val TWO_PI = (Math.PI * 2f).toFloat()

/**
 * Android 13-style squiggly progress bar (AOSP SystemUI media player).
 *
 * The played portion is a travelling sine wave in [color]; the unplayed portion
 * is the same wave faded through a 1.5-wavelength transition in [trackColor],
 * clipped at the true progress — so the head melts from wave into line instead
 * of snapping. When [animate] is false the wave flattens to a straight line,
 * mirroring how the system player stops moving when paused.
 *
 * @param progress 0..1, or null for an indeterminate full-width travelling wave.
 * @param animate whether the phase travels and the amplitude stays at full height.
 */
@Composable
fun SquigglyProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = 3.dp,
    waveLength: Dp = 16.dp,
    waveAmplitude: Dp = 3.dp,
    barHeight: Dp = 16.dp
) {
    val density = LocalDensity.current
    val waveLengthPx = with(density) { waveLength.toPx() }
    val amplitudePx = with(density) { waveAmplitude.toPx() }
    val strokePx = with(density) { strokeWidth.toPx() }
    val current = progress?.coerceIn(0f, 1f)

    val travelling = animate || progress == null
    var phase = 0f
    if (travelling && waveLengthPx > 0f) {
        val infinite = rememberInfiniteTransition(label = "squiggly-phase")
        phase = infinite.animateFloat(
            initialValue = 0f,
            targetValue = waveLengthPx,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "squiggly-phase"
        ).value
    }
    // Pausing flattens the wave into a line over ~550ms; (re)starting grows it
    // back over ~800ms — the same timings AOSP uses for its height animator.
    val heightFraction by animateFloatAsState(
        targetValue = if (travelling) 1f else 0f,
        animationSpec = tween(durationMillis = if (travelling) 800 else 550),
        label = "squiggly-height"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = if (current == null) {
                    ProgressBarRangeInfo.Indeterminate
                } else {
                    ProgressBarRangeInfo(current, 0f..1f)
                }
            }
    ) {
        val totalWidth = size.width
        if (totalWidth <= 0f || waveLengthPx <= 0f || strokePx <= 0f) return@Canvas
        val centerY = size.height / 2f
        val progressPx = totalWidth * (current ?: 1f)
        val waveProgressPx = totalWidth * SquigglyMath.waveEndpoint(current ?: 1f)

        fun coeffAt(x: Float): Float =
            if (current == null) heightFraction
            else SquigglyMath.amplitudeCoeff(x, waveProgressPx, waveLengthPx, heightFraction)

        // Cubic half-wave segments approximating a sine, as in AOSP: each step
        // advances half a wavelength and flips the sign.
        val path = Path()
        val waveStart = -phase - waveLengthPx / 2f
        var currentX = waveStart
        var waveSign = 1f
        var currentAmp = coeffAt(currentX) * waveSign * amplitudePx
        path.moveTo(currentX, centerY + currentAmp)
        val dist = waveLengthPx / 2f
        while (currentX < totalWidth) {
            waveSign = -waveSign
            val nextX = (currentX + dist).coerceAtMost(totalWidth + dist)
            val midX = currentX + dist / 2f
            val nextAmp = coeffAt(nextX.coerceAtMost(totalWidth)) * waveSign * amplitudePx
            path.cubicTo(midX, centerY + currentAmp, midX, centerY + nextAmp, nextX, centerY + nextAmp)
            currentAmp = nextAmp
            currentX = nextX
        }

        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
        if (current == null) {
            drawPath(path, color = color, style = stroke)
        } else {
            clipRect(left = 0f, top = 0f, right = progressPx, bottom = size.height) {
                drawPath(path, color = color, style = stroke)
            }
            clipRect(left = progressPx, top = 0f, right = totalWidth, bottom = size.height) {
                drawPath(path, color = trackColor, style = stroke)
            }
            // Round cap dot at the wave start, as AOSP draws with the wave paint.
            if (progressPx > 0f) {
                val startAmp = cos(abs(waveStart) / waveLengthPx * TWO_PI) * heightFraction * amplitudePx
                drawCircle(
                    color = color,
                    radius = strokePx / 2f,
                    center = androidx.compose.ui.geometry.Offset(0f, centerY + startAmp)
                )
            }
        }
    }
}
