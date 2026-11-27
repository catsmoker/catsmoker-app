package com.catsmoker.app.platform.display

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How a requested application rate divides the display's physical rate.
 *
 * The distinction is the whole reason this type exists: a rate the panel does not divide
 * evenly still works, but each frame stays up for an uneven number of refreshes, which is
 * the same effect as 24 fps video on a 60 Hz screen. Presenting it as an equivalent choice
 * would be the fake-FPS claim this feature must not make.
 */
enum class FramePacing {
    /** The panel refreshes exactly N times per requested frame. The intended case. */
    EVEN,

    /** The panel and the request do not divide; frames are held unevenly. */
    UNEVEN,

    /** The request is faster than the display can refresh — it cannot be honoured. */
    EXCEEDS
}

/**
 * Pure arithmetic over [DisplayMode] lists.
 *
 * No Android types are touched, so every rule here is unit-tested without a device. The
 * mode *catalogue* is the device's answer; this file only decides what that answer means.
 */
object DisplayRateMath {

    /** Rates differing by less than this are the same rate stored differently. */
    const val HZ_TOLERANCE = 0.6f

    /** Distinct rates across [modes], ascending, with near-duplicates collapsed. */
    fun distinctRates(modes: List<DisplayMode>): List<Float> {
        val out = ArrayList<Float>(modes.size)
        for (mode in modes.sortedBy { it.refreshRateHz }) {
            if (out.none { abs(it - mode.refreshRateHz) <= HZ_TOLERANCE }) out += mode.refreshRateHz
        }
        return out
    }

    /** Distinct rates available at exactly [width]x[height]; empty when no mode matches. */
    fun ratesAt(modes: List<DisplayMode>, width: Int, height: Int): List<Float> =
        distinctRates(modes.filter { it.width == width && it.height == height })

    /**
     * The nearest enumerated rate to [target], or null when nothing is within [tolerance].
     *
     * Returning null rather than the closest rate is deliberate: silently substituting a
     * different rate would make the UI claim a rate the display cannot run.
     */
    fun nearestRate(
        rates: List<Float>,
        target: Float,
        tolerance: Float = HZ_TOLERANCE
    ): Float? = rates.minByOrNull { abs(it - target) }?.takeIf { abs(it - target) <= tolerance }

    /**
     * The mode to switch to for [rateHz].
     *
     * Resolution is held first — [atWidth]x[atHeight] when the display offers that rate at
     * the same resolution, so pinning a rate never silently downscales the picture (some
     * panels only reach their top rate in a smaller mode group). Falls back to the largest
     * mode at that rate, then to null when the rate is not enumerated at all.
     */
    fun modeAtRate(
        modes: List<DisplayMode>,
        rateHz: Float,
        atWidth: Int? = null,
        atHeight: Int? = null,
        tolerance: Float = HZ_TOLERANCE
    ): DisplayMode? {
        val atRate = modes.filter { abs(it.refreshRateHz - rateHz) <= tolerance }
        if (atRate.isEmpty()) return null
        if (atWidth != null && atHeight != null) {
            atRate.firstOrNull { it.width == atWidth && it.height == atHeight }?.let { return it }
        }
        return atRate.maxByOrNull { it.width.toLong() * it.height }
    }

    /** [FramePacing] for an application rate [requestHz] on a display running [panelHz]. */
    fun pacing(
        panelHz: Float,
        requestHz: Float,
        tolerance: Float = HZ_TOLERANCE
    ): FramePacing {
        if (panelHz <= 0f || requestHz <= 0f) return FramePacing.UNEVEN
        if (requestHz > panelHz + tolerance) return FramePacing.EXCEEDS
        val refreshesPerFrame = panelHz / requestHz
        val whole = refreshesPerFrame.roundToInt()
        if (whole < 1) return FramePacing.EXCEEDS
        return if (abs(panelHz - whole * requestHz) <= tolerance) FramePacing.EVEN
        else FramePacing.UNEVEN
    }

    /**
     * The highest rate the display enumerates at the resolution it is already using.
     *
     * Mirrors the rule [DisplayRefreshRateProvider] applies to the built-in panel, kept here
     * so an external display and the phone panel are measured the same way.
     */
    fun peakRateAtActiveResolution(display: AttachedDisplay): Float? {
        val active = display.activeMode
        val atResolution = if (active != null) {
            ratesAt(display.modes, active.width, active.height)
        } else {
            emptyList()
        }
        val rates = atResolution.ifEmpty { display.rates }
        return rates.maxOrNull()
    }
}
