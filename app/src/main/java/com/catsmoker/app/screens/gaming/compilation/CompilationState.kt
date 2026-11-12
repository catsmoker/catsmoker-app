package com.catsmoker.app.screens.gaming.compilation



/**
 * How the ART dexopt sweep ended, or that it never began.
 *
 * Mirrors the reference project's `OptimizationResult`, including the distinction it draws between
 * a cancelled run and a completed one — its repository carries an explicit "never overwrite a
 * cancellation with 'Completed'" guard, and that only works if the two are separate states.
 */
sealed class BoosterOutcome {
    /** Nothing has been run in this session. */
    object Idle : BoosterOutcome()
    object Running : BoosterOutcome()
    object Completed : BoosterOutcome()
    object Cancelled : BoosterOutcome()

    /** The sweep could not start at all; [reason] is what the device or the shell refused. */
    data class Unavailable(val reason: String) : BoosterOutcome()

    /** The sweep started and then broke; [reason] is the real error text. */
    data class Failed(val reason: String) : BoosterOutcome()
}

/**
 * What the ART dexopt sweep is doing right now.
 *
 * Modelled on the reference project's `OptimizationProgress`: the running flag, the counts and the
 * outcome travel together, so the UI can tell "preparing", "compiling app 12 of 90", "cancelled
 * after 12" and "finished" apart. A bare progress float could not — it reads 0 both before a run
 * starts and after one is cancelled, which is why the old card showed nothing in either case.
 *
 * Every count is incremented from a command that actually ran and actually answered.
 */
data class BoosterState(
    val isRunning: Boolean = false,    /** Package being compiled at this moment, or null between packages. */
    val currentPackage: String? = null,
    /** Apps the sweep will visit. 0 until the package list has been queried. */
    val totalCount: Int = 0,
    /** Apps `cmd package compile` accepted. */
    val optimizedCount: Int = 0,
    /** Apps already in the requested compile filter, so no command was run for them. */
    val skippedCount: Int = 0,
    /** Apps the platform refused to compile. */
    val failedCount: Int = 0,
    val outcome: BoosterOutcome = BoosterOutcome.Idle
) {
    /** Apps visited, whether compiled, skipped or refused. */
    val processedCount: Int get() = optimizedCount + skippedCount + failedCount

    /**
     * Fraction done, or null while the total is still unknown. The UI shows an indeterminate bar
     * then, rather than a 0% that would read as a run that has stalled.
     */
    val progress: Float?
        get() = if (totalCount > 0) processedCount.toFloat() / totalCount.toFloat() else null
}

/**
 * Whether one package is worth a `cmd package compile -m [mode]` run.
 *
 * The sweep's own skip rules, factored out so the single-game path honors them too: forcing
 * compiles everything; an app already at the requested filter — or at a strictly better one
 * (`speed`/`everything` cover a `speed-profile` target, which recompiling could only
 * downgrade from) — is done; a never-opened app at `verify` has no runtime profile for
 * `speed-profile` to work with ("nothing to do yet", not a failure — forced runs compile
 * them anyway). An unreadable status compiles, because asking the platform is the only way
 * to find out.
 */
fun shouldCompilePackage(currentStatus: String?, mode: String, force: Boolean): Boolean {
    if (force) return true
    if (isFilterOptimalForTarget(currentStatus, mode)) return false
    if (mode == "speed-profile" && currentStatus == "verify") return false
    return true
}
