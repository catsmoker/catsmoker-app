package com.catsmoker.app.features.gamingtools.tools.forcestop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the force-stop kill cooldown (TODO M70): a rapid A→B→A foreground switch must not
 * kill A twice within seconds for nothing — the first kill already did the job.
 *
 * There is no reference to port here (neither tree has a killer, let alone a cooldown), so
 * this pins the new design instead: per-package last-kill timestamps with a fixed window.
 * The window is a heuristic (5× the 2 s poll), documented as pending device observation.
 */
class KillCooldownTest {

    @Test
    fun firstKillAlwaysAllowed() {
        assertTrue(KillCooldown.shouldKill("com.a", 10_000L, emptyMap()))
    }

    @Test
    fun immediateRekillIsSuppressed() {
        val lastKills = mapOf("com.a" to 10_000L)
        assertFalse(KillCooldown.shouldKill("com.a", 12_000L, lastKills))
    }

    @Test
    fun killAfterTheWindowIsAllowed() {
        val lastKills = mapOf("com.a" to 10_000L)
        assertTrue(KillCooldown.shouldKill("com.a", 10_000L + KillCooldown.COOLDOWN_MS, lastKills))
    }

    @Test
    fun cooldownIsPerPackage() {
        val lastKills = mapOf("com.a" to 10_000L)
        assertTrue(KillCooldown.shouldKill("com.b", 11_000L, lastKills))
    }

    @Test
    fun clockSkewBackwardsDoesNotSuppressForever() {
        val lastKills = mapOf("com.a" to 20_000L)
        assertTrue(KillCooldown.shouldKill("com.a", 10_000L, lastKills))
    }
}
