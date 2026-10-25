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
 * Names the active root provider without guessing from a single file or package.
 *
 * A provider is reported when either its version command answered or both its manager package
 * and its private data directory are present. The second rule exists because hidden Magisk
 * stubs (`com.topjohnwu.magisk` replaced by a random package) and non-PATH `ksud`/`apd`
 * binaries can hide the command while the manager and its directory still prove the install.
 * An APK alone is deliberately *not* enough: a user may keep a root-manager APK installed
 * after switching providers, and naming that stale package would misattribute root.
 *
 * Empty means "root answered, but no known provider could be named" — the caller must say
 * that instead of inventing one.
 */
object RootProviderDetector {

    fun detect(signals: RootProviderSignals): Set<RootProvider> = buildSet {
        if (signals.magiskVersion || (signals.magiskManager && signals.magiskDir)) {
            add(RootProvider.MAGISK)
        }
        if (signals.kernelsuVersion || (signals.kernelsuManager && signals.kernelsuDir)) {
            add(RootProvider.KERNELSU)
        }
        if (signals.apatchVersion || (signals.apatchManager && signals.apatchDir)) {
            add(RootProvider.APATCH)
        }
    }
}
