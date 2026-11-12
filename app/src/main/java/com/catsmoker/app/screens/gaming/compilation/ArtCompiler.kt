package com.catsmoker.app.screens.gaming.compilation

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import com.catsmoker.app.R
import com.catsmoker.app.platform.shell.ShellRunner
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What a single-game `speed-profile` compile reported.
 *
 * @param status the current dexopt filter when one was read, or null when unreadable.
 * @param skipped true when the platform had nothing to do (already compiled or no profile
 *   yet) — "nothing to do", never a failure.
 */
data class SingleCompileResult(
    val pkg: String,
    val succeeded: Boolean,
    val skipped: Boolean,
    val status: String?,
    val detail: String
)

/** One compiler for both manual and scheduled runs, using the engine's existing lifetime. */
internal class ArtCompiler(
    private val context: Context,
    private val shellRunner: ShellRunner,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val hasInventoryConsent: () -> Boolean
) {
    private val _boosterLog = MutableStateFlow<List<String>>(emptyList())
    val boosterLog: StateFlow<List<String>> = _boosterLog.asStateFlow()

    /**
     * The same lines as [boosterLog], typed. Every string line is also filed here as `INFO`;
     * lifecycle events (`ANALYZING`, `NO_PROFILE`, `SKIPPED`, …) carry their own type so a
     * future surface can tell "nothing to do yet" from "already done" without re-parsing
     * words. Capped at [MAX_BOOSTER_LOG_ENTRIES], oldest evicted first.
     */
    private val _boosterLogEntries = MutableStateFlow<List<BoosterLogEntry>>(emptyList())
    val boosterLogEntries: StateFlow<List<BoosterLogEntry>> = _boosterLogEntries.asStateFlow()

    private val _boosterState = MutableStateFlow(BoosterState())
    val boosterState: StateFlow<BoosterState> = _boosterState.asStateFlow()

    /**
     * Finished sweeps, newest last, persisted across process death by [BoosterHistoryStore].
     *
     * The in-memory booster log dies with the process; this is the "what did the last boost
     * actually do" that survives. Loaded off the main thread in the `init` block, since reading the file
     * is real disk I/O however small.
     */
    private val _boosterHistory = MutableStateFlow<List<BoosterRun>>(emptyList())
    val boosterHistory: StateFlow<List<BoosterRun>> = _boosterHistory.asStateFlow()
    private val boosterCancelRequested = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Held for the whole of one sweep and released in its `finally`.
     *
     * The published [BoosterState] cannot be the guard: cancellation clears `isRunning` immediately
     * so the UI updates without waiting for the current compile, which would leave a window where a
     * second Start could begin walking the package list alongside the first.
     */
    private val boosterActive = java.util.concurrent.atomic.AtomicBoolean(false)
    private val boosterHistoryStore = BoosterHistoryStore(context)

    /** Mode and start time of the sweep in flight, for its own history entry when it ends. */
    @Volatile private var activeBoosterMode: String? = null
    @Volatile private var activeBoosterStartedAt: Long = 0L
    @Volatile
    private var currentCompileProcess: Process? = null

    suspend fun loadHistory() { _boosterHistory.value = boosterHistoryStore.load() }

    /**
     * Compiles every user app with `cmd package compile` — the same command Android's own `pm
     * compile` front-end runs, so this is real ART dexopt rather than an imitation of it.
     *
     * Follows the reference project's optimisation run: a privilege check before anything is
     * claimed, its own package left out so a forced recompile cannot kill the process doing the
     * compiling, a cancellation flag checked between packages, and a completion that can never
     * overwrite a cancellation. Each package is counted from what the shell actually answered, so
     * the closing summary is a tally of real results and not of attempts made.
     */
    suspend fun runArtOptimization(mode: String = "speed-profile", force: Boolean = false) {
        if (!boosterActive.compareAndSet(false, true)) {
            addBoosterLog(context.getString(R.string.gt_eng_booster_running))
            return
        }
        // Per-app compile times from our own past successes, persisted on the way out so a
        // cancelled sweep keeps the successes it banked before the stop landed.
        val compiledTimes = readBoosterCompiledTimes().toMutableMap()
        try {
            _boosterLog.value = emptyList()
            _boosterLogEntries.value = emptyList()
            boosterCancelRequested.set(false)
            activeBoosterMode = mode
            activeBoosterStartedAt = System.currentTimeMillis()
            _boosterState.value = BoosterState(isRunning = true, outcome = BoosterOutcome.Running)
            addBoosterLog(
                if (force) context.getString(R.string.gt_eng_booster_start_force)
                else context.getString(R.string.gt_eng_booster_start)
            )

            // `cmd package compile` is refused for other packages without a privileged shell.
            // Checking first means the log says why nothing happened, instead of listing apps that
            // were never touched and then reporting a success that never took place.
            if (!hasInventoryConsent()) {
                val reason = context.getString(R.string.policy_access_needed)
                fail(BoosterOutcome.Unavailable(reason), context.getString(R.string.gt_eng_booster_unavail, reason))
                return
            }
            if (!shellRunner.hasPrivilege()) {
                val reason = context.getString(R.string.gt_eng_booster_no_priv)
                fail(BoosterOutcome.Unavailable(reason), context.getString(R.string.gt_eng_booster_unavail, reason))
                return
            }

            val apps = eligibleBoosterPackages(force)
            if (apps.isEmpty()) {
                val reason = context.getString(R.string.gt_eng_booster_no_apps)
                fail(BoosterOutcome.Unavailable(reason), context.getString(R.string.gt_eng_booster_unavail, reason))
                return
            }
            addBoosterLog(context.getString(R.string.gt_eng_booster_found, apps.size))
            _boosterState.update { it.copy(totalCount = apps.size) }

            val useRoot = shellRunner.isRootAvailable()
            addBoosterLog(
                if (useRoot) context.getString(R.string.gt_eng_booster_root)
                else context.getString(R.string.gt_eng_booster_shizuku)
            )

            // Without -f the platform skips an app that is already in the requested filter, so ask
            // it up front what each app is compiled with and say which ones are being skipped.
            val currentStatuses = if (force) emptyMap() else queryDexoptStatuses()

            // Pre-scan first: the tallies say what this run will do before it does anything, so
            // a sweep that skips everything still reports why instead of looking stalled.
            val analysis = analyzeDexoptStatuses(currentStatuses, mode, apps)
            logBooster(
                BoosterLogType.ANALYZING,
                null,
                context.getString(
                    R.string.gt_eng_booster_analysis,
                    analysis.appsNeedingOptimization,
                    analysis.appsAlreadyOptimized,
                    analysis.appsWithNoProfile,
                    analysis.unknownPackages.size
                )
            )

            // Per-app compile times from our own past successes: the 7-day re-optimization gate
            // and the update-after-compile rule need them, and the platform never reports them.
            // Absent times leave those rules dormant rather than guessing.
            // (`compiledTimes` is loaded before the try and persisted in the finally below.)

            for (pkg in apps) {
                if (boosterCancelRequested.get()) return
                if (!hasInventoryConsent()) {
                    val reason = context.getString(R.string.policy_access_needed)
                    fail(BoosterOutcome.Unavailable(reason), context.getString(R.string.gt_eng_booster_unavail, reason))
                    return
                }

                // One decision path for skips: already optimal (or recently compiled to it) is
                // "already done"; `verify` under speed-profile is "nothing to do yet" — a
                // never-opened app with no runtime profile, not a failure. Forced runs compile
                // everything by explicit request.
                if (!force) {
                    val (needs, reason) = evaluateOptimization(
                        compilerFilter = currentStatuses[pkg],
                        lastCompilationTimeMs = compiledTimes[pkg],
                        lastUpdateTimeMs = lastUpdateTimeMs(pkg),
                        targetFilter = mode
                    )
                    if (!needs) {
                        if (reason is CompileSkipReason.NoProfile) {
                            logBooster(
                                BoosterLogType.NO_PROFILE,
                                pkg,
                                context.getString(R.string.gt_eng_booster_skip_verify, pkg)
                            )
                        } else {
                            logBooster(
                                BoosterLogType.SKIPPED,
                                pkg,
                                context.getString(R.string.gt_eng_booster_skip_done, pkg)
                            )
                        }
                        _boosterState.update { it.copy(currentPackage = null, skippedCount = it.skippedCount + 1) }
                        continue
                    }
                }

                _boosterState.update { it.copy(currentPackage = pkg) }
                addBoosterLog(context.getString(R.string.gt_eng_booster_working, pkg))
                val outcome = runCompileCommand(mode, force, pkg, useRoot)

                // A cancel that landed mid-compile: the partial result is not a failure of this app.
                if (boosterCancelRequested.get()) return

                if (outcome.succeeded) {
                    compiledTimes[pkg] = System.currentTimeMillis()
                    addBoosterLog(
                        context.getString(
                            R.string.gt_eng_booster_ok,
                            pkg,
                            outcome.detail.takeIf { it.isNotBlank() }
                                ?.let { context.getString(R.string.gt_eng_booster_ok_detail, it) }
                                .orEmpty()
                        )
                    )
                    _boosterState.update { it.copy(currentPackage = null, optimizedCount = it.optimizedCount + 1) }
                } else {
                    addBoosterLog(context.getString(R.string.gt_eng_booster_fail, pkg, outcome.detail))
                    _boosterState.update { it.copy(currentPackage = null, failedCount = it.failedCount + 1) }
                }
            }

            // The reference's own guard: a cancellation that arrived during the last compile must
            // not be reported as a completed sweep.
            if (boosterCancelRequested.get()) return

            val finished = _boosterState.value
            addBoosterLog(
                context.getString(
                    R.string.gt_eng_booster_done,
                    finished.optimizedCount,
                    finished.skippedCount,
                    finished.failedCount
                )
            )
            _boosterState.value = finished.copy(
                isRunning = false,
                currentPackage = null,
                outcome = BoosterOutcome.Completed
            )
            recordBoosterRun("completed")
        } catch (e: CancellationException) {
            markBoosterCancelled()
            throw e
        } catch (e: Exception) {
            val reason = e.message ?: e.javaClass.simpleName
            fail(
                BoosterOutcome.Failed(reason),
                context.getString(R.string.gt_eng_booster_failed, reason)
            )
        } finally {
            // Whatever happened, nothing is left compiling and a new run can start. Persist the
            // per-app compile times first, so successes banked before a cancel survive it.
            writeBoosterCompiledTimes(compiledTimes)
            if (boosterCancelRequested.get()) markBoosterCancelled()
            currentCompileProcess?.destroy()
            currentCompileProcess = null
            boosterActive.set(false)
            activeBoosterMode = null
        }
    }


    /**
     * Compiles one game with `cmd package compile -m speed-profile` — the pre-launch pass that
     * warms exactly the game about to be played instead of sweeping every installed app.
     *
     * Reuses the sweep's own machinery (privilege gate, cancel flag, compile classifier,
     * history record) but guards on the same [boosterActive] lock, so a single run and a sweep
     * can never walk the platform side by side. Skips by the same [shouldCompilePackage] rule
     * the sweep applies, read from the same `dumpsys package dexopt` source.
     */
    suspend fun runSinglePackageOptimization(pkg: String): SingleCompileResult {
        if (!boosterActive.compareAndSet(false, true)) {
            return SingleCompileResult(pkg, false, false, null, context.getString(R.string.gt_eng_booster_running))
        }
        try {
            boosterCancelRequested.set(false)
            activeBoosterMode = "speed-profile"
            activeBoosterStartedAt = System.currentTimeMillis()
            _boosterState.value = BoosterState(isRunning = true, outcome = BoosterOutcome.Running)
            addBoosterLog(context.getString(R.string.gt_eng_booster_working, pkg))

            if (!shellRunner.hasPrivilege()) {
                val reason = context.getString(R.string.gt_eng_booster_no_priv)
                _boosterState.value = BoosterState(isRunning = false, outcome = BoosterOutcome.Unavailable(reason))
                return SingleCompileResult(pkg, false, false, null, reason)
            }

            val status = queryDexoptStatuses()[pkg]
            if (!shouldCompilePackage(status, "speed-profile", false)) {
                _boosterState.value = BoosterState(isRunning = false, outcome = BoosterOutcome.Completed)
                recordBoosterRun("skipped")
                return SingleCompileResult(pkg, true, true, status, "")
            }

            val outcome = runCompileCommand("speed-profile", false, pkg, shellRunner.isRootAvailable())
            if (boosterCancelRequested.get()) {
                markBoosterCancelled()
                return SingleCompileResult(
                    pkg, false, false, status,
                    context.getString(R.string.gt_eng_booster_stopped, 0)
                )
            }
            _boosterState.value = if (outcome.succeeded) {
                BoosterState(isRunning = false, outcome = BoosterOutcome.Completed)
            } else {
                BoosterState(isRunning = false, outcome = BoosterOutcome.Failed(outcome.detail))
            }
            recordBoosterRun(if (outcome.succeeded) "completed" else "failed")
            return SingleCompileResult(pkg, outcome.succeeded, false, status, outcome.detail)
        } finally {
            if (boosterCancelRequested.get()) markBoosterCancelled()
            currentCompileProcess?.destroy()
            currentCompileProcess = null
            boosterActive.set(false)
            activeBoosterMode = null
        }
    }

    /**
     * Appends the finished run to the persisted history and the live flow, from the counts the
     * state holds right now. Not a read-back of anything — the counts were accumulated from
     * what the shell answered, which is the same standard a report row has to meet.
     */
    private fun recordBoosterRun(outcome: String) {
        val mode = activeBoosterMode ?: return
        val startedAt = activeBoosterStartedAt
        if (startedAt <= 0L) return
        val s = _boosterState.value
        val run = BoosterRun(
            startedAt = startedAt,
            durationMs = System.currentTimeMillis() - startedAt,
            mode = mode,
            optimized = s.optimizedCount,
            skipped = s.skippedCount,
            failed = s.failedCount,
            outcome = outcome
        )
        boosterHistoryStore.record(run)
        _boosterHistory.update { it + run }
    }

    /** Ends the run with a stated reason instead of a silent stop. */
    private fun fail(outcome: BoosterOutcome, logLine: String) {
        addBoosterLog(logLine)
        _boosterState.update { it.copy(isRunning = false, currentPackage = null, outcome = outcome) }
        // Only a run that actually started something is worth a history entry: Unavailable means
        // the sweep never began, and recording it would bury the runs that did.
        if (outcome is BoosterOutcome.Failed) recordBoosterRun("failed")
    }

    /**
     * The apps worth compiling: everything the user installed, plus any system app they added to
     * their own game library — with the two classes that can only waste a compile slot screened
     * out by [BoosterPackageClassifier]: overlay/RRO packages (no meaningful dex; the platform
     * refuses or no-ops them) and, for a `speed-profile` run, never-opened apps at status `verify`
     * (no runtime profile yet, so a profile-guided compile has nothing to work with). Both are
     * counted as [BoosterState.skippedCount] with their own log line — "nothing to do" is not
     * "already done" and not a failure — and neither is filtered in a forced run, where the user
     * explicitly asked for every package.
     */
    // Partial visibility is fine: an invisible app simply misses this sweep.
    private fun eligibleBoosterPackages(force: Boolean = false): List<String> {
        if (!hasInventoryConsent()) return emptyList()
        val userAdded = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            .getStringSet("user_games", emptySet()) ?: emptySet()
        return context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName in userAdded }
            .map { it.packageName to it }
            // Recompiling ourselves restarts this process and takes the sweep down with it. The
            // reference excludes its own package for the same reason.
            .filter { (pkg, _) -> pkg != context.packageName }
            .filter { (pkg, info) ->
                // The classifier reads the APK's own path — no shell call, no per-app fork. In a
                // forced run every package the user asked for goes through, overlays included.
                force || !BoosterPackageClassifier.isOverlayLike(pkg, info.sourceDir)
            }
            .map { (pkg, _) -> pkg }
            .distinct()
            .toList()
            // The verify check needs the statuses, which come from one `dumpsys package dexopt`
            // call the caller already makes; keep the filter there (see runArtOptimization).
    }

    /**
     * Records a cancellation without discarding the counts: the run stopped where it stopped, and
     * the UI reports how far it got rather than resetting as if it had never started.
     */
    private fun markBoosterCancelled() {
        val current = _boosterState.value
        // Only a run still in progress can be cancelled. Once it has reported how it ended —
        // completed, unavailable, failed, or already cancelled — that stands: a Stop that arrives
        // in the moment after the last package must not rewrite a finished sweep as a cancelled one.
        if (current.outcome !is BoosterOutcome.Running) return
        addBoosterLog(context.getString(R.string.gt_eng_booster_stopped, current.processedCount))
        _boosterState.value = current.copy(
            isRunning = false,
            currentPackage = null,
            outcome = BoosterOutcome.Cancelled
        )
        // "Cancelled after 12" is a different fact about the sweep than "completed", and the
        // counts of how far it got are exactly what the history row is for.
        recordBoosterRun("cancelled")
    }

    private suspend fun queryDexoptStatuses(): Map<String, String> {
        return withContext(Dispatchers.IO) {
            if (!hasInventoryConsent()) emptyMap()
            else DexoptStatusParser.parse(execute("dumpsys package dexopt"))
        }
    }

    /**
     * When this app last successfully compiled each package, epoch millis by package name.
     *
     * Stored as `pkg@time` strings under one prefs key: a structured store would be nicer, but
     * this is write-rarely (once per sweep) and read-once per sweep, and a corrupt entry is
     * simply dropped rather than failing the run. Times feed the 7-day re-optimization gate;
     * a package with no recorded time leaves the gate dormant for it.
     */
    private fun readBoosterCompiledTimes(): Map<String, Long> {
        val raw = prefs.getStringSet("booster_compiled_times", emptySet()) ?: emptySet()
        val out = HashMap<String, Long>(raw.size)
        for (entry in raw) {
            val at = entry.lastIndexOf('@')
            if (at <= 0) continue
            entry.substring(at + 1).toLongOrNull()?.let { out[entry.substring(0, at)] = it }
        }
        return out
    }

    private fun writeBoosterCompiledTimes(times: Map<String, Long>) {
        prefs.edit { putStringSet("booster_compiled_times", times.map { (pkg, t) -> "$pkg@$t" }.toSet()) }
    }

    /** When the package was last updated, or null when the package manager cannot say. */
    private fun lastUpdateTimeMs(pkg: String): Long? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
        } else {
            context.packageManager.getPackageInfo(pkg, 0).lastUpdateTime
        }
    }.getOrNull()
    /** What `cmd package compile` actually reported for one package. */
    private data class CompileOutcome(val succeeded: Boolean, val detail: String)

    private fun shellCommandForSu(args: List<String>): String =
        args.joinToString(" ") { arg ->
            if (arg.isEmpty()) "''" else "'" + arg.replace("'", "'\\''") + "'"
        }

    /**
     * Compiles one package and reports what the platform said about it.
     *
     * The root path spawns `su` directly instead of going through [ShellRunner] because cancelling
     * has to be able to `destroy()` this exact process: both libsu and the Shizuku binder call
     * block until the compile returns, and a dexopt of a large app is not quick.
     */
    private suspend fun runCompileCommand(
        mode: String,
        force: Boolean,
        pkg: String,
        useRoot: Boolean
    ): CompileOutcome = withContext(Dispatchers.IO) {
        val args = buildList {
            add("cmd"); add("package"); add("compile"); add("-m"); add(mode)
            if (force) add("-f")
            add(pkg)
        }
        if (!useRoot) {
            val result = shellRunner.execSafeResult(*args.toTypedArray())
            return@withContext classifyCompileOutput(result.exitCode, result.text)
        }
        try {
            val process = ProcessBuilder("su", "-c", shellCommandForSu(args))
                .redirectErrorStream(true)
                .start()
            currentCompileProcess = process
            val output = StringBuilder()
            val reader = Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        output.append(reader.readText())
                    }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }
            val exit = try {
                if (!process.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    runCatching { process.inputStream.close() }
                    runCatching { process.errorStream.close() }
                    -1
                } else {
                    process.exitValue()
                }
            } finally {
                reader.join(1_000)
                runCatching { process.inputStream.close() }
                runCatching { process.errorStream.close() }
                runCatching { process.outputStream.close() }
                currentCompileProcess = null
            }
            classifyCompileOutput(exit, output.toString())
        } catch (e: Exception) {
            currentCompileProcess = null
            CompileOutcome(false, e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Decides whether one compile was accepted, from the exit code and the shell's own words.
     *
     * `cmd package compile` prints its verdict — `Success`, or a `Failure`/`Error:` line — and some
     * builds still exit 0 after refusing, so both signals are read. A silent exit 0 is taken at
     * face value; inventing a failure there would be as wrong as inventing a success.
     */
    private fun classifyCompileOutput(exitCode: Int, output: String): CompileOutcome {
        val text = output.trim()
        val lower = text.lowercase(java.util.Locale.US)
        return when {
            exitCode != 0 -> CompileOutcome(false, text.ifBlank { context.getString(R.string.gt_eng_booster_exit, exitCode) })
            lower.startsWith("failure") || lower.contains("error:") -> CompileOutcome(false, text)
            else -> CompileOutcome(true, text.takeUnless { it.equals("Success", ignoreCase = true) }.orEmpty())
        }
    }

    /**
     * User-initiated stop. Says plainly when there was nothing to stop, matching the reference's
     * own "No optimization is currently running." response.
     */
    suspend fun cancelArtOptimization() {
        if (!requestBoosterCancel()) {
            addBoosterLog(context.getString(R.string.gt_eng_booster_idle))
            return
        }
        // The platform forks dex2oat on our behalf, and it outlives the shell that asked for it.
        shellRunner.killCurrentProcess()
    }

    /**
     * Stop from a component that is being torn down.
     *
     * The flag and the `destroy()` run on the calling thread, so the sweep is unblocked before this
     * returns even though the caller's scope is about to die; the `pkill` needs a coroutine and
     * goes to the engine's own scope, which outlives the service.
     *
     * @return true when a run was actually in progress.
     */
    fun cancelArtOptimizationNow(): Boolean {
        if (!requestBoosterCancel()) return false
        scope.launch { shellRunner.killCurrentProcess() }
        return true
    }

    /**
     * Raises the cancellation flag and unblocks the compile in flight.
     *
     * @return false when no sweep was running, so callers can say so rather than report a stop
     *   that stopped nothing.
     */
    private fun requestBoosterCancel(): Boolean {
        if (!boosterActive.get()) return false
        boosterCancelRequested.set(true)
        // Ends the blocking read and waitFor in runCompileCommand, so the loop reaches its next
        // cancellation check instead of waiting out the current dexopt.
        currentCompileProcess?.destroy()
        currentCompileProcess = null
        markBoosterCancelled()
        return true
    }
    /**
     * Appends one line to the booster log.
     *
     * [MutableStateFlow.update] rather than a read-then-write, because the sweep logs from the IO
     * dispatcher while a stop can come in from the main thread, and a lost line would be a step
     * that happened with nothing to show it.
     */
    private fun addBoosterLog(msg: String) {
        _boosterLog.update { it + msg }
        _boosterLogEntries.update { appendBoosterLogEntry(it, BoosterLogEntry(BoosterLogType.INFO, null, msg)) }
    }

    /**
     * Appends one typed line: the same words go to the string log the UI already shows, and
     * the type goes to [boosterLogEntries] for future surfacing.
     *
     * Also used by `GamingEngine` for the Gaming Mode lines it writes to the same history.
     */
    internal fun logBooster(type: BoosterLogType, pkg: String?, msg: String) {
        _boosterLog.update { it + msg }
        _boosterLogEntries.update { appendBoosterLogEntry(it, BoosterLogEntry(type, pkg, msg)) }
    }

    private suspend fun execute(command: String): String = shellRunner.exec(command)

    private companion object { const val COMPILE_TIMEOUT_SECONDS = 60L }
}
