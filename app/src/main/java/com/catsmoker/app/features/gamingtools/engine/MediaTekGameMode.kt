package com.catsmoker.app.features.gamingtools.engine

/**
 * MediaTek game-mode tables: the GED boost parameters and PPM policy states HunterX-Reborn-II
 * writes in `common/service.sh` (lines 258-288, read before this file was written), as data.
 *
 * Every value widens — boosts on, idle floor up, boost policy maxed — never a cap; the one
 * `0` (`ged_force_mdp_enable`) disables a force-off, not a ceiling. Behavior (presence gates,
 * snapshot, read-back, restore) lives in [GamingEngine], which consults only nodes the device
 * actually has: on the lab device (TB-8505X, mt6761) all 15 GED params exist except
 * `gx_boost_on`, which stays in the table for devices that carry it and is skipped where it
 * does not. PPM restore re-issues per-index states parsed from the status dump (see
 * [ppmRestoreWrites]), because that node reports human-readable status, not re-writable text.
 */
object MediaTekGameMode {

    /** GED parameter node → game-mode value, verbatim from the reference payload. */
    val gedParams: Map<String, String> = linkedMapOf(
        "/sys/module/ged/parameters/gx_game_mode" to "1",
        "/sys/module/ged/parameters/gx_force_cpu_boost" to "1",
        "/sys/module/ged/parameters/boost_amp" to "1",
        "/sys/module/ged/parameters/boost_extra" to "1",
        "/sys/module/ged/parameters/boost_gpu_enable" to "1",
        "/sys/module/ged/parameters/enable_cpu_boost" to "1",
        "/sys/module/ged/parameters/enable_gpu_boost" to "1",
        "/sys/module/ged/parameters/enable_game_self_frc_detect" to "1",
        "/sys/module/ged/parameters/gpu_idle" to "10",
        "/sys/module/ged/parameters/cpu_boost_policy" to "100",
        "/sys/module/ged/parameters/ged_force_mdp_enable" to "0",
        "/sys/module/ged/parameters/ged_boost_enable" to "1",
        "/sys/module/ged/parameters/ged_smart_boost" to "100",
        "/sys/module/ged/parameters/gx_frc_mode" to "1",
        "/sys/module/ged/parameters/gx_boost_on" to "1"
    )

    /** PPM policy index → enabled, verbatim from the reference `policy_status` writes. */
    val ppmPolicies: List<Pair<Int, Int>> = listOf(
        0 to 0,
        1 to 1,
        2 to 0,
        3 to 0,
        4 to 0,
        5 to 0,
        6 to 1,
        7 to 1,
        8 to 0,
        9 to 1
    )

    const val PPM_POLICY_STATUS_NODE = "/proc/ppm/policy_status"
    const val PPM_ENABLED_NODE = "/proc/ppm/enabled"
    const val GED_PARAMETERS_DIR = "/sys/module/ged/parameters"

    /**
     * The writes for nodes the device actually has: priors intersected with the known
     * tables, mapped to target values. Unknown nodes never enter the plan, even with a
     * prior — the engine only ever writes what this table names.
     */
    fun writePlan(priors: Map<String, String?>): Map<String, String> {
        val plan = linkedMapOf<String, String>()
        for ((node, target) in gedParams) {
            if (priors.containsKey(node)) plan[node] = target
        }
        return plan
    }

    /**
     * Parses a `/proc/ppm/policy_status` status dump (`[N] NAME: enabled|disabled` lines)
     * into the `echo N 1/0` writes that reproduce it. Garbage (like the `enabled` node's
     * `ppm is enabled` string) parses to nothing — never a fabricated write.
     */
    fun ppmRestoreWrites(statusDump: String): List<String> {
        val pattern = Regex("""\[(\d+)]\s+\S+:\s+(enabled|disabled)""")
        return statusDump.lineSequence().mapNotNull { line ->
            val match = pattern.find(line.trim()) ?: return@mapNotNull null
            val idx = match.groupValues[1]
            val on = if (match.groupValues[2] == "enabled") "1" else "0"
            "$idx $on"
        }.toList()
    }

    /** The game-mode `policy_status` writes: `echo <idx> <1/0>`. */
    fun ppmGameWrites(): List<String> = ppmPolicies.map { (idx, on) -> "$idx $on" }
}
