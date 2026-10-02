package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the ART sweep's compile-decision layer against
 * `reference/gamingtools/art/.../domain/model/common/AppCompilationInfo.kt`, which was read
 * before this test was written: the 7-day re-optimization gate, the optimal-filter lattice
 * (`speed` covers `speed-profile`; `everything` covers all), and the distinct skip reasons
 * (`NoProfile` ≠ `AlreadyOptimal` ≠ `RecentlyOptimized`).
 *
 * Plus the pre-scan analysis tallies (reference `OptimizationAnalysis`: needing / already /
 * no-profile counts before the sweep goes in) and the typed-log cap (reference
 * `OptimizationLogger.MAX_LOG_ENTRIES = 100`).
 */
class CompileDecisionTest {

    // ── isFilterOptimalForTarget ─────────────────────────────────────────────

    @Test
    fun speedCoversSpeedProfileTarget() {
        assertTrue(isFilterOptimalForTarget("speed", "speed-profile"))
    }

    @Test
    fun everythingCoversEveryTarget() {
        assertTrue(isFilterOptimalForTarget("everything", "speed"))
        assertTrue(isFilterOptimalForTarget("everything", "speed-profile"))
    }

    @Test
    fun speedProfileDoesNotCoverFullSpeed() {
        assertFalse(isFilterOptimalForTarget("speed-profile", "speed"))
    }

    @Test
    fun verifyAndUnknownAreNeverOptimal() {
        assertFalse(isFilterOptimalForTarget("verify", "speed-profile"))
        assertFalse(isFilterOptimalForTarget("verify", "speed"))
        assertFalse(isFilterOptimalForTarget(null, "speed-profile"))
        assertFalse(isFilterOptimalForTarget("unknown-present", "speed-profile"))
    }

    // ── shouldCompilePackage honors the lattice ─────────────────────────────

    @Test
    fun alreadyCompiledSpeedIsNotRecompiledForSpeedProfile() {
        // `speed` is strictly more compiled than `speed-profile`; recompiling it would
        // burn a slot for the same-or-worse result.
        assertFalse(shouldCompilePackage("speed", "speed-profile", false))
        assertFalse(shouldCompilePackage("everything", "speed-profile", false))
        assertFalse(shouldCompilePackage("everything", "speed", false))
    }

    // ── evaluateOptimization ─────────────────────────────────────────────────

    @Test
    fun unknownStateCompiles() {
        // No filter, no timestamps, no oat file: asking the platform is the only way out.
        assertTrue(evaluateOptimization(null, null, null, "speed-profile", oatFileExists = false).first)
        assertTrue(evaluateOptimization(null, null, null, "speed-profile", oatFileExists = true).first)
    }

    @Test
    fun updatedAfterCompileRecompiles() {
        val (needs, reason) = evaluateOptimization(
            compilerFilter = "speed-profile",
            lastCompilationTimeMs = 1_000L,
            lastUpdateTimeMs = 2_000L,
            targetFilter = "speed-profile",
            oatFileExists = true,
        )
        assertTrue(needs)
        assertNull(reason)
    }

    @Test
    fun verifyUnderSpeedProfileIsNoProfile() {
        val (needs, reason) = evaluateOptimization(
            compilerFilter = "verify",
            lastCompilationTimeMs = 1_000L,
            lastUpdateTimeMs = 500L,
            targetFilter = "speed-profile",
            oatFileExists = true,
        )
        assertFalse(needs)
        assertTrue(reason is CompileSkipReason.NoProfile)
    }

    @Test
    fun recentlyOptimizedSkipsWithAge() {
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 2L * 24 * 60 * 60 * 1000
        val (needs, reason) = evaluateOptimization(
            compilerFilter = "speed-profile",
            lastCompilationTimeMs = twoDaysAgo,
            lastUpdateTimeMs = twoDaysAgo - 1_000L,
            targetFilter = "speed-profile",
            oatFileExists = true,
            nowMs = now,
        )
        assertFalse(needs)
        val recent = reason as? CompileSkipReason.RecentlyOptimized
        assertEquals(2L, recent?.daysAgo)
    }

    @Test
    fun staleOptimalRecompilesAfterSevenDays() {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
        val (needs, _) = evaluateOptimization(
            compilerFilter = "speed-profile",
            lastCompilationTimeMs = thirtyDaysAgo,
            lastUpdateTimeMs = thirtyDaysAgo - 1_000L,
            targetFilter = "speed-profile",
            oatFileExists = true,
            nowMs = now,
        )
        // Profiles accumulated for a month; a re-compile has material to work with.
        assertTrue(needs)
    }

    // ── analyzeDexoptStatuses ────────────────────────────────────────────────

    @Test
    fun analysisTalliesNeedingAlreadyNoProfileAndUnknown() {
        val statuses = mapOf(
            "com.need.one" to "verify", // no-profile under speed-profile
            "com.need.two" to "quicken",
            "com.done" to "speed-profile",
            "com.apex" to "speed",
        )
        val universe = statuses.keys + "com.ghost" // eligible but absent from the dump
        val analysis = analyzeDexoptStatuses(statuses, "speed-profile", universe)
        assertEquals(5, analysis.totalApps)
        assertEquals(2, analysis.appsNeedingOptimization) // quicken + ghost
        assertEquals(2, analysis.appsAlreadyOptimized) // speed-profile + speed
        assertEquals(1, analysis.appsWithNoProfile) // verify
        assertEquals(listOf("com.ghost"), analysis.unknownPackages)
    }

    // ── typed log ────────────────────────────────────────────────────────────

    @Test
    fun typedLogCapsAtOneHundred() {
        var entries = emptyList<BoosterLogEntry>()
        repeat(150) { i -> entries = appendBoosterLogEntry(entries, BoosterLogEntry(BoosterLogType.INFO, null, "line $i")) }
        assertEquals(100, entries.size)
        // Oldest evicted, newest kept.
        assertEquals("line 50", entries.first().message)
        assertEquals("line 149", entries.last().message)
    }

    @Test
    fun logTypesCoverTheSweepLifecycle() {
        val types = BoosterLogType.entries.map { it.name }.toSet()
        assertTrue(types.containsAll(setOf("ANALYZING", "NO_PROFILE", "SKIPPED", "OPTIMIZING", "SUCCESS", "COMMAND", "START", "COMPLETE", "CANCELLED")))
    }
}
