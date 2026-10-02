package com.catsmoker.app.features.gamingtools.engine

/**
 * The ART sweep's compile-decision layer: which apps are worth a
 * `cmd package compile -m [mode]` run, and why not for the rest.
 *
 * Ported from `reference/gamingtools/art/.../domain/model/common/AppCompilationInfo.kt`
 * (read before this file was written), minus what the app deliberately does not do:
 * - No per-package `dumpsys package <pkg>` / `compile --check` / oat-`ls` chain. The app reads
 *   one global `dumpsys package dexopt` dump and the APK's own `sourceDir`; extra forks per app
 *   cost more than they answer for the same verdict (see the per-pkg chain this replaces).
 * - No session-cache / cached-dump reuse: a sweep reads fresh state every run, because a
 *   `recreate()`-surviving cache would report yesterday's filters as today's.
 *
 * What IS ported, and why each piece earns its keep:
 * - [isFilterOptimalForTarget]: without it the sweep recompiles `speed` apps for a
 *   `speed-profile` target — burning a slot for a strictly worse filter coming back.
 * - [evaluateOptimization]: the 7-day re-optimization gate (`MIN_DAYS_BEFORE_REOPTIMIZATION`)
 *   plus the update-after-compile rule. The gate only bites when the caller supplies both
 *   timestamps; with unknown times the decision degrades to the optimal-filter + no-profile
 *   rules, never to a fabricated "recently optimized".
 * - [analyzeDexoptStatuses]: the pre-scan tallies (needing / already / no-profile / unknown)
 *   the sweep logs before compiling anything, so a run that skips everything still says why.
 * - [BoosterLogType] / [BoosterLogEntry] / [appendBoosterLogEntry]: the typed log. The
 *   string log the UI already shows keeps working untouched; every string line is also filed
 *   as `INFO`, while lifecycle events (`ANALYZING`, `NO_PROFILE`, `SKIPPED`, …) carry their
 *   own type for future surfacing.
 */
const val MIN_DAYS_BEFORE_REOPTIMIZATION = 7L

private const val MS_PER_DAY = 24 * 60 * 60 * 1000L

/** Filters that are already fully optimized: no `speed` re-compile can improve them. */
private val FULLY_OPTIMIZED_FILTERS = setOf("speed", "everything")

/** Filters carrying profile-guided optimization: only a stale profile justifies re-doing them. */
private val PROFILE_OPTIMIZED_FILTERS = setOf("speed-profile")

/** Filters the platform reports when it has nothing usable to say. */
private val NON_FILTER_MARKERS = setOf("unknown-present", "unknown-optimized")

/**
 * Whether [filter] already satisfies [target] (`speed-profile` or `speed`), so compiling
 * would at best reproduce what is there. Mirrors the reference's `isFilterOptimalForTarget`:
 * `everything` covers all targets, `speed` covers `speed-profile`, nothing covers upward.
 */
fun isFilterOptimalForTarget(filter: String?, target: String): Boolean {
    if (filter == null || filter in NON_FILTER_MARKERS) return false
    return when (target) {
        "speed" -> filter == "speed" || filter == "everything"
        "speed-profile" -> filter == "speed-profile" || filter == "speed" || filter == "everything"
        else -> filter == target
    }
}

/** Why one package was skipped: nothing-to-do, told apart instead of lumped. */
sealed interface CompileSkipReason {
    /** At the target filter but compiled too recently for new profiles to have accumulated. */
    data class RecentlyOptimized(val daysAgo: Long, val filter: String) : CompileSkipReason

    /** Already carries the target (or better) filter. */
    data class AlreadyOptimal(val filter: String) : CompileSkipReason

    /** At `verify`: never opened, so no runtime profile exists for `speed-profile` to use. */
    data class NoProfile(val filter: String) : CompileSkipReason
}

/**
 * Whether one package needs a compile run, and the skip reason when it does not.
 *
 * @param compilerFilter current filter, or null when unreadable — unreadable compiles, because
 * asking the platform is the only way to find out.
 * @param lastCompilationTimeMs when dex2oat last ran for this app, or null when unknown (the
 * 7-day gate and the update-after-compile rule stay dormant rather than guessing).
 * @param lastUpdateTimeMs when the app was last updated, or null when unknown.
 * @param targetFilter the requested compile filter.
 * @param oatFileExists whether compiled output exists; false always compiles. Defaults to true
 * because the sweep cannot cheaply stat oat files per app without the per-package fork chain
 * it deliberately avoids — unknown is treated as present, never as absent.
 */
fun evaluateOptimization(
    compilerFilter: String?,
    lastCompilationTimeMs: Long?,
    lastUpdateTimeMs: Long?,
    targetFilter: String,
    oatFileExists: Boolean = true,
    nowMs: Long = System.currentTimeMillis(),
): Pair<Boolean, CompileSkipReason?> {
    if (!oatFileExists) return true to null
    if (compilerFilter == null || lastCompilationTimeMs == null) {
        // verify-without-timestamps still carries its no-profile meaning; everything else
        // unknown compiles.
        if (compilerFilter == "verify" && targetFilter == "speed-profile") {
            return false to CompileSkipReason.NoProfile(compilerFilter)
        }
        return true to null
    }
    if (lastUpdateTimeMs != null && lastUpdateTimeMs > lastCompilationTimeMs) return true to null
    if (compilerFilter == "verify" && targetFilter == "speed-profile") {
        return false to CompileSkipReason.NoProfile(compilerFilter)
    }
    if (isFilterOptimalForTarget(compilerFilter, targetFilter)) {
        val ageMs = nowMs - lastCompilationTimeMs
        if (ageMs < MIN_DAYS_BEFORE_REOPTIMIZATION * MS_PER_DAY) {
            return false to CompileSkipReason.RecentlyOptimized(ageMs / MS_PER_DAY, compilerFilter)
        }
        // Optimal but stale: profiles accumulated since deserve a re-compile.
        // (Falls through to true below.)
    } else if (compilerFilter == targetFilter) {
        return false to CompileSkipReason.AlreadyOptimal(compilerFilter)
    }
    return true to null
}

/** What the pre-scan found, before the sweep compiles anything. */
data class DexoptAnalysis(
    val totalApps: Int,
    val appsNeedingOptimization: Int,
    val appsAlreadyOptimized: Int,
    val appsWithNoProfile: Int,
    /** Eligible apps the dump never mentioned: unreadable, so they compile. */
    val unknownPackages: List<String>,
)

/**
 * Tallies one global dump against the eligible universe: optimal filters are done, `verify`
 * under `speed-profile` has no profile yet, everything else (including unreadable) needs work.
 * Pure so the counts are pinnable without a shell.
 */
fun analyzeDexoptStatuses(
    statuses: Map<String, String>,
    mode: String,
    universe: Collection<String>,
): DexoptAnalysis {
    var needing = 0
    var already = 0
    var noProfile = 0
    val unknown = ArrayList<String>()
    for (pkg in universe) {
        val status = statuses[pkg]
        if (status == null) {
            needing++
            unknown.add(pkg)
        } else if (mode == "speed-profile" && status == "verify") {
            noProfile++
        } else if (isFilterOptimalForTarget(status, mode)) {
            already++
        } else {
            needing++
        }
    }
    return DexoptAnalysis(
        totalApps = universe.size,
        appsNeedingOptimization = needing,
        appsAlreadyOptimized = already,
        appsWithNoProfile = noProfile,
        unknownPackages = unknown,
    )
}

/** Lifecycle event types for the sweep's typed log. */
enum class BoosterLogType {
    INFO,
    OPTIMIZING,
    SUCCESS,
    SKIPPED,
    ERROR,
    START,
    COMPLETE,
    CANCELLED,
    COMMAND,
    ANALYZING,
    NO_PROFILE,
}

/** One typed log line: what happened, to which package, in words. */
data class BoosterLogEntry(
    val type: BoosterLogType,
    val packageName: String?,
    val message: String,
)

/** Maximum in-memory typed entries; oldest evicted first (reference: `MAX_LOG_ENTRIES = 100`). */
const val MAX_BOOSTER_LOG_ENTRIES = 100

/** Appends one typed entry, evicting the oldest past the cap. Pure for testability. */
fun appendBoosterLogEntry(
    entries: List<BoosterLogEntry>,
    entry: BoosterLogEntry,
): List<BoosterLogEntry> {
    if (entries.size < MAX_BOOSTER_LOG_ENTRIES) return entries + entry
    return entries.drop(entries.size - MAX_BOOSTER_LOG_ENTRIES + 1) + entry
}
