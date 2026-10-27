package com.catsmoker.app.features.gamingtools.engine

/**
 * The `cpu_input_boost` and `/dev/stune` half of SmartPack Kernel Manager's boost vocabulary, as
 * data and raise-only rules (`utils/kernel/cpu/boost/CPUInputBoost.java` and `StuneBoost.java`,
 * read before this file was written).
 *
 * The reference is a general kernel manager: every knob is a free-form text field. This object is
 * deliberately narrower. The engine only ever turns a declared-off switch on, raises a frequency
 * toward a policy's own hardware maximum, and raises the dynamic stune boost toward the top-app
 * boost the device itself already carries. Three things follow:
 *
 *  - **A boost that never happens is not written.** `enabled = 1` with a zero/missing duration is
 *    a switch whose effect is invisible, so the whole family is gated on a positive window the
 *    device declares (`ib_duration_ms`, `input_boost_duration` or `input_boost_duration_ms`).
 *  - **The duration is never written.** Its length is the kernel author's tuning; a "gaming"
 *    duration is an invented constant, so those nodes are read-only gates.
 *  - **The static top-app boost is not written.** It is the device's own value and the only
 *    same-unit reference for `dynamic_stune_boost`; raising the dynamic half to it is derived,
 *    raising the static half to anything else would be a guess. The `prefer_idle` and
 *    `sched_boost_enabled` flags are enable-only, so they can never turn a boost off.
 *
 * Not written at all, with the reason: `wake_boost_duration` (a duration, same rule as above),
 * `input_boost_freq_lp`/`input_boost_freq_hp` (vendor-specific per-cluster spellings with no
 * declared ceiling), `remove_input_boost_freq_*` (those *lower* a boost), and
 * `schedtune.colocate` / `sched_boost_no_override` (mechanism toggles with no magnitude).
 * Behavior (presence gates, snapshot priors, read-back, restore) lives in [GamingEngine].
 */
object CpuInputBoostNodes {

    /**
     * The two parent layouts the reference recognises, in its own order: CAF's
     * `/sys/kernel/cpu_input_boost` first, then the module parameter directory. The engine picks
     * the first one whose `enabled` node actually reads.
     */
    val PARENT_CANDIDATES: List<String> = listOf(
        "/sys/kernel/cpu_input_boost",
        "/sys/module/cpu_input_boost/parameters"
    )

    /**
     * The three layouts SmartPack probes for `dynamic_stune_boost`, in its own order: the
     * `cpu_boost` module, the `cpu_input_boost` module, then CAF's kernel directory. This node can
     * live somewhere other than the boost switch's own parent, so the engine resolves it
     * separately.
     */
    val STUNE_BOOST_PARENTS: List<String> = listOf(
        "/sys/module/cpu_boost/parameters",
        "/sys/module/cpu_input_boost/parameters",
        "/sys/kernel/cpu_input_boost"
    )

    /** `enabled`: the boost's master switch, enable-if-zero only. */
    const val ENABLED = "enabled"

    /** `ib_freqs`: per-CPU `cpu:freq` boost frequencies (the same vocabulary as `cpu_boost`). */
    const val IB_FREQS = "ib_freqs"

    /** `dynamic_stune_boost`: the transient schedtune boost applied while input boosting. */
    const val DYNAMIC_STUNE_BOOST = "dynamic_stune_boost"

    /**
     * The duration spellings the reference probes, in its own order. Read-only: a positive value
     * is the gate that says the boost really happens.
     */
    val DURATION_NAMES: List<String> = listOf(
        "ib_duration_ms",
        "input_boost_duration",
        "input_boost_duration_ms"
    )

    /** `/dev/stune/top-app/schedtune.boost` — the device's own static top-app boost. */
    const val TOP_APP_STUNE_BOOST = "/dev/stune/top-app/schedtune.boost"

    /** `/dev/stune/top-app/schedtune.prefer_idle` — enable-only wakeup placement hint. */
    const val TOP_APP_PREFER_IDLE = "/dev/stune/top-app/schedtune.prefer_idle"

    /** `/dev/stune/top-app/schedtune.sched_boost_enabled` — enable-only static-boost switch. */
    const val TOP_APP_SCHED_BOOST_ENABLED = "/dev/stune/top-app/schedtune.sched_boost_enabled"

    /**
     * The first duration the device declares as a positive number of milliseconds, or null when
     * every candidate is absent, zero or unparseable. A zero is a real answer ("no window"), not
     * a missing one — both make the boost a no-op.
     */
    fun boostWindowMs(durations: Map<String, String?>): Long? {
        for (name in DURATION_NAMES) {
            val value = durations[name]?.trim()?.toLongOrNull() ?: continue
            if (value > 0L) return value
        }
        return null
    }

    /**
     * The `dynamic_stune_boost` target: the device's own static top-app boost when that is
     * strictly greater than the dynamic value, else null.
     *
     * Both values share the schedtune unit by construction — they are the same knob's static and
     * dynamic halves — so copying one to the other is a device-derived target, not a magic
     * number. A zero static boost means top-app is not boosted at all (nothing to copy), and a
     * negative value is refused rather than lowered into.
     */
    fun stuneBoostTarget(dynamic: String?, topApp: String?): String? {
        val dynamicValue = dynamic?.trim()?.toLongOrNull() ?: return null
        val topAppValue = topApp?.trim()?.toLongOrNull() ?: return null
        if (dynamicValue < 0L || topAppValue <= 0L) return null
        return if (topAppValue > dynamicValue) topAppValue.toString() else null
    }

    /**
     * The desired dynamic stune boost for an idempotent re-apply (recovery): the larger of the
     * current dynamic value and the device's own static top-app boost. Null when either side is
     * unreadable or the static boost is not positive — there is no declared magnitude to copy.
     */
    fun desiredStuneBoost(dynamic: String?, topApp: String?): String? {
        val dynamicValue = dynamic?.trim()?.toLongOrNull() ?: return null
        val topAppValue = topApp?.trim()?.toLongOrNull() ?: return null
        if (dynamicValue < 0L || topAppValue <= 0L) return null
        return maxOf(dynamicValue, topAppValue).toString()
    }

    /**
     * The desired state of an enable-only flag for an idempotent re-apply (recovery): `1`
     * whenever the node reads a real `0` or `1`, else null. [SnapdragonKernelNodes.enableIfZero]
     * is the change-needed form; this one keeps an already-on flag reportable after a process
     * restart without ever writing over a value that is not a switch.
     */
    fun desiredFlag(current: String?): String? =
        current?.trim()?.takeIf { it == "0" || it == "1" }?.let { "1" }

    /**
     * The file names this family can ever write. The set exists so the deliberate non-writes can
     * be asserted: `CpuInputBoostNodesTest` fails if any name starts naming a GPU, thermal,
     * voltage or screen control.
     */
    fun writableNodeNames(): List<String> = listOf(
        ENABLED,
        IB_FREQS,
        DYNAMIC_STUNE_BOOST,
        "schedtune.prefer_idle",
        "schedtune.sched_boost_enabled"
    )
}
