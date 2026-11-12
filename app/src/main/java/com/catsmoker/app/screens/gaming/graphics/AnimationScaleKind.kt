package com.catsmoker.app.screens.gaming.graphics

import android.provider.Settings

/**
 * The three animation scales Android's Developer Options exposes, with the keys it writes.
 *
 * Kept as the real `Settings.Global` constants: these are the same settings the system screen edits,
 * so a change here is the same change made there — not an app-local imitation of one.
 */
enum class AnimationScaleKind(val key: String, val label: String) {
    WINDOW(Settings.Global.WINDOW_ANIMATION_SCALE, "Window animation scale"),
    TRANSITION(Settings.Global.TRANSITION_ANIMATION_SCALE, "Transition animation scale"),
    ANIMATOR(Settings.Global.ANIMATOR_DURATION_SCALE, "Animator duration scale")
}
