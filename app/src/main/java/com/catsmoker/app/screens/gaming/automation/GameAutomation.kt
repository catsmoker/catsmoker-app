package com.catsmoker.app.screens.gaming.automation

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import com.catsmoker.app.screens.gaming.cache.GameCachePreloader
import com.catsmoker.app.screens.gaming.mode.GamingOptimizationSnapshot

@Singleton
class GameAutomation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: AutomationAccess,
    private val settings: GameSettingsStore,
    private val engineProvider: dagger.Lazy<GamingEngine>,
    private val jobsProvider: dagger.Lazy<GamingModeJobRunner>,
    private val modeObservation: GamingModeObservation,
    private val cachePreloader: GameCachePreloader
) {
    // Reject unauthenticated entry points before constructing a privileged engine.
    private val engine: GamingEngine get() = engineProvider.get()
    private val jobs: GamingModeJobRunner get() = jobsProvider.get()
    private val prefs = context.getSharedPreferences("game_automation_status", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writes = Mutex()
    private var restorePending = false
    private val _status = MutableStateFlow(AutomationStatus.afterProcessStart(prefs.getString("last", null)))
    val status = _status.asStateFlow()
    private var lastDelivery = AutomationStatus.afterProcessStart(prefs.getString("last_delivery", null))

    fun parse(intent: Intent): AutomationProtocol.Request? = runCatching {
        if (intent.action != AutomationProtocol.ACTION || intent.data != null ||
            intent.clipData != null || intent.selector != null) return null
        if (intent.extras?.keySet()?.any { it !in AutomationProtocol.keys } == true) return null
        if (!access.accepts(intent.getStringExtra("token")) || !consented()) return null
        AutomationProtocol.parse(intent.getStringExtra("operation"), intent.getStringExtra("game"))
    }.getOrNull()

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

    fun query(): String = JSONObject().apply {
        val live = modeObservation.current()
        val recorded = context.getSharedPreferences("gaming_engine_prefs", Context.MODE_PRIVATE)
        val recordedSession = recorded.getBoolean("is_active", false) ||
            !recorded.getStringSet("affected_pkgs", emptySet()).isNullOrEmpty() ||
            !recorded.getStringSet("appops_prior", emptySet()).isNullOrEmpty()
        put("version", 1)
        put("selectedGame", settings.selection.value.packageName ?: JSONObject.NULL)
        put("mode", live?.mode ?: if (recordedSession) "RecordedSession" else "Idle")
        put("modeObserved", live != null)
        put("sessionOpen", live?.sessionOpen ?: recordedSession)
        put("recoveryRequired", live?.recoveryRequired ?: recordedSession)
        put("unavailableCount", live?.unavailableCount ?: JSONObject.NULL)
        put("last", JSONObject(status.value))
        put("lastDelivery", JSONObject(lastDelivery))
        put("cache", JSONObject(cachePreloader.status.value))
    }.toString()

    fun select(request: AutomationProtocol.Request): String {
        val pkg = request.game ?: return response("invalid_request")
        if (!gameAvailable(pkg)) return response("game_unavailable")
        settings.select(pkg)
        return record("selected")
    }

    /** Process-owned; Activity destruction must not cancel a half-written engine session. */
    fun execute(intent: Intent) = scope.async {
        val request = parse(intent) ?: return@async response("denied")
        runRequest(request) { parse(intent) != null }.also { result ->
            // A rejected concurrent request must be observable without overwriting the active run.
            lastDelivery = JSONObject().put("version", 1).put("operation", request.operation.name)
                .put("game", request.game ?: JSONObject.NULL)
                .put("result", JSONObject(result).optString("result", "query"))
                .put("time", System.currentTimeMillis()).toString()
            prefs.edit().putString("last_delivery", lastDelivery).apply()
        }
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
        val restore = request.operation in setOf(AutomationProtocol.Operation.RESTORE, AutomationProtocol.Operation.DISABLE_MODE)
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
            if (pkg != null && !gameAvailable(pkg)) return record("game_unavailable")
            record("running")
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
                        else "restore_required")
                    } else {
                        // The session target is explicit; preparing it must not move the editing context.
                        jobs.activate(pkg).join()
                        record(if (engine.state.value is GamingModeState.Active)
                            if (engine.report.value.unavailable.isEmpty() &&
                                (pkg == null || JSONObject(cachePreloader.status.value).optString("result") in
                                setOf("completed", "disabled", "byte_budget"))) "applied" else "partial"
                        else "failed")
                    }
                }
                AutomationProtocol.Operation.RESTORE,
                AutomationProtocol.Operation.DISABLE_MODE -> {
                    if (!engine.sessionOpen.value && !engine.recovery.value.isNeeded) {
                        record("nothing_to_restore")
                    } else {
                        jobs.deactivate(ownedOnly = true).join()
                        record(if (engine.sessionOpen.value || engine.recovery.value.isNeeded ||
                            engine.report.value.unavailable.isNotEmpty()) "partial" else "restored")
                    }
                }
                AutomationProtocol.Operation.LAUNCH_GAME -> record("launch_ready")
                AutomationProtocol.Operation.QUERY_SELECTION,
                AutomationProtocol.Operation.QUERY_STATUS -> query()
            }.let { result ->
                if (request.operation in setOf(AutomationProtocol.Operation.SELECT_GAME,
                        AutomationProtocol.Operation.LAUNCH_GAME,
                        AutomationProtocol.Operation.QUERY_SELECTION, AutomationProtocol.Operation.QUERY_STATUS)) return@let result
                JSONObject(result).apply {
                    put("operation", request.operation.name)
                    put("game", pkg ?: JSONObject.NULL)
                    val report = JSONObject(com.google.gson.Gson().toJson(engine.report.value))
                    report.put("unavailable", org.json.JSONArray(engine.report.value.unavailable.map {
                        when (it) {
                            is com.catsmoker.app.screens.gaming.mode.GamingModeNotice.Raw -> it.text
                            is com.catsmoker.app.screens.gaming.mode.GamingModeNotice.Res ->
                                context.getString(it.resId, *it.args.toTypedArray())
                        }
                    }))
                    put("operations", report)
                    put("notRequested", org.json.JSONArray(listOf("fps_cap", "cache_cleanup",
                        "forced_idle", "kill_all", "always_finish_activities", "background_process_limit",
                        "physical_display_resolution", "game_file_deployment")))
                    put("cache", JSONObject(cachePreloader.status.value))
                    put("error", (engine.state.value as? GamingModeState.Error)?.message ?: JSONObject.NULL)
                }.toString().also {
                    _status.value = it
                    prefs.edit().putString("last", it).apply()
                }
            }
        } catch (_: Exception) {
            record("failed")
        } finally {
            writes.unlock()
            if (restore) restorePending = false
        }
    }

    fun record(code: String): String = response(code).also {
        _status.value = it
        prefs.edit().putString("last", it).apply()
    }

    companion object {
        fun response(code: String): String = AutomationStatus.response(code)
    }
}
