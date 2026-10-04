package com.catsmoker.app.features.spoofdevice.root

import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import kotlin.math.roundToInt

/**
 * Display-metrics spoof math: pixel sizes onto metrics/bounds/points, density across the
 * whole density family, and a coherent Configuration (dp values, smallest width,
 * orientation, size class).
 *
 * Adapted from `reference/spoofdevice/example-1/.../hooks/DisplayHooks.java` (read in full):
 * same formulas, driven by this app's rendered `screen.*` keys instead of the reference's
 * per-field toggles. Absent fields leave the device's real values alone — coherence means
 * every touched surface agrees, and untouched surfaces stay real.
 */
object SpoofDisplayMetrics {

    /** The rendered screen keys, parsed; null when the profile carries no screen block. */
    data class ScreenSpec(val width: Int, val height: Int, val densityDpi: Int)

    fun specFromProps(props: Map<String, String>): ScreenSpec? {
        val w = props["screen.width"]?.toIntOrNull()?.takeIf { it > 0 }
        val h = props["screen.height"]?.toIntOrNull()?.takeIf { it > 0 }
        val dpi = props["screen.density"]?.toIntOrNull()?.takeIf { it > 0 }
        if (w == null && h == null && dpi == null) return null
        return ScreenSpec(w ?: 0, h ?: 0, dpi ?: 0)
    }

    /** Whether the master metrics switch is on: explicit opt-in, blank/off by default. */
    fun shouldApply(props: Map<String, String>): Boolean =
        props[com.catsmoker.app.shared.data.model.LSPosedConfig.KEY_APPLY_SCREEN_METRICS] == "1"

    fun applyMetrics(metrics: DisplayMetrics, width: Int?, height: Int?, densityDpi: Int?) {
        if (width != null && width > 0) metrics.widthPixels = width
        if (height != null && height > 0) metrics.heightPixels = height
        if (densityDpi != null && densityDpi > 0) {
            metrics.densityDpi = densityDpi
            metrics.density = densityDpi / 160f
            // Deprecated with no setter replacement; a fabricated DisplayMetrics with
            // density but no scaledDensity would mis-scale every sp dimension, so the
            // field is still written deliberately.
            metrics.scaledDensity = densityDpi / 160f
            metrics.xdpi = densityDpi.toFloat()
            metrics.ydpi = densityDpi.toFloat()
        }
    }

    fun applyConfiguration(config: Configuration, width: Int?, height: Int?, densityDpi: Int?) {
        val widthValue = width?.takeIf { it > 0 }
        val heightValue = height?.takeIf { it > 0 }
        val densityValue = densityDpi?.takeIf { it > 0 }
        if (widthValue == null && heightValue == null && densityValue == null) return

        val originalDensity = config.densityDpi.takeIf { it > 0 } ?: densityDpi ?: 0
        val effectiveDensity = densityValue ?: originalDensity
        if (effectiveDensity <= 0) return

        val widthPixels = widthValue ?: (config.screenWidthDp * originalDensity / 160f).roundToInt()
        val heightPixels = heightValue ?: (config.screenHeightDp * originalDensity / 160f).roundToInt()

        if (densityValue != null) config.densityDpi = effectiveDensity
        if (widthValue != null || densityValue != null) config.screenWidthDp = (widthPixels * 160f / effectiveDensity).roundToInt()
        if (heightValue != null || densityValue != null) config.screenHeightDp = (heightPixels * 160f / effectiveDensity).roundToInt()

        config.smallestScreenWidthDp = minOf(config.screenWidthDp, config.screenHeightDp)
        config.orientation = if (widthPixels >= heightPixels) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
        val sizeMask = when {
            config.smallestScreenWidthDp >= 720 -> Configuration.SCREENLAYOUT_SIZE_XLARGE
            config.smallestScreenWidthDp >= 600 -> Configuration.SCREENLAYOUT_SIZE_LARGE
            config.smallestScreenWidthDp >= 480 -> Configuration.SCREENLAYOUT_SIZE_NORMAL
            else -> Configuration.SCREENLAYOUT_SIZE_SMALL
        }
        config.screenLayout = config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK.inv() or sizeMask
    }

    fun applyBounds(bounds: Rect, width: Int?, height: Int?) {
        val w = if (width != null && width > 0) width else bounds.width()
        val h = if (height != null && height > 0) height else bounds.height()
        bounds.right = bounds.left + w
        bounds.bottom = bounds.top + h
    }

    fun applyPoint(point: Point, width: Int?, height: Int?) {
        if (width != null && width > 0) point.x = width
        if (height != null && height > 0) point.y = height
    }
}
