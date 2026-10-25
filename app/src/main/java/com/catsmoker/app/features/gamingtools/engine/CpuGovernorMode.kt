package com.catsmoker.app.features.gamingtools.engine

/**
 * The CPU-governor half of SmartPack Kernel Manager's `CPUFreq` vocabulary
 * (`utils/kernel/cpu/CPUFreq.java`, read before this file was written), narrowed to one rule:
 * switch a policy to the device's own `performance` governor when — and only when — that policy
 * lists it in `scaling_available_governors`.
 *
 * The reference exposes every governor a kernel ships and lets the user pick. This channel does
 * not: a "gaming" or "turbo" name is vendor vocabulary with unknown semantics, and choosing one
 * would be guessing. `performance` is the one governor whose contract is universal — run at the
 * policy's maximum frequency — which is exactly the Gaming Mode direction (maximum, never a cap).
 *
 * Deliberately not written: `scaling_min_freq`/`scaling_max_freq` (the widen channel already
 * raises the ceiling from the device's own table), `cpufreq_hardlimit`, `msm_cpufreq_limit`,
 * `enable_oc`, `screen_off_max_freq` (vendor/overclock paths with no device-derived target), and
 * the reference's offline-CPU wake/read/restore dance (a policy whose `scaling_governor` does not
 * read is simply left alone here). Behavior — snapshot priors, root gating, read-back and restore
 * — lives in [GamingEngine].
 */
object CpuGovernorMode {

    /** `scaling_governor`: the policy's active governor. */
    const val SCALING_GOVERNOR = "scaling_governor"

    /** `scaling_available_governors`: the list the policy itself declares. */
    const val AVAILABLE_GOVERNORS = "scaling_available_governors"

    /** The only governor this channel ever writes. */
    const val PERFORMANCE = "performance"

    /** The policy's declared governor names, whitespace-separated; garbage parses to nothing. */
    fun availableGovernors(text: String?): List<String> =
        text?.trim()?.split(WHITESPACE)?.filter { it.isNotBlank() }.orEmpty()

    /**
     * The change-needed target: `performance` when the policy lists it and is not already on it,
     * else null. An unreadable governor or an absent/blank governor table writes nothing — the
     * device never declared that it can honour the switch.
     */
    fun performanceTarget(current: String?, available: String?): String? {
        val now = current?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (now == PERFORMANCE) return null
        return PERFORMANCE.takeIf { it in availableGovernors(available) }
    }

    /**
     * The desired state for an idempotent re-apply (recovery): `performance` when the policy is
     * already there *or* can be switched there, else null.
     *
     * Without this, a process restart would find the previous session's governor already in place
     * and report "not applicable", forgetting a change that is still in effect. The extra write is
     * a no-op the read-back confirms.
     */
    fun desiredGovernor(current: String?, available: String?): String? =
        if (current?.trim() == PERFORMANCE) PERFORMANCE else performanceTarget(current, available)

    private val WHITESPACE = Regex("\\s+")
}
