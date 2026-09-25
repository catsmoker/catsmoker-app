package com.catsmoker.app.features.gamingtools.engine

/**
 * Validates the touch-speed choice for Gaming Mode.
 *
 * `Settings.System.pointer_speed` runs -7 (slowest) through +7 (fastest) around a stock 0.
 * Stock needs no entry — not touching the setting already is stock — so 0 sanitizes to null
 * ("off"). Anything outside the platform range degrades to off rather than failing activation
 * or writing a speed the user did not pick.
 */
object PointerSpeed {

    /** The opt-in choices the UI offers, slowest to fastest, stock skipped. */
    val CHOICES = listOf(-5, -3, -1, 1, 3, 5)

    fun sanitize(speed: Int?): Int? {
        if (speed == null || speed == 0) return null
        return if (speed in MIN_SPEED..MAX_SPEED) speed else null
    }

    private const val MIN_SPEED = -7
    private const val MAX_SPEED = 7
}
