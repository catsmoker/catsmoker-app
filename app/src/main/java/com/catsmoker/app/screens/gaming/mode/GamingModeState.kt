package com.catsmoker.app.screens.gaming.mode

import androidx.annotation.StringRes

sealed class GamingModeState {
    object Idle : GamingModeState()

    /** Startup reconciliation is still reading the persisted session and the device. */
    object Checking : GamingModeState()

    data class Enabling(val progress: Float = 0f, val statusText: String = "Preparing…") : GamingModeState()
    object Active : GamingModeState()
    object Disabling : GamingModeState()

    /**
     * A session was started but its process is gone: the levers may still be applied and some
     * apps may still be suspended.
     *
     * This is deliberately not [Active]. After process death or a reboot the app cannot verify
     * that every setting is still applied, and claiming "active" from a persisted Boolean is how
     * the UI ended up lying while frozen apps were left behind. It is not [Idle] either: the
     * snapshot and the suspension record still exist, so the power button must revert rather than
     * start a second session, and the recovery control is offered until the record is clear.
     *
     * @param suspendedPackages packages the record still marks as possibly suspended.
     */
    data class Interrupted(val suspendedPackages: Int) : GamingModeState()

    data class Error(val message: String) : GamingModeState()
}

/**
 * The recovery evidence shown next to the Gaming Mode switch.
 *
 * @param isChecking true while startup reconciliation is still reading the device.
 * @param pendingPackages packages recorded as suspended whose wake-up is not confirmed. When
 *   [verified] is false this is the record alone, because the device would not answer — it is
 *   then evidence of what *we did*, not of what is still frozen.
 * @param pendingAppOps packages whose background app-op is still set to `ignore` by a session.
 * @param verified whether the package manager answered with a per-package suspension list.
 */
data class GamingModeRecovery(
    val isChecking: Boolean = false,
    val pendingPackages: List<String> = emptyList(),
    val pendingAppOps: List<String> = emptyList(),
    val verified: Boolean = true
) {
    /** True when a session left something behind that the user should be able to restore. */
    val isNeeded: Boolean get() = pendingPackages.isNotEmpty() || pendingAppOps.isNotEmpty()

    /** Distinct apps needing attention, for the card's one-line caption. */
    val pendingCount: Int get() = (pendingPackages + pendingAppOps).distinct().size
}

/**
 * What one Restore Apps attempt actually achieved.
 *
 * Counts, not just a Boolean, because this action exists for the case where the device refused
 * something: reporting "restored" after half the apps woke is the same lie the old deactivation
 * told. [verified] is false when the package manager would not answer, and the caller must then
 * present the untouched record as unverified rather than as a confirmed failure.
 */
data class GamingModeRestoreResult(
    val restoredPackages: Int = 0,
    val stillSuspended: Int = 0,
    val restoredAppOps: Int = 0,
    val failedAppOps: Int = 0,
    val verified: Boolean = true,
    /** True when Gaming Mode is running; nothing was touched. */
    val refusedWhileActive: Boolean = false
) {
    /** Some device work was attempted (not refused, and something was pending). */
    val attempted: Boolean get() = !refusedWhileActive

    /** Everything the record held is confirmed restored. */
    val isComplete: Boolean get() = attempted && stillSuspended == 0 && failedAppOps == 0
}

/**
 * One entry of the Gaming Mode refusal note, in a form that survives a language switch.
 *
 * The engine (and the ViewModel holding its report) lives on the application context, whose
 * locale is frozen at process start, and both survive the activity `recreate()` a language
 * change performs — a `context.getString` resolved here would stay in the previous language no
 * matter what the user picks (the PUBG-profile-label bug of 2026-09-16 was the same shape).
 * So the engine carries the string-resource ID plus its format arguments, and the card resolves
 * them with `stringResource` at composition, which always follows the current language.
 * [Raw] is the escape hatch for text that is not ours to translate: the device's own refusal
 * words (shell output, exception messages), carried verbatim per the honesty rules.
 */
sealed interface GamingModeNotice {
    data class Res(@StringRes val resId: Int, val args: List<Any> = emptyList()) : GamingModeNotice
    data class Raw(val text: String) : GamingModeNotice
}

/**
 * What activation actually managed to apply, verified by reading each value back.
 *
 * The card used to show four fixed strings whether or not anything landed. Every field here is
 * either a measured fact or absent, and [unavailable] carries the human-readable reason for each
 * optimization the device refused — that is what gets shown instead of a slogan.
 */
data class GamingModeReport(
    val fixedPerformance: Boolean = false,
    /**
     * Whether Qualcomm's vendor GPU mode was confirmed at `performance` (with
     * `vendor.gfx.low_quality` at `1`) by reading both properties back.
     *
     * The reference project ships a native root daemon for exactly this payload
     * (`reference/spoofdevice/GameUnlocker-main/cpp/controller.cpp`, read in full: a daemon on an
     * abstract UNIX socket that sets the two properties while a whitelisted game process is
     * connected and restores them when the last one exits). The daemon itself is not portable
     * here — opening the connection from inside the game needs Zygisk injection — but its payload
     * is, and Gaming Mode is this app's equivalent lifecycle: applied on activation, restored from
     * the snapshot on deactivation. The daemon logs its `setprop` and never reads the property
     * back; this reports the device's answer instead. Its `isQualcomm()` `ro.hardware` prefix
     * table is deliberately not ported — the property's own existence is the gate, and a curated
     * chipset-name list goes stale the same way a curated module-id list does.
     *
     * null when the device carries neither property (every non-Qualcomm SoC) — not applicable
     * rather than failed, so the report row is omitted.
     */
    val gpuPerformanceMode: Boolean? = null,
    /**
     * Whether Qualcomm's `debug.vendor.qti.game.fps` hint was confirmed at the panel's measured
     * peak by reading the property back.
     *
     * The reference sets both this and its `persist.` twin to a hard-coded 120 at boot in
     * `post-fs-data.sh` (`reference/spoofdevice/GameUnlocker-main/common/post-fs-data.sh`, read
     * in full — those two `setprop` lines are that file's whole payload). Three deliberate
     * divergences:
     *  - **Session, not boot.** Gaming Mode is this app's lifecycle — the hint is applied on
     *    activation and the prior value restored from the snapshot on deactivation, the same
     *    contract as every other switch here.
     *  - **The panel's measured peak, not a hard-coded 120.** The same `maxHz` the refresh-rate
     *    lock verified is what gets hinted; 120 on a 90 Hz panel would be a number the hardware
     *    cannot honor.
     *  - **The `persist.` twin is deliberately not set.** `setprop` cannot delete a property, so
     *    a `persist.vendor.qti.game.fps` that did not exist would become a permanent,
     *    reboot-surviving, device-wide change that deactivation could overwrite but never
     *    remove — exactly the class of change the Magisk channel's narrowing rejected. The
     *    `debug.` prop is reversible in the only way Android offers: a reboot clears the debug
     *    property area. Until then, a hint that did not exist before stays hinting the panel's
     *    own peak — bounded, and stated here rather than hidden.
     *
     * null when the device's own property dump carries no `ro.vendor.qti.*` property (every
     * non-Qualcomm SoC) — not applicable, so the report row is omitted. The gate is the dump
     * itself rather than the reference's `isQualcomm()` `ro.hardware` prefix table, for the same
     * reason [gpuPerformanceMode] gives: a curated chipset list goes stale.
     */
    val qtiGameFps: Boolean? = null,
    /**
     * Whether MediaTek's game-mode knobs (GED boost parameters + PPM policy states from
     * HunterX-Reborn-II's service payload) were confirmed by read-back.
     *
     * null when the device carries neither `/sys/module/ged/parameters` nor `/proc/ppm`
     * (every non-MediaTek SoC, and MediaTek builds without the nodes) — not applicable
     * rather than failed, so the report row is omitted. Per-node presence gates mean a
     * partial table still applies what exists; the value is true only when every attempted
     * node read back at its target.
     */
    val mediaTekGameMode: Boolean? = null,
    /**
     * Whether the Snapdragon widen-only channel was confirmed by read-back.
     *
     * The channel has two halves, both from HunterX-Reborn-II's payload: [SnapdragonCpuWiden]
     * (policy ceilings raised to each policy's own hardware max + game-visible cpusets unioned
     * toward the full set) and [SnapdragonKernelNodes] (schedutil up rate limiter removed and
     * hispeed jump raised, `msm_performance` touch boost enabled and declared CPU ceilings raised,
     * `core_ctl` floors/ceilings raised to each cluster's real core count, `cpu_boost` input boost
     * enabled and its per-CPU frequencies raised toward each policy's max).
     *
     * null when the device has no widenable node (no policy headroom, no narrow game cpuset and
     * none of the node families above — the normal case on non-Snapdragon SoCs and on Snapdragons
     * already at their maxima) — not applicable rather than failed, so the report row is omitted.
     * Like every other switch here this applies only inside an explicit Gaming Mode activation and
     * restores the snapshot priors on deactivation; a refused node is reported, never hidden, and
     * one family's refusal never blocks the nodes that did land.
     */
    val snapdragonCpuMode: Boolean? = null,
    /**
     * Whether the `cpu_input_boost` + `/dev/stune` family was confirmed by read-back: the boost
     * master switch (enable-only), the per-CPU `ib_freqs` body (raised toward each policy's
     * hardware max, gated on a declared boost window) and the dynamic stune boost (raised toward
     * the device's own static top-app boost).
     *
     * null when the kernel carries no such node or nothing had headroom — not applicable, so the
     * row is omitted rather than shown refused. See [CpuInputBoostNodes] for the rules and the
     * deliberate non-writes.
     */
    val cpuInputBoost: Boolean? = null,
    /**
     * Whether every cpufreq policy that declared `performance` in its own
     * `scaling_available_governors` was confirmed running it by read-back.
     *
     * null when no policy needed the switch (or this kernel does not offer `performance`) — not
     * applicable. The governor is the one universal maximum-frequency governor; vendor names like
     * `turbo` are never guessed at (see [CpuGovernorMode]).
     */
    val cpuGovernor: Boolean? = null,
    /**
     * The Spectrum profile confirmed by read-back: 3 = gaming, or 2 when the kernel clamped the
     * gaming request to performance. null when the device declares no usable Spectrum interface
     * or the write was refused (the refusal is in [unavailable]).
     */
    val spectrumProfile: Int? = null,
    /**
     * Whether raising the `/data` block device's `read_ahead_kb` to its queue's own
     * `max_sectors_kb` was confirmed by read-back.
     *
     * null when nothing had headroom or the `/data` device could not be resolved — not
     * applicable, so the row is omitted. See [StorageTuning] for why the scheduler is not touched.
     */
    val storageTuning: Boolean? = null,
    /** The read-ahead the device reported after the write, in KB, or null when not applied. */
    val storageReadAheadKb: Int? = null,
    /**
     * Whether raising the VM watermarks (`min_free_kbytes` toward 1 % of `MemTotal`,
     * `extra_free_kbytes` toward the device's own base reserve) was confirmed by read-back.
     *
     * null when the device already reserved at or above both targets, or either fact was
     * unreadable — not applicable. See [MemoryTuning] for the excluded knobs.
     */
    val memoryTuning: Boolean? = null,
    /** The `min_free_kbytes` the device reported after the write, in KB, or null when not applied. */
    val memoryMinFreeKb: Int? = null,
    /** Refresh rate the panel was actually pinned to, or null when the ROM ignored the keys. */
    val lockedRefreshHz: Int? = null,
    val touchResponseBoost: Boolean = false,
    /**
     * The touch speed (`pointer_speed`) the read-back confirmed, or null when no speed was
     * chosen or the write was refused. Shown beside the touch row so a faster/slower pointer
     * never looks identical to stock.
     */
    val pointerSpeed: Int? = null,
    val suspendedPackages: Int = 0,
    val suspendFailures: Int = 0,
    val dndEngaged: Boolean = false,
    /** null when no game was targeted, so the UI can say "not applicable" instead of "failed". */
    val networkWhitelisted: Boolean? = null,
    /** `always_finish_activities` confirmed at 1 by a read-back. */
    val discardActivities: Boolean = false,
    /** `max_cached_processes=1` confirmed present in `activity_manager_constants`. */
    val processLimit: Boolean = false,
    /**
     * Metered-background data blocked for the apps that are not the game.
     *
     * null means "left alone deliberately" — the user's own Background Data Restriction switch was
     * already on before activation, so this run neither engaged nor owns it.
     */
    val backgroundDataRestricted: Boolean? = null,
    /**
     * Whether the `device_config game_overlay` intervention for the target game was confirmed by a
     * read-back.
     *
     * null when no game was targeted or the device is older than Android 12, where game
     * interventions do not exist — both are "not applicable", not "failed", and the report row is
     * omitted rather than shown as refused.
     */
    val gameInterventionApplied: Boolean? = null,
    /**
     * The render scale the confirmed intervention carries, or null when the entry is FPS-only
     * (or no intervention landed). Shown beside the frame-cap row so a downscaled game never
     * looks identical to a full-resolution one.
     */
    val gameInterventionDownscale: Float? = null,
    /**
     * Whether the second-layer notification suppression is armed.
     *
     * null means the user has not granted notification access, so
     * [GamingNotificationListener] is never bound — "not applicable", not "failed". true means
     * the listener is connected and cancelling; each individual cancellation is best-effort,
     * so the field reports the layer being armed rather than promising every notification was
     * swallowed.
     */
    val notificationSuppression: Boolean? = null,
    /**
     * Refused optimizations, as resource IDs resolved by the card at composition — never
     * pre-resolved strings (see [GamingModeNotice] for why resolving here would freeze the
     * previous language across a language switch).
     */
    val unavailable: List<GamingModeNotice> = emptyList()
)

/**
 * Whether the device accepted a fixed-performance-mode request, and what it said if it did not.
 *
 * The toggle used to set its own state from the request, so it read "on" on builds where the command
 * does not exist. [accepted] is now the framework's answer and [message] is the device's own text.
 */
data class FixedPerformanceOutcome(
    val accepted: Boolean,
    val message: String
)

