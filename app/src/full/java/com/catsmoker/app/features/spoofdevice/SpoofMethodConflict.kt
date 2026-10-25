package com.catsmoker.app.features.spoofdevice

/**
 * Device-spoofing method exclusivity: the Xposed framework path (LSPosed or
 * Vector, per-app hooks) and the generated Magisk module (device-wide
 * `system.prop` at boot) rewrite the same device identity through different
 * channels, so both being armed at once is a conflict.
 *
 * Pure decisions only — detection feeds them (Vector's manager row plus load
 * evidence for Xposed, `/data/adb/modules` presence for Magisk). This file never
 * touches the device; [SpoofDeviceViewModel] performs the one supported change
 * when [shouldDisableVector] is true, through Vector's own
 * `vector-cli modules disable <pkg>` command, and re-reads the manager row to
 * verify it. Confirmation still gates user-initiated assignments.
 *
 * Status honesty: a Magisk state that cannot be read (no privilege) reports
 * [MagiskModuleStatus.UNKNOWN], never Absent — absence is only reported after
 * a privileged read found nothing. An UNKNOWN module never gates anything and
 * never raises the conflict banner.
 */
object SpoofMethodConflict {

    /** The generated Magisk module's on-device state, as last probed. */
    enum class MagiskModuleStatus {
        /** Module directory present without a `disable` flag — may rewrite identity at boot. */
        INSTALLED,
        /** Directory present but Magisk-disabled — inert until re-enabled. */
        DISABLED,
        /** Privileged read answered and the directory is not there. */
        ABSENT,
        /** No privilege to read — honestly unknown, never treated as absent. */
        UNKNOWN
    }

    /**
     * Whether both methods may rewrite identity right now: an installed Magisk module plus a
     * Vector integration that is enabled in the manager.
     *
     * The manager row ([vectorModuleEnabled]) decides, not the heartbeat/loaded proof. A
     * disabled Vector row must not keep raising the conflict banner just because the target
     * process that was hooked before the disable is still alive; keying on that old heartbeat
     * made every refresh disable an already-disabled module forever. A null row is unknown,
     * so a genuinely observed load still raises the banner — but [shouldDisableVector] stays
     * false until the manager state can be read.
     */
    fun isConflict(
        vectorModuleEnabled: Boolean?,
        xposedLoaded: Boolean,
        magisk: MagiskModuleStatus
    ): Boolean = magisk == MagiskModuleStatus.INSTALLED &&
        (vectorModuleEnabled == true || (vectorModuleEnabled == null && xposedLoaded))

    /**
     * Whether the app may deactivate only its own Vector integration automatically.
     *
     * This is intentionally narrower than [isConflict]: the supported
     * `vector-cli modules disable <pkg>` path needs a positive manager row to target, and
     * disabling on an unknown row would be an unverified change to the user's framework.
     * The generated Magisk module must be installed; a disabled or absent Catsmoker module
     * never causes a change to Vector.
     */
    fun shouldDisableVector(
        vectorModuleEnabled: Boolean?,
        magisk: MagiskModuleStatus
    ): Boolean = magisk == MagiskModuleStatus.INSTALLED && vectorModuleEnabled == true

    /**
     * Whether assigning a profile needs explicit confirmation first: only a
     * fresh assignment (never a removal) against an installed Magisk module.
     */
    fun needsAssignConfirm(profileId: String?, magisk: MagiskModuleStatus): Boolean =
        profileId != null && magisk == MagiskModuleStatus.INSTALLED

    /**
     * Whether generating the Magisk ZIP needs explicit confirmation first:
     * whenever the Xposed side is live (heartbeat) or merely claimed
     * (assignments or ladders that a reboot would arm).
     */
    fun needsGenerateConfirm(
        moduleActive: Boolean,
        hasAssignments: Boolean,
        hasLadders: Boolean
    ): Boolean = moduleActive || hasAssignments || hasLadders

    /**
     * Maps a privileged directory probe to the honest module state. Without
     * privilege nothing can be said; a present directory with a `disable`
     * flag is inert rather than active.
     */
    fun parseMagiskModuleStatus(
        hasPrivilege: Boolean,
        dirExists: Boolean,
        disableExists: Boolean
    ): MagiskModuleStatus = when {
        !hasPrivilege -> MagiskModuleStatus.UNKNOWN
        !dirExists -> MagiskModuleStatus.ABSENT
        disableExists -> MagiskModuleStatus.DISABLED
        else -> MagiskModuleStatus.INSTALLED
    }
}
