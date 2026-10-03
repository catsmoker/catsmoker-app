package com.catsmoker.app.shared.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the assignment matcher: exact package names win first, then `*` wildcard patterns
 * (longest = most specific wins), and the system guard never routes protected packages to
 * a pattern. Hand-written pattern keys keep working without any store migration — keys
 * without a `*` behave exactly as before.
 */
class AssignmentMatcherTest {

    @Test
    fun exactBeatsWildcard() {
        val assignments = mapOf(
            "com.pubg.*" to "profile-family",
            "com.pubg.krmobile" to "profile-exact"
        )
        assertEquals("profile-exact", AssignmentMatcher.match(assignments, "com.pubg.krmobile"))
        assertEquals("profile-family", AssignmentMatcher.match(assignments, "com.pubg.imobile"))
    }

    @Test
    fun longestPatternWins() {
        val assignments = mapOf(
            "com.pubg.*" to "profile-short",
            "com.pubg.kr*" to "profile-long"
        )
        assertEquals("profile-long", AssignmentMatcher.match(assignments, "com.pubg.krmobile"))
    }

    @Test
    fun prefixSuffixAndInfixShapes() {
        val assignments = mapOf(
            "*.vng" to "profile-suffix",
            "com.rekoo.*" to "profile-prefix"
        )
        assertEquals("profile-suffix", AssignmentMatcher.match(assignments, "com.pubg.vng"))
        assertEquals("profile-prefix", AssignmentMatcher.match(assignments, "com.rekoo.pubgmhd"))
        assertNull(AssignmentMatcher.match(assignments, "com.pubg.krmobile"))
    }

    @Test
    fun systemGuardNeverMatchesProtected() {
        val assignments = mapOf("*" to "profile-all")
        assertNull(AssignmentMatcher.match(assignments, "android"))
        assertNull(AssignmentMatcher.match(assignments, "com.android.systemui"))
    }

    @Test
    fun noMatchWithoutPatterns() {
        assertNull(AssignmentMatcher.match(mapOf("com.a" to "p"), "com.b"))
        assertNull(AssignmentMatcher.match(emptyMap(), "com.a"))
    }
}
