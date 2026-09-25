package com.catsmoker.app.shared.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for the shared installed-app query predicates (Phase 1 / C4).
 *
 * One base query + feature-specific predicates. Pure functions stay JVM-testable;
 * PackageManager-bound enumeration lives beside them and reuses the same rules.
 */
class InstalledAppQueryTest {

    private fun entry(pkg: String, flags: Int = 0, uid: Int = 10100, internet: Boolean = true) =
        InstalledAppQuery.AppEntry(pkg, flags, uid, internet)

    @Test
    fun systemAppsAreNotUserApps() {
        assertFalse(InstalledAppQuery.isUserApp(android.content.pm.ApplicationInfo.FLAG_SYSTEM))
        assertFalse(
            InstalledAppQuery.isUserApp(android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
        )
        assertTrue(InstalledAppQuery.isUserApp(0))
    }

    @Test
    fun blockCandidatesExcludeSelfGamesSystemAndNoInternet() {
        val self = "com.catsmoker.app"
        val games = setOf("com.game.one")
        assertTrue(InstalledAppQuery.shouldBlockCandidate(entry("com.other"), self, games))
        assertFalse(InstalledAppQuery.shouldBlockCandidate(entry(self), self, games))
        assertFalse(InstalledAppQuery.shouldBlockCandidate(entry("com.game.one"), self, games))
        assertFalse(
            InstalledAppQuery.shouldBlockCandidate(
                entry("com.sys", android.content.pm.ApplicationInfo.FLAG_SYSTEM), self, games
            )
        )
        assertFalse(
            InstalledAppQuery.shouldBlockCandidate(entry("com.noinet", internet = false), self, games)
        )
    }

    @Test
    fun filtersPackageNamesDistinct() {
        val entries = listOf(entry("com.a"), entry("com.a"), entry("com.b", internet = false))
        assertEquals(
            listOf("com.a"),
            InstalledAppQuery.filterBlockPackageNames(entries, "com.catsmoker.app", emptySet())
        )
    }

    @Test
    fun restrictableUidsEnforceMinUid() {
        val entries = listOf(entry("com.a", uid = 9999), entry("com.b", uid = 10100))
        assertEquals(
            setOf(10100),
            InstalledAppQuery.filterRestrictableUids(entries, "com.catsmoker.app", emptySet(), 10000)
        )
    }
}
