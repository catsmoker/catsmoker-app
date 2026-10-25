package com.catsmoker.app.features.spoofdevice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the provider naming on the Device Spoofing status card.
 *
 * The user-visible failure this guards against is claiming "Magisk" from a leftover manager
 * APK (or from a single directory) when KernelSU is the provider actually granting root. A
 * package alone is never evidence; a version answer or the package-plus-private-directory
 * pair is.
 */
class RootProviderDetectorTest {

    @Test
    fun versionAnswerNamesTheProvider() {
        assertEquals(
            setOf(RootProvider.MAGISK),
            RootProviderDetector.detect(RootProviderSignals(magiskVersion = true))
        )
        assertEquals(
            setOf(RootProvider.KERNELSU),
            RootProviderDetector.detect(RootProviderSignals(kernelsuVersion = true))
        )
        assertEquals(
            setOf(RootProvider.APATCH),
            RootProviderDetector.detect(RootProviderSignals(apatchVersion = true))
        )
    }

    @Test
    fun managerPackageAloneIsNotAProviderClaim() {
        val signals = RootProviderSignals(
            magiskManager = true,
            kernelsuManager = true,
            apatchManager = true
        )
        assertTrue(RootProviderDetector.detect(signals).isEmpty())
    }

    @Test
    fun managerAndPrivateDirectoryProveHiddenProviderInstallations() {
        assertEquals(
            setOf(RootProvider.MAGISK),
            RootProviderDetector.detect(
                RootProviderSignals(magiskManager = true, magiskDir = true)
            )
        )
        assertEquals(
            setOf(RootProvider.KERNELSU),
            RootProviderDetector.detect(
                RootProviderSignals(kernelsuManager = true, kernelsuDir = true)
            )
        )
        assertEquals(
            setOf(RootProvider.APATCH),
            RootProviderDetector.detect(
                RootProviderSignals(apatchManager = true, apatchDir = true)
            )
        )
    }

    @Test
    fun directoryAloneIsNotEnough() {
        assertFalse(
            RootProviderDetector.detect(
                RootProviderSignals(
                    magiskDir = true,
                    kernelsuDir = true,
                    apatchDir = true
                )
            ).isNotEmpty()
        )
    }

    @Test
    fun multipleSignalsReportEveryProvider() {
        val detected = RootProviderDetector.detect(
            RootProviderSignals(
                magiskVersion = true,
                kernelsuVersion = true,
                kernelsuManager = true,
                kernelsuDir = true
            )
        )
        assertEquals(setOf(RootProvider.MAGISK, RootProvider.KERNELSU), detected)
    }
}
