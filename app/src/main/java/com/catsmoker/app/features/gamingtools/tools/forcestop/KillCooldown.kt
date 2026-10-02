package com.catsmoker.app.features.gamingtools.tools.forcestop

/**
 * Kill cooldown for the auto force-stop loop: per-package last-kill timestamps with a fixed
 * window, so a rapid A→B→A foreground switch does not kill A twice within seconds for
 * nothing.
 *
 * New design, not a port — neither tree has a per-package killer to copy the value from.
 * [COOLDOWN_MS] is a heuristic at 5× the service's 2 s poll: long enough to ride out a fast
 * app switch, short enough that a genuinely restarted app is enforced again promptly. Kept
 * pure so the rule is pinnable without a device; device observation may retune the window.
 */
object KillCooldown {

    const val COOLDOWN_MS = 10_000L

    /**
     * Whether [pkg] may be killed at [nowMs]. A timestamp in the future (clock skew) never
     * suppresses — a stuck map must fail open, not silent.
     */
    fun shouldKill(
        pkg: String,
        nowMs: Long,
        lastKills: Map<String, Long>,
        cooldownMs: Long = COOLDOWN_MS
    ): Boolean {
        val last = lastKills[pkg] ?: return true
        val elapsed = nowMs - last
        if (elapsed < 0) return true
        return elapsed >= cooldownMs
    }
}
