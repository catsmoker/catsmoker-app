package com.catsmoker.app.screens.gaming.automation

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import com.catsmoker.app.application.privacy.GamingAccessConsent
import com.catsmoker.app.screens.gaming.GameLibrarySelection
import com.catsmoker.app.screens.gaming.GameSettingsStore
import com.catsmoker.app.screens.gaming.mode.GamingEngine
import com.catsmoker.app.screens.gaming.mode.GamingModeJobRunner
import com.catsmoker.app.screens.gaming.mode.GamingModeState
import com.catsmoker.app.screens.gaming.mode.GamingModeObservation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import com.catsmoker.app.screens.gaming.cache.GameCachePreloader
import com.catsmoker.app.platform.device.isSamsung
import com.catsmoker.app.screens.gaming.display.DisplayApplyReport
import com.catsmoker.app.screens.gaming.display.DisplayEngine
import com.catsmoker.app.screens.gaming.display.DisplayRateStore
import com.catsmoker.app.screens.gaming.mode.GamingOptimizationSnapshot

@Singleton
class GameAutomation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: AutomationAccess,
    private val settings: GameSettingsStore,
    private val engineProvider: dagger.Lazy<GamingEngine>,
    private val jobsProvider: dagger.Lazy<GamingModeJobRunner>,
    private val modeObservation: GamingModeObservation,
    private val cachePreloader: GameCachePreloader,
    private val displayEngineProvider: dagger.Lazy<DisplayEngine>,
    private val displayRates: DisplayRateStore
) {
    /** Built on first display request only, so a game-only caller never constructs it. */
    private val displayEngine: DisplayEngine get() = displayEngineProvider.get()
    // Reject unauthenticated entry points before constructing a privileged engine.
    private val engine: GamingEngine get() = engineProvider.get()
    private val jobs: GamingModeJobRunner get() = jobsProvider.get()
    private val prefs = context.getSharedPreferences("game_automation_status", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writes = Mutex()
    private val admission = BroadcastAdmission()
    private var restorePending = false
    private val _status = MutableStateFlow(AutomationStatus.afterProcessStart(prefs.getString("last", null)))
    val status = _status.asStateFlow()
    private val _delivery = MutableStateFlow(AutomationStatus.afterProcessStart(prefs.getString("last_delivery", null)))
    val delivery = _delivery.asStateFlow()

    @Suppress("DEPRECATION") // Bundle.get checks the actual type, including optional extras.
    fun parse(intent: Intent): AutomationProtocol.Request? = runCatching {
        if (intent.action != AutomationProtocol.ACTION || intent.data != null ||
            intent.clipData != null || intent.selector != null || intent.type != null) return null
        val extras = intent.extras ?: return null
        if (extras.keySet().any { it !in AutomationProtocol.keys || extras.get(it) !is String }) return null
        if (!access.accepts(extras.getString("token")) || !consented()) return null
        AutomationProtocol.parse(
            extras.getString("operation"),
            extras.getString("game"),
            extras.getString("request_id"),
            extras.getString("hz")
        )
    }.getOrNull()

    internal fun rejectBroadcast(request: AutomationProtocol.Request): String? =
        admission.admit(request, SystemClock.elapsedRealtime())

    private fun consented(): Boolean =
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getBoolean("terms_accepted", false) &&
            GamingAccessConsent.allows(context, GamingAccessConsent.INVENTORY)

    fun gameAvailable(pkg: String): Boolean = runCatching {
        if (!consented() || pkg == context.packageName) return false
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        val library = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        GameLibrarySelection.isVisible(pkg, info.category == ApplicationInfo.CATEGORY_GAME,
            library.getStringSet("user_games", emptySet()).orEmpty(),
            library.getStringSet("removed_games", emptySet()).orEmpty()) &&
            context.packageManager.getLaunchIntentForPackage(pkg) != null
    }.getOrDefault(false)

    fun query(): String = query(null)

    fun query(requestId: String?): String = JSONObject().apply {
        val live = modeObservation.current()
        val recorded = context.getSharedPreferences("gaming_engine_prefs", Context.MODE_PRIVATE)
        val recordedSession = recorded.getBoolean("is_active", false) ||
            !recorded.getStringSet("affected_pkgs", emptySet()).isNullOrEmpty() ||
            !recorded.getStringSet("appops_prior", emptySet()).isNullOrEmpty()
        put("version", AutomationProtocol.VERSION)
        put("selectedGame", settings.selection.value.packageName ?: JSONObject.NULL)
        put("mode", live?.mode ?: if (recordedSession) "RecordedSession" else "Idle")
        put("modeObserved", live != null)
        put("sessionOpen", live?.sessionOpen ?: recordedSession)
        put("recoveryRequired", live?.recoveryRequired ?: recordedSession)
        put("unavailableCount", live?.unavailableCount ?: JSONObject.NULL)
        put("last", JSONObject(status.value))
        put("lastDelivery", JSONObject(delivery.value))
        // This is an exact lookup of the two most recent records, not a persistent request history.
        requestId?.let { id ->
            put("request_id", id)
            put("matchingRequest", AutomationStatus.matchingRequest(id, status.value, delivery.value) ?: JSONObject.NULL)
        }
        put("cache", JSONObject(cachePreloader.status.value))
        put("display", displayStatus())
    }.toString()

    /**
     * The display half of a query: what is attached, what each display runs at, and what the
     * engine last did. Reported even when no display session is open, because "nothing is
     * attached" is itself the answer a caller needs before sending APPLY_DISPLAY.
     */
    private fun displayStatus(): JSONObject = JSONObject().apply {
        val state = displayEngine.state.value
        put("displays", org.json.JSONArray(state.displays.map { display ->
            JSONObject().apply {
                put("id", display.id)
                put("name", display.name)
                put("external", display.isExternal)
                put("on", display.isOn)
                display.currentHz?.let { put("currentHz", it) }
                put("rates", org.json.JSONArray(display.rates.map { it.toDouble() }))
            }
        }))
        put("availableBackends", org.json.JSONArray(state.probe.availableBackends.map { it.name }))
        put("privilegedReason", state.probe.reason ?: JSONObject.NULL)
        put("savedRates", JSONObject().apply {
            for (scope in listOf(DisplayRateStore.GLOBAL, DisplayRateStore.EXTERNAL, DisplayRateStore.DEX)) {
                displayRates.rate(scope)?.let { put(scope, it) }
            }
        })
        put("lastReport", state.lastReport?.let { report ->
            JSONObject().apply {
                put("applied", report.applied)
                put("summary", report.summary)
                report.backend?.let { put("backend", it.name) }
                report.requestedHz?.let { put("requestedHz", it) }
                report.observedHz?.let { put("observedHz", it) }
                report.refusedReason?.let { put("refused", it) }
            }
        } ?: JSONObject.NULL)
    }

    fun select(request: AutomationProtocol.Request): String {
        val pkg = request.game ?: return response("invalid_request")
        if (!gameAvailable(pkg)) return recordDelivery(request, "game_unavailable")
        settings.select(pkg)
        recordDelivery(request, "selected")
        return AutomationStatus.withRequest(response("selected"), request)
    }

    /** Process-owned; Activity destruction must not cancel a half-written engine session. */
    fun execute(intent: Intent) = scope.async {
        val request = parse(intent) ?: return@async response("denied")
        val start = SystemClock.elapsedRealtime()
        runRequest(request) { parse(intent) != null }.let { result ->
            // A rejected concurrent request must be observable without overwriting the active run.
            if (!request.isQuery) recordDelivery(request, JSONObject(result).optString("result"),
                SystemClock.elapsedRealtime() - start)
            result
        }
    }

    internal fun recordDelivery(request: AutomationProtocol.Request, code: String, durationMs: Long? = null): String {
        val result = AutomationStatus.withRequest(response(code), request)
        val lastDelivery = JSONObject(result).apply { durationMs?.let { put("durationMs", it) } }.toString()
        _delivery.value = lastDelivery
        prefs.edit().putString("last_delivery", lastDelivery).apply()
        return lastDelivery
    }

    fun prepareFromUi(pkg: String?) = scope.async {
        if (!consented() || (pkg != null && !gameAvailable(pkg))) return@async response("denied")
        runRequest(AutomationProtocol.Request(AutomationProtocol.Operation.APPLY_SAVED, pkg))
    }

    fun restoreFromUi() = scope.async {
        // Revoking automation never removes the user's ability to restore an owned session.
        runRequest(AutomationProtocol.Request(AutomationProtocol.Operation.RESTORE, null)) { true }
    }

    private suspend fun runRequest(request: AutomationProtocol.Request, authorized: () -> Boolean = ::consented): String {
        if (!authorized()) return response("denied")
        // Display work is independent of a Gaming Mode session: it takes neither the session
        // mutex nor the session's restore ordering, so a game-exit RESTORE and a panel change
        // cannot deadlock each other.
        if (request.isDisplay) return runDisplayRequest(request, authorized)
        val restore = request.isRestore
        val startedAt = SystemClock.elapsedRealtime()
        if (restore) {
            if (restorePending) return response("busy")
            restorePending = true
            cachePreloader.cancel()
            // A game can close while preparation is still running: retain one restore request.
            writes.lock()
        } else if (!writes.tryLock()) return response("busy")
        return try {
            if (!authorized()) return response("denied")
            val pkg = request.game
            if (pkg != null && !gameAvailable(pkg)) return record("game_unavailable", request)
            record("running", request)
            var executed = false
            when (request.operation) {
                AutomationProtocol.Operation.SELECT_GAME -> select(request)
                AutomationProtocol.Operation.APPLY_SAVED,
                AutomationProtocol.Operation.APPLY_AND_LAUNCH,
                AutomationProtocol.Operation.ENABLE_MODE -> {
                    val desired = pkg?.let { settings.read(it).encode() }
                    val snapshot = context.getSharedPreferences("gaming_engine_prefs", Context.MODE_PRIVATE)
                        .getString("last_snapshot", null)?.let { GamingOptimizationSnapshot.fromJson(it) }
                    if (engine.sessionOpen.value || engine.recovery.value.isNeeded) {
                        record(if (engine.state.value is GamingModeState.Active &&
                            snapshot?.activeGamePackage == pkg && desired != null &&
                            context.getSharedPreferences("gaming_engine_prefs", Context.MODE_PRIVATE)
                                .getString("active_desired_settings", null) == desired) "already_applied"
                        else "restore_required", request)
                    } else {
                        // The session target is explicit; preparing it must not move the editing context.
                        executed = true
                        jobs.activate(pkg).join()
                        record(if (engine.state.value is GamingModeState.Active)
                            if (engine.report.value.unavailable.isEmpty() &&
                                (pkg == null || JSONObject(cachePreloader.status.value).optString("result") in
                                setOf("completed", "disabled", "byte_budget"))) "applied" else "partial"
                        else "failed", request)
                    }
                }
                AutomationProtocol.Operation.RESTORE,
                AutomationProtocol.Operation.DISABLE_MODE -> {
                    if (!engine.sessionOpen.value && !engine.recovery.value.isNeeded) {
                        record("nothing_to_restore", request)
                    } else {
                        executed = true
                        jobs.deactivate().join()
                        record(if (engine.sessionOpen.value || engine.recovery.value.isNeeded ||
                            engine.report.value.unavailable.isNotEmpty()) "partial" else "restored", request)
                    }
                }
                AutomationProtocol.Operation.LAUNCH_GAME -> record("launch_ready", request)
                // Routed to runDisplayRequest before the session lock; never reached here.
                AutomationProtocol.Operation.APPLY_DISPLAY,
                AutomationProtocol.Operation.RESTORE_DISPLAY -> response("failed")
                AutomationProtocol.Operation.QUERY_SELECTION,
                AutomationProtocol.Operation.QUERY_STATUS -> query(request.requestId)
            }.let { result ->
                if (request.operation in setOf(AutomationProtocol.Operation.SELECT_GAME,
                        AutomationProtocol.Operation.LAUNCH_GAME,
                        AutomationProtocol.Operation.QUERY_SELECTION, AutomationProtocol.Operation.QUERY_STATUS)) return@let result
                JSONObject(AutomationStatus.withRequest(result, request)).apply {
                    put("durationMs", SystemClock.elapsedRealtime() - startedAt)
                    if (executed) {
                        val report = JSONObject(com.google.gson.Gson().toJson(engine.report.value))
                        report.put("unavailable", org.json.JSONArray(engine.report.value.unavailable.map {
                            when (it) {
                                is com.catsmoker.app.screens.gaming.mode.GamingModeNotice.Raw -> it.text
                                is com.catsmoker.app.screens.gaming.mode.GamingModeNotice.Res ->
                                    context.getString(it.resId, *it.args.toTypedArray())
                            }
                        }))
                        put("operations", report)
                        put("error", (engine.state.value as? GamingModeState.Error)?.message ?: JSONObject.NULL)
                        if (!request.isRestore && pkg != null && engine.state.value is GamingModeState.Active) {
                            put("cache", JSONObject(cachePreloader.status.value).takeIf { it.optString("game") == pkg }
                                ?: JSONObject.NULL)
                        }
                    }
                    put("notRequested", org.json.JSONArray(listOf("fps_cap", "cache_cleanup",
                        "forced_idle", "kill_all", "always_finish_activities", "background_process_limit",
                        "physical_display_resolution", "game_file_deployment")))
                }.toString().also {
                    _status.value = it
                    prefs.edit().putString("last", it).apply()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            record("failed", request)
        } finally {
            writes.unlock()
            if (restore) restorePending = false
        }
    }

    /**
     * Applies or restores a display rate for an automation caller.
     *
     * The target is the display in use: an attached external display if there is one, otherwise
     * the built-in panel. A caller that names a rate gets exactly that rate; a caller that omits
     * `hz` gets the saved preference for its context, which is what makes "configure once, apply
     * from Tasker" work. Background callers are non-interactive on purpose, so a change that
     * would need the confirmation countdown is refused rather than made unconfirmable.
     */
    private suspend fun runDisplayRequest(
        request: AutomationProtocol.Request,
        authorized: () -> Boolean
    ): String {
        if (!authorized()) return response("denied")
        val startedAt = SystemClock.elapsedRealtime()
        record("running", request)
        val engine = displayEngine
        engine.refresh()
        val state = engine.state.value
        val target = state.external ?: state.builtIn

        val report = when {
            request.operation == AutomationProtocol.Operation.RESTORE_DISPLAY -> engine.restore()
            target == null -> DisplayApplyReport(refusedReason = "No display is available")
            else -> {
                val onExternal = state.external != null
                val requested = request.hz
                    ?: displayRates.resolve(request.game, onExternal, onExternal && isSamsung())
                if (requested == null) {
                    DisplayApplyReport(
                        refusedReason = "No refresh rate is saved for this context, and the request did not supply hz"
                    )
                } else {
                    engine.apply(target.id, requested, interactive = false)
                }
            }
        }
        return displayReceipt(request, report, SystemClock.elapsedRealtime() - startedAt)
    }

    private fun displayReceipt(
        request: AutomationProtocol.Request,
        report: DisplayApplyReport,
        durationMs: Long
    ): String {
        val code = if (report.applied) "applied" else "failed"
        val json = JSONObject(AutomationStatus.withRequest(response(code), request)).apply {
            put("durationMs", durationMs)
            put("display", JSONObject().apply {
                put("applied", report.applied)
                put("summary", report.summary)
                report.backend?.let { put("backend", it.name) }
                report.requestedHz?.let { put("requestedHz", it) }
                report.observedHz?.let { put("observedHz", it) }
                report.pacing?.let { put("pacing", it.name) }
                report.refusedReason?.let { put("refused", it) }
                if (report.details.isNotEmpty()) put("details", org.json.JSONArray(report.details))
                report.revertSeconds?.let { put("revertSeconds", it) }
            })
        }
        return json.toString().also {
            _status.value = it
            prefs.edit().putString("last", it).apply()
        }
    }

    /**
     * Admits a display request and runs it off the receiver thread.
     *
     * No foreground service is started for this family: a display change is one settings write
     * or one binder transaction, and it needs no long-running process to hold it. Android may
     * still stop the process mid-flight, which is exactly why the engine's snapshot carries a
     * restore record and its pending-confirmation flag survives a restart.
     */
    fun launchDisplay(request: AutomationProtocol.Request): String {
        scope.launch { runCatching { runRequest(request) } }
        return "accepted"
    }

    /** "Apply this rate" from the display UI, through the same engine the automation uses. */
    fun applyDisplayFromUi(displayId: Int, hz: Float) = scope.async {
        runCatching { displayEngine.apply(displayId, hz, interactive = true) }.getOrNull()
    }

    /** Restore every display CatSmoker changed, from the UI's own button. */
    fun restoreDisplayFromUi() = scope.async {
        runCatching { displayEngine.restore() }.getOrNull()
    }

    fun record(code: String, request: AutomationProtocol.Request? = null): String =
        (request?.let { AutomationStatus.withRequest(response(code), it) } ?: response(code)).also {
            _status.value = it
            prefs.edit().putString("last", it).apply()
        }

    companion object {
        fun response(code: String): String = AutomationStatus.response(code)
    }
}
