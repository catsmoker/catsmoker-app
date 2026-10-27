package com.catsmoker.app.features.gamingtools.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.catsmoker.app.features.gamingtools.engine.GamingModeState
import com.catsmoker.app.shared.ui.components.SquigglyProgressBar

/**
 * What the Gaming Mode progress bar is allowed to show, derived from the engine's own state.
 *
 * The bar used to be permanent: it sat on the card as a flat line even when nothing was running,
 * which made it look like a finished or decorative element. It is an operation indicator now — the
 * card only composes [GamingModeProgressBar] while [isVisible] says the engine is working.
 *
 * Enabling already reports real progress from its read-back steps, so those values are passed
 * through unchanged. Disabling has no per-step progress in the engine; the card shows a full bar
 * and runs the reverse tween in [GamingModeProgressBar] for as long as the engine stays in
 * [GamingModeState.Disabling].
 */
internal object GamingModeProgress {
    /** Where the reverse transition begins: the enabled state the user is leaving. */
    const val REVERSE_START = 1f

    /** Where the reverse transition ends. */
    const val REVERSE_END = 0f

    /**
     * How long the reverse fill takes. Long enough to read as a deliberate unwind, short enough
     * that a fast revert is not held open by the animation: the operation's state owns visibility,
     * so a revert that finishes first simply removes the bar.
     */
    const val REVERSE_DURATION_MS = 1_500

    fun isVisible(state: GamingModeState): Boolean =
        state is GamingModeState.Enabling || state is GamingModeState.Disabling

    /**
     * The forward value for an enabling run, clamped to the bar's range, or the starting value
     * while disabling. Null means there is no operation to display.
     */
    fun progressFor(state: GamingModeState): Float? = when (state) {
        is GamingModeState.Enabling -> state.progress.coerceIn(0f, 1f)
        is GamingModeState.Disabling -> REVERSE_START
        else -> null
    }
}

/**
 * The squiggly operation bar, shown only while the engine is enabling or disabling Gaming Mode.
 *
 * Forward progress is the engine's sequence, so the current enabling animation keeps its exact
 * values and timing. Disabling starts at [GamingModeProgress.REVERSE_START] and animates toward
 * [GamingModeProgress.REVERSE_END]; [LaunchedEffect] cancels that animation when the state changes
 * or the card leaves composition, and the card removes this composable as soon as the operation
 * ends, so neither path can leave a stale bar behind.
 */
@Composable
fun GamingModeProgressBar(
    state: GamingModeState,
    modifier: Modifier = Modifier
) {
    val isDisabling = state is GamingModeState.Disabling
    val reverse = remember { Animatable(GamingModeProgress.REVERSE_START) }

    LaunchedEffect(isDisabling) {
        if (isDisabling) {
            reverse.snapTo(GamingModeProgress.REVERSE_START)
            reverse.animateTo(
                targetValue = GamingModeProgress.REVERSE_END,
                animationSpec = tween(
                    durationMillis = GamingModeProgress.REVERSE_DURATION_MS,
                    easing = LinearEasing
                )
            )
        } else {
            // A fresh enable starts from an idle bar; a previous reverse must not leak into it.
            reverse.snapTo(GamingModeProgress.REVERSE_START)
        }
    }

    val progress = if (isDisabling) {
        reverse.value
    } else {
        GamingModeProgress.progressFor(state) ?: GamingModeProgress.REVERSE_END
    }

    SquigglyProgressBar(
        progress = progress,
        animate = true,
        modifier = modifier.fillMaxWidth()
    )
}
