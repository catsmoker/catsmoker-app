package com.catsmoker.app.system.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Host boundary of the in-app native bridge (M152 NDK spike).
 *
 * The `.so` ships inside the APK for device ABIs only — a JVM unit-test host has no such
 * library, so the bridge must report itself unavailable (false/null) rather than throwing.
 * The positive half (loads + version string on arm64) is proven on the lab device via the
 * Diagnostics screen row, not here.
 */
class NativeBridgeTest {

    @Test
    fun bridgeReportsUnavailableOnJvm() {
        assertFalse(NativeBridge.isAvailable())
    }

    @Test
    fun versionIsNullWhenUnavailable() {
        assertNull(NativeBridge.version())
    }

    @Test
    fun unityProbeReportsNoLibraryOnJvm() {
        // No `.so` on the host, so the probe must answer "no library" (-4), never throw.
        assertEquals(NativeBridge.NO_LIBRARY, NativeBridge.unityTargetFrameRate())
    }

    @Test
    fun unityUnlockReportsNoLibraryOnJvm() {
        // Same host boundary for the write half: no library, no write, no throw.
        assertEquals(NativeBridge.NO_LIBRARY, NativeBridge.unitySetTargetFrameRate(60))
    }
}
