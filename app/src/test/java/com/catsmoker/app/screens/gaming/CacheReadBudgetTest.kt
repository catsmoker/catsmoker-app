package com.catsmoker.app.screens.gaming

import com.catsmoker.app.screens.gaming.cache.CacheReadBudget
import com.catsmoker.app.screens.gaming.apps.SuspendSweep
import com.catsmoker.app.screens.gaming.graphics.GameInterventions
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CacheReadBudgetTest {
    @Test fun totalByteBudgetIsSharedAcrossFiles() {
        val budget = CacheReadBudget(100, 1000, { 0 }, { true }, {})
        assertEquals(60, budget.read(ByteArrayInputStream(ByteArray(60)), 60).toInt())
        assertEquals(40, budget.read(ByteArrayInputStream(ByteArray(80)), 80).toInt())
        assertEquals("byte_budget", budget.stopReason)
        assertEquals(0, budget.read(ByteArrayInputStream(ByteArray(80)), 80).toInt())
    }

    @Test fun deadlineAndPressurePreventAnyRead() {
        val expired = CacheReadBudget(100, 10, { 10 }, { true }, {})
        assertEquals(0L, expired.read(ByteArrayInputStream(ByteArray(100)), 100))
        assertEquals("time_budget", expired.stopReason)
        val pressure = CacheReadBudget(100, 10, { 0 }, { false }, {})
        assertEquals(0L, pressure.read(ByteArrayInputStream(ByteArray(100)), 100))
        assertEquals("memory_pressure", pressure.stopReason)
    }

    @Test fun exactPrefixLimitIsReportedWithoutOpeningAnotherFile() {
        val budget = CacheReadBudget(100, 1000, { 0 }, { true }, {})
        assertEquals(100L, budget.read(ByteArrayInputStream(ByteArray(100)), 100))
        assertEquals("byte_budget", budget.stopReason)
        assertFalse(budget.canStartIo())
    }

    @Test fun pressureBetweenCandidatesStopsBeforeTheNextOpen() {
        var memorySafe = true
        val budget = CacheReadBudget(100, 10, { 0 }, { memorySafe }, {})
        assertTrue(budget.canStartIo())
        assertEquals(20L, budget.read(ByteArrayInputStream(ByteArray(20)), 20))
        memorySafe = false
        assertFalse(budget.canStartIo())
        assertEquals("memory_pressure", budget.stopReason)
    }

    @Test(expected = CancellationException::class)
    fun cancellationPropagatesBeforeIo() {
        CacheReadBudget(100, 10, { 0 }, { true }, { throw CancellationException() })
            .read(ByteArrayInputStream(ByteArray(100)), 100)
    }

    @Test fun emptyFileDoesNotClaimReadBytes() {
        val budget = CacheReadBudget(100, 10, { 0 }, { true }, {})
        assertEquals(0L, budget.read(ByteArrayInputStream(byteArrayOf()), 100))
    }

    @Test fun cacheOptionsSurviveRecreationAndAreBoundedOnDecode() {
        val settings = GameSettings(cachePreload = true, cacheBudgetMiB = 4096,
            cacheDocuments = listOf("content://assets/one", "/data/private", "https://example.com"))
        val decoded = GameSettings.decode(settings.encode())
        assertTrue(decoded.cachePreload)
        assertEquals(64, decoded.cacheBudgetMiB)
        assertEquals(listOf("content://assets/one"), decoded.cacheDocuments)
        assertFalse(GameSettings.decode("""{"version":1}""").cachePreload)
    }

    @Test fun explicitExtraPicksCannotFreezeAutomationOrMappers() {
        assertEquals(emptyList<String>(), SuspendSweep.resolveTargets(
            SuspendSweep.companions, SuspendSweep.companions, emptySet(), "com.game", "com.catsmoker.app"))
    }

    @Test fun renderScalingDoesNotInstallFpsCaps() {
        val value = GameInterventions.scaleOnlyValue(0.8f)!!
        assertTrue(value.contains("downscaleFactor=0.8"))
        assertFalse(value.contains("fps"))
        assertNull(GameInterventions.scaleOnlyValue(Float.NaN))
    }
}
