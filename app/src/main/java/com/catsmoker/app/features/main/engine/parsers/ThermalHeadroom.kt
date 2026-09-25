package com.catsmoker.app.features.main.engine.parsers

import com.catsmoker.app.shared.data.model.MetricReadStatus

/**
 * Classifies one `PowerManager.getThermalHeadroom` sample (API 30+).
 *
 * The platform returns a non-negative float where 1.0 marks the SEVERE throttle threshold and
 * values above 1.0 are allowed (heavier throttling), or NaN when the thermal HAL exposes
 * nothing — including when polled faster than about once per second. NaN therefore means
 * "unsupported here", never 0.0: a device with full headroom and a device with no sensor
 * are different facts. Negative or infinite values are outside the documented range and are
 * reported as unparseable rather than rescaled into a guess.
 */
object ThermalHeadroom {

    /** One classified sample: the value to display, or null with the reason it is absent. */
    data class Reading(val headroom: Float?, val status: MetricReadStatus)

    fun classify(raw: Float): Reading = when {
        raw.isNaN() -> Reading(null, MetricReadStatus.Unsupported)
        !raw.isFinite() || raw < 0f -> Reading(null, MetricReadStatus.ParseFailed)
        else -> Reading(raw, MetricReadStatus.Ok)
    }
}
