package com.catsmoker.app.features.gamingtools.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for the single-package compile skip decision.
 *
 * Mirrors the sweep's own rules: forcing compiles everything; an app already at the requested
 * filter is done; a never-opened app at `verify` has no runtime profile for `speed-profile`
 * to work with, so there is nothing to do yet. Anything else — including an unreadable
 * status — compiles, because asking the platform is the only way to find out.
 */
class ShouldCompilePackageTest {

    @Test
    fun forceCompilesEverything() {
        assertTrue(shouldCompilePackage("speed-profile", "speed-profile", true))
        assertTrue(shouldCompilePackage("verify", "speed-profile", true))
        assertTrue(shouldCompilePackage(null, "speed-profile", true))
    }

    @Test
    fun alreadyAtFilterIsDone() {
        assertFalse(shouldCompilePackage("speed-profile", "speed-profile", false))
    }

    @Test
    fun neverOpenedAppHasNothingForSpeedProfile() {
        assertFalse(shouldCompilePackage("verify", "speed-profile", false))
    }

    @Test
    fun otherStatusesCompile() {
        assertTrue(shouldCompilePackage("speed", "speed-profile", false))
        assertTrue(shouldCompilePackage(null, "speed-profile", false))
        assertTrue(shouldCompilePackage("", "speed-profile", false))
    }

    @Test
    fun verifyCompilesUnderOtherModes() {
        // The "no profile yet" skip is specific to speed-profile; a full-speed compile has
        // material to work with regardless.
        assertTrue(shouldCompilePackage("verify", "speed", false))
    }
}
