package com.catsmoker.app.screens.gaming.display

import com.catsmoker.app.platform.device.isSamsung
import com.catsmoker.app.platform.display.AttachedDisplay
import com.catsmoker.app.platform.display.DisplayModeCatalog
import com.catsmoker.app.platform.display.DisplayRateMath
import com.catsmoker.app.platform.display.FramePacing
import com.catsmoker.app.screens.gaming.mode.RefreshRateController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the display engine currently sees.
 *
 * @param appRenderHz the rate applications are rendering at, when the metrics engine is
 *   producing one. Reported apart from [AttachedDisplay.currentHz] on purpose: the monitor
 *   running at 120 Hz and the app rendering at 120 frames do not follow from each other, and
 *   presenting them as one number is the fake-FPS claim this feature must not make.
 * @param revertSeconds the countdown left on a risky change, or null when nothing is pending.
 */
data class DisplayEngineState(
    val displays: List<AttachedDisplay> = emptyList(),
    val probe: DisplayProbe = DisplayProbe(),
    val snapshot: DisplaySnapshot? = null,
    val revertSeconds: Int? = null,
    val lastReport: DisplayApplyReport? = null,
    val backendIncludesHiddenInterfaces: Boolean = false
) {
    val builtIn: AttachedDisplay? get() = displays.firstOrNull { it.isDefault }
    val external: AttachedDisplay? get() = displays.firstOrNull { it.isExternal && it.isOn }
}

/**
 * The outcome of one attempt, in the terms the UI and the automation report use.
 *
 * @param applied whether the requested rate was observed afterwards — not whether a call was
 *   accepted. A mechanism that returns success and changes nothing reports `applied = false`.
 * @param observedHz the rate the display was actually running at when the report was made.
 */
data class DisplayApplyReport(
    val requestedHz: Float? = null,
    val backend: DisplayBackend? = null,
    val applied: Boolean = false,
    val observedHz: Float? = null,
    val pacing: FramePacing? = null,
    val refusedReason: String? = null,
    val details: List<String> = emptyList(),
    val revertSeconds: Int? = null
) {
    /** One line for the automation's `status` payload and the UI's summary row. */
    val summary: String
        get() = when {
            refusedReason != null -> "refused: $refusedReason"
            applied && requestedHz != null -> "${DisplayPlanner.formatHz(requestedHz)} Hz via ${backend ?: "settings"}"
            applied -> "applied via ${backend ?: "settings"}"
            else -> "not applied"
        }
}

/**
 * The capability-based refresh-rate engine.
 *
 * Its job is ordering, not mechanism. The mechanisms are one settings pair, one composer policy
 * and one vendor switch, and none of them works everywhere: the settings rows cannot switch a
 * mode, the composer policy needs `ACCESS_SURFACE_FLINGER`, and Samsung's DeX switch only lifts
 * DeX's own filter. So the engine plans per device (see [DisplayPlanner]), tries the plan, then
 * **re-reads the display** and reports what actually happened. Every write is preceded by a
 * snapshot of what was there, and a mode switch — the change that can blank a monitor — carries
 * a countdown that puts it back unless the user keeps it.
 */
@Singleton
class DisplayEngine @Inject constructor(
    private val catalog: DisplayModeCatalog,
    private val refreshRates: com.catsmoker.app.platform.display.DisplayRefreshRateProvider,
    private val refreshController: RefreshRateController,
    private val backend: VariantDisplayBackend,
    private val snapshots: DisplaySnapshotStore
) {
    private val _state = MutableStateFlow(DisplayEngineState())
    val state: StateFlow<DisplayEngineState> = _state.asStateFlow()

    private var revertJob: Job? = null

    /** The countdown's own scope, so it survives the caller that started it going away. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Re-reads the displays and the privileged probe; the answer changes on every hotplug. */
    suspend fun refresh() {
        val probe = backend.probe()
        _state.update {
            it.copy(
                displays = catalog.read(),
                probe = probe,
                snapshot = snapshots.load(),
                backendIncludesHiddenInterfaces = backend.usesHiddenInterfaces
            )
        }
    }

    /**
     * Requests [rateHz] on the display with [displayId].
     *
     * @param interactive true when a person is watching and can answer the countdown. A
     *   background automation request passes false: it cannot confirm, so a change that would
     *   need confirmation is refused rather than made unrecoverable.
     */
    suspend fun apply(displayId: Int, rateHz: Float, interactive: Boolean): DisplayApplyReport {
        refresh()
        val target = catalog.read().firstOrNull { it.id == displayId }
            ?: return report(DisplayApplyReport(refusedReason = "That display is not connected"))

        val canWriteSettings = refreshController.canWrite()
        val plan = DisplayPlanner.plan(
            target = target,
            rateHz = rateHz,
            probe = _state.value.probe,
            canWriteSettings = canWriteSettings,
            isDeXCapable = isSamsung()
        )
        if (plan is DisplayPlan.Refused) return report(DisplayApplyReport(refusedReason = plan.reason))

        val risky = plan is DisplayPlan.Composer && plan.switchesMode
        if (risky && !interactive) {
            return report(
                DisplayApplyReport(
                    refusedReason = "Switching this display's mode needs the confirmation countdown, " +
                        "which a background request cannot show. Change it from the app first."
                )
            )
        }

        recordSnapshot(target, plan)

        val outcome = when (plan) {
            is DisplayPlan.Composer -> applyComposer(target, plan)
            is DisplayPlan.Settings -> applySettings(target, plan)
            is DisplayPlan.DeX -> applyDeX(target, plan)
            is DisplayPlan.Refused -> DisplayApplyReport(refusedReason = plan.reason)
        }
        if (outcome.applied && risky) startCountdown() else if (!outcome.applied) rollBackSilently()
        return report(outcome)
    }

    /**
     * Puts everything in the snapshot back.
     *
     * Also the emergency reset: it reads the snapshot from disk rather than from memory, so it
     * works from a cold start after the process that made the change is gone.
     */
    suspend fun restore(): DisplayApplyReport {
        revertJob?.cancel()
        revertJob = null
        val snapshot = snapshots.load()
            ?: return report(DisplayApplyReport(refusedReason = "There is no saved display state to restore"))

        val details = mutableListOf<String>()

        // Composer first: a stored mode is what a blanked external display needs back.
        for ((displayId, stored) in snapshot.displayModes) {
            val outcome = backend.applyModeSpecs(
                displayId = displayId.toLong(),
                modeId = stored.modeId,
                minHz = stored.refreshRateHz,
                maxHz = stored.refreshRateHz
            )
            details += "display $displayId -> mode ${stored.modeId}: " +
                if (outcome.ok) "restored" else "not restored (${outcome.detail})"
        }

        // The pacesetter override is last-in first-out: clear it before the modes settle.
        if (snapshot.forcedPacesetter != null || snapshot.dexHighResolution != null) {
            val pacesetter = backend.setPacesetter(snapshot.forcedPacesetter)
            details += if (snapshot.forcedPacesetter == null) {
                "pacesetter: " + if (pacesetter.ok) "cleared" else "not cleared (${pacesetter.detail})"
            } else {
                "pacesetter: repinned to ${snapshot.forcedPacesetter}"
            }
            snapshot.dexHighResolution?.let { wasEnabled ->
                val dex = backend.setDeXHighResolution(wasEnabled)
                details += "DeX high refresh: " + if (dex.ok) "set to $wasEnabled" else "not restored (${dex.detail})"
            }
        }

        val ratesOk = refreshController.restoreRates(
            minHz = snapshot.minRefreshRate?.toFloatOrNull(),
            peakHz = snapshot.peakRefreshRate?.toFloatOrNull()
        )
        if (snapshot.minRefreshRate != null || snapshot.peakRefreshRate != null) {
            details += if (ratesOk) "refresh-rate rows restored" else "refresh-rate rows not restored"
        }

        snapshots.clear()
        return report(
            DisplayApplyReport(
                applied = true,
                details = details,
                observedHz = catalog.read().firstOrNull { it.isDefault }?.currentHz
            )
        )
    }

    /** Keeps a pending change and drops the countdown. */
    suspend fun confirm() {
        revertJob?.cancel()
        revertJob = null
        snapshots.load()?.let { snapshots.save(it.copy(pendingConfirm = false)) }
        _state.update { it.copy(revertSeconds = null, snapshot = snapshots.load()) }
    }

    /**
     * Rolls back a change that was never confirmed — called at startup.
     *
     * The stored `pendingConfirm` flag is the only evidence left after a process death, and a
     * change nobody saw working must not be adopted silently.
     */
    suspend fun recoverIfNeeded(): DisplayApplyReport? {
        val snapshot = snapshots.load() ?: return null
        if (!snapshot.pendingConfirm) return null
        val report = restore()
        return report.copy(details = report.details + "previous change was not confirmed")
    }

    // ------------------------------------------------------------------ mechanisms

    private suspend fun applyComposer(target: AttachedDisplay, plan: DisplayPlan.Composer): DisplayApplyReport {
        val outcome = backend.applyModeSpecs(
            displayId = plan.displayId,
            modeId = plan.mode.id,
            minHz = plan.mode.refreshRateHz,
            maxHz = plan.mode.refreshRateHz
        )
        if (!outcome.ok) {
            return report(
                DisplayApplyReport(
                    requestedHz = plan.mode.refreshRateHz,
                    backend = DisplayBackend.DISPLAY_MODE_SPECS,
                    refusedReason = outcome.detail ?: "The composer refused the mode change"
                )
            )
        }
        // Re-read: the composer accepting a policy is not the display being in the mode.
        val observed = catalog.read().firstOrNull { it.id == target.id }?.currentHz
        val landed = observed != null &&
            kotlin.math.abs(observed - plan.mode.refreshRateHz) <= DisplayRateMath.HZ_TOLERANCE
        return report(
            DisplayApplyReport(
                requestedHz = plan.mode.refreshRateHz,
                backend = DisplayBackend.DISPLAY_MODE_SPECS,
                applied = landed,
                observedHz = observed,
                pacing = plan.pacing,
                details = buildList {
                    outcome.readBack?.let { add("composer policy $it") }
                    add("mode ${plan.mode.id} at ${plan.mode.width}x${plan.mode.height}")
                    if (!landed) add("the display is still at ${observed?.let { DisplayPlanner.formatHz(it) } ?: "an unknown rate"}")
                },
                refusedReason = if (landed) null else "The display did not switch to that mode"
            )
        )
    }

    private suspend fun applySettings(target: AttachedDisplay, plan: DisplayPlan.Settings): DisplayApplyReport {
        val result = refreshController.writeRates(minHz = plan.rateHz, peakHz = plan.rateHz)
        val observed = catalog.read().firstOrNull { it.id == target.id }?.currentHz
        val landed = result.minOk || result.peakOk
        return report(
            DisplayApplyReport(
                requestedHz = plan.rateHz,
                backend = DisplayBackend.STANDARD_SETTINGS,
                applied = landed,
                observedHz = observed,
                pacing = plan.pacing,
                details = buildList {
                    if (result.minOk) add("min_refresh_rate = ${DisplayPlanner.formatHz(plan.rateHz)}")
                    if (result.peakOk) add("peak_refresh_rate = ${DisplayPlanner.formatHz(plan.rateHz)}")
                    observed?.let { add("panel now at ${DisplayPlanner.formatHz(it)} Hz") }
                    add("the settings rows ask; the display manager may still override them")
                },
                refusedReason = if (landed) null else "The device did not accept the refresh-rate rows"
            )
        )
    }

    private suspend fun applyDeX(target: AttachedDisplay, plan: DisplayPlan.DeX): DisplayApplyReport {
        val details = mutableListOf<String>()
        val lift = backend.setDeXHighResolution(true)
        if (!lift.ok) {
            return report(
                DisplayApplyReport(
                    requestedHz = plan.rateHz,
                    backend = DisplayBackend.SAMSUNG_DEX_HIGH_RES,
                    refusedReason = lift.detail ?: "DeX refused to lift its refresh filter"
                )
            )
        }
        details += "DeX high-refresh modes enabled"

        plan.rateHz?.let { rate ->
            val mode = DisplayRateMath.modeAtRate(target.modes, rate)
            if (mode != null) {
                val pin = backend.applyModeSpecs(plan.monitorId, mode.id, rate, rate)
                details += if (pin.ok) {
                    "monitor pinned to mode ${mode.id} (${DisplayPlanner.formatHz(mode.refreshRateHz)} Hz)"
                } else {
                    "monitor mode not pinned (${pin.detail})"
                }
            }
        }

        val pace = backend.setPacesetter(plan.monitorId)
        details += if (pace.ok) "applications now pace on the monitor" else "pacesetter left alone (${pace.detail})"

        val observed = catalog.read().firstOrNull { it.isExternal && it.isOn }?.currentHz
        val landed = lift.ok && (pace.ok || plan.rateHz == null)
        return report(
            DisplayApplyReport(
                requestedHz = plan.rateHz,
                backend = DisplayBackend.SAMSUNG_DEX_HIGH_RES,
                applied = landed,
                observedHz = observed,
                pacing = plan.pacing,
                details = details,
                refusedReason = if (landed) null else "DeX accepted the filter but the monitor did not move"
            )
        )
    }

    // ------------------------------------------------------------------ safety

    /**
     * Records what is there before writing anything.
     *
     * Existing values are kept rather than re-read, so a second change does not snapshot the
     * first one's result and then "restore" to it.
     */
    private fun recordSnapshot(target: AttachedDisplay, plan: DisplayPlan) {
        val existing = snapshots.load() ?: DisplaySnapshot(recordedAt = System.currentTimeMillis())
        val modes = existing.displayModes.toMutableMap()
        val active = target.activeMode
        if (active != null && plan is DisplayPlan.Composer) {
            modes.putIfAbsent(target.id, StoredMode(active.id, active.refreshRateHz))
        }
        snapshots.save(
            existing.copy(
                minRefreshRate = existing.minRefreshRate ?: refreshController.readMinHz()?.toString(),
                peakRefreshRate = existing.peakRefreshRate ?: refreshController.readPeakHz()?.toString(),
                displayModes = modes,
                dexHighResolution = existing.dexHighResolution
                    ?: if (plan is DisplayPlan.DeX) false else null,
                pendingConfirm = plan is DisplayPlan.Composer && plan.switchesMode
            )
        )
    }

    /** Arms the countdown for a mode switch: unconfirmed means rolled back. */
    private fun startCountdown() {
        revertJob?.cancel()
        revertJob = scope.launch {
            var remaining = CONFIRM_SECONDS
            while (remaining > 0) {
                _state.update { it.copy(revertSeconds = remaining) }
                delay(1_000)
                remaining--
            }
            // Only undo what is still pending: a confirm during the last second wins the race.
            if (snapshots.load()?.pendingConfirm == true) restore()
            _state.update { it.copy(revertSeconds = null) }
        }
    }

    /** A change that did not land leaves nothing to restore; drop what it recorded. */
    private fun rollBackSilently() {
        val snapshot = snapshots.load() ?: return
        if (!snapshot.pendingConfirm) return
        snapshots.save(snapshot.copy(pendingConfirm = false))
    }

    private fun report(report: DisplayApplyReport): DisplayApplyReport {
        _state.update { it.copy(lastReport = report, snapshot = snapshots.load()) }
        return report
    }

    /** Peak rate of the built-in panel, so the UI can say what "max" means on this phone. */
    fun panelPeakHz(): Float = refreshRates.getMaxHardwareRefreshRate()

    private companion object {
        /** How long a mode switch waits for the user before it is undone. */
        const val CONFIRM_SECONDS = 20
    }
}
