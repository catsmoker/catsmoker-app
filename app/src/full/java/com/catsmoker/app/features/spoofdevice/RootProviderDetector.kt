package com.catsmoker.app.features.spoofdevice

/**
 * The root providers this app knows how to name.
 *
 * Root access itself is answered by [com.catsmoker.app.system.shell.ShellRunner.isRootAvailable];
 * this enum only answers *which* provider granted it. The distinction matters on the Device
 * Spoofing screen: "rooted" is not the same fact as "Magisk is installed", and a hidden Magisk
 * stub is not visible in the package list at all.
 */
enum class RootProvider {
    MAGISK,
    KERNELSU,
    APATCH
}

/**
 * Read-only signals one privileged probe can collect. Every field means "the probe answered
 * yes", never "the package might exist": detection must not turn a lone APK into a claim that
 * its root implementation is active.
 */
data class RootProviderSignals(
    /** `magisk -V` (or its known binary path) answered with a version. */
    val magiskVersion: Boolean = false,
    /** `/data/adb/magisk` exists, which only a Magisk installation creates. */
    val magiskDir: Boolean = false,
    /** `com.topjohnwu.magisk` is visible in the package list. */
    val magiskManager: Boolean = false,
    /** `ksud -V` answered with a version. */
    val kernelsuVersion: Boolean = false,
    /** `/data/adb/ksud` or KernelSU's module directory exists. */
    val kernelsuDir: Boolean = false,
    /** `me.weishu.kernelsu` is visible in the package list. */
    val kernelsuManager: Boolean = false,
    /** `apd -V` answered with a version. */
    val apatchVersion: Boolean = false,
    /** `/data/adb/apd` or APatch's data directory exists. */
    val apatchDir: Boolean = false,
    /** `me.bmax.apatch` is visible in the package list. */
    val apatchManager: Boolean = false
)

/**
 * Names the active root provider without guessing from a single file, package or version.
 *
 * A provider is reported only when its private data directory exists *and* at least one
 * independent signal agrees: its version command answered or its manager package is present.
 * The directory alone is not enough (a leftover install), a manager APK alone is not enough
 * (a user may keep the old manager after switching providers), and a version command alone is
 * not enough either — a residual `magisk -V` binary answers on a KernelSU-rooted device and
 * used to make the screen claim both providers were active. Requiring the directory for the
 * command keeps the hidden-manager case working, since every provider keeps its directory
 * regardless of whether its manager is hidden.
 *
 * Empty means "root answered, but no known provider could be named" — the caller must say
 * that instead of inventing one.
 */
object RootProviderDetector {

    fun detect(signals: RootProviderSignals): Set<RootProvider> = buildSet {
        if (signals.magiskDir && (signals.magiskVersion || signals.magiskManager)) {
            add(RootProvider.MAGISK)
        }
        if (signals.kernelsuDir && (signals.kernelsuVersion || signals.kernelsuManager)) {
            add(RootProvider.KERNELSU)
        }
        if (signals.apatchDir && (signals.apatchVersion || signals.apatchManager)) {
            add(RootProvider.APATCH)
        }
    }
}
