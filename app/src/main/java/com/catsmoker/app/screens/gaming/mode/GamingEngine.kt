package com.catsmoker.app.screens.gaming.mode

import com.catsmoker.app.R

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import com.catsmoker.app.features.gamingtools.engine.GamingNotificationListener
import com.catsmoker.app.platform.device.DeviceCapabilities
import com.catsmoker.app.platform.device.isVivoOrIqoo
import com.catsmoker.app.platform.shell.ShellRunner
import com.catsmoker.app.screens.gaming.apps.AppOpsRecord
import com.catsmoker.app.screens.gaming.apps.AppOpsVerdict
import com.catsmoker.app.screens.gaming.apps.SuspendExemptStore
import com.catsmoker.app.screens.gaming.apps.SuspendListStore
import com.catsmoker.app.screens.gaming.apps.SuspendSweep
import com.catsmoker.app.screens.gaming.apps.SuspendVerdict
import com.catsmoker.app.screens.gaming.compilation.ArtCompiler
import com.catsmoker.app.screens.gaming.compilation.BoosterHistoryStore
import com.catsmoker.app.screens.gaming.compilation.BoosterLogEntry
import com.catsmoker.app.screens.gaming.compilation.BoosterLogType
import com.catsmoker.app.screens.gaming.compilation.BoosterOutcome
import com.catsmoker.app.screens.gaming.compilation.BoosterPackageClassifier
import com.catsmoker.app.screens.gaming.compilation.BoosterRun
import com.catsmoker.app.screens.gaming.compilation.BoosterState
import com.catsmoker.app.screens.gaming.compilation.CompileSkipReason
import com.catsmoker.app.screens.gaming.compilation.DexoptStatusParser
import com.catsmoker.app.screens.gaming.compilation.SingleCompileResult
import com.catsmoker.app.screens.gaming.compilation.analyzeDexoptStatuses
import com.catsmoker.app.screens.gaming.compilation.appendBoosterLogEntry
import com.catsmoker.app.screens.gaming.compilation.evaluateOptimization
import com.catsmoker.app.screens.gaming.compilation.shouldCompilePackage
import com.catsmoker.app.screens.gaming.graphics.AnimationScaleKind
import com.catsmoker.app.screens.gaming.graphics.GameInterventions
import com.catsmoker.app.screens.gaming.mode.GamingOptimizationSnapshot
import com.catsmoker.app.screens.gaming.mode.SettingValue
import com.catsmoker.app.screens.gaming.network.BackgroundDataRestrictor
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GamingEngine(
    private val context: Context,
    private val shellRunner: ShellRunner,
    private val backgroundDataRestrictor: BackgroundDataRestrictor,
    private val gameInterventions: GameInterventions,
    private val suspendListStore: SuspendListStore,
    private val suspendExemptStore: SuspendExemptStore,
    private val refreshController: RefreshRateController
) {
    private val _state = MutableStateFlow<GamingModeState>(GamingModeState.Idle)
    val state: StateFlow<GamingModeState> = _state.asStateFlow()

    /**
     * What a previous, unfinished session left behind.
     *
     * Separate from [state] because it answers a different question: the state says whether a
     * gaming session is running, this says whether anything is waiting to be restored. The card
     * shows its recovery control only from this flow, so an active session can never present a
     * button that would wake the apps it is deliberately suspending.
     */
    private val _recovery = MutableStateFlow(GamingModeRecovery())
    val recovery: StateFlow<GamingModeRecovery> = _recovery.asStateFlow()

    /**
     * Whether a Gaming Mode session record is still open.
     *
     * The power control cannot read this from [state] alone: an activation error after the session
     * opened is an [GamingModeState.Error], not [GamingModeState.Active], yet starting another
     * session would record the half-applied values as the user's own. This flow is written at the
     * same points `is_active` is, so the button always offers the action the engine can actually
     * perform. `prefs` is declared later in this class, so the persisted flag is read in `init`
     * (which runs before any collector can observe this flow) rather than in this initializer.
     */
    private val _sessionOpen = MutableStateFlow(false)
    val sessionOpen: StateFlow<Boolean> = _sessionOpen.asStateFlow()

    /**
     * Packages this app verified as suspended, kept for the suspension list's status badges.
     *
     * It is the session's own confirmed record — never a live "everything frozen on the device"
     * list — so an app frozen by another tool is not claimed as ours.
     */
    private val _suspendedPackages = MutableStateFlow<Set<String>>(emptySet())
    val suspendedPackages: StateFlow<Set<String>> = _suspendedPackages.asStateFlow()

    private val _report = MutableStateFlow(GamingModeReport())
    val report: StateFlow<GamingModeReport> = _report.asStateFlow()

    /**
     * Whether [GamingNotificationListener] should be cancelling notifications right now.
     *
     * Read by the listener (which the system binds in this same process whenever the user has
     * granted notification access), set only by activation, deactivation and the boot-time
     * restore of a persisted active mode — never by the listener itself.
     */
    private val _notificationSuppressionActive = MutableStateFlow(false)
    val notificationSuppressionActive: StateFlow<Boolean> = _notificationSuppressionActive.asStateFlow()

    private val _isFixedPerformanceMode = MutableStateFlow(value = false)
    val isFixedPerformanceMode: StateFlow<Boolean> = _isFixedPerformanceMode.asStateFlow()

    private val _alwaysFinishActivities = MutableStateFlow(false)
    val alwaysFinishActivities: StateFlow<Boolean> = _alwaysFinishActivities.asStateFlow()

    private val _backgroundProcessLimit = MutableStateFlow(false)
    val backgroundProcessLimit: StateFlow<Boolean> = _backgroundProcessLimit.asStateFlow()

    /**
     * The render-scale the next activation writes into the `game_overlay` intervention, or null
     * for the FPS-only reference format.
     *
     * A pure preference — choosing it touches no setting — so the setter needs no privilege and
     * takes effect on the next activation, never mid-session (re-writing the table under a
     * running game would need a restart to take hold anyway). `0` in prefs means off, since 0
     * is outside the official 0.3..0.9 range and can never be a real choice.
     */
    private val _interventionDownscale = MutableStateFlow<Float?>(null)
    val interventionDownscale: StateFlow<Float?> = _interventionDownscale.asStateFlow()

    /**
     * The touch-speed choice for the next activation (`pointer_speed`, -7..+7), or null for
     * stock (the setting is then never touched).
     *
     * A pure preference like the render scale and frame cap: choosing it touches no setting,
     * takes effect on the next activation, never mid-session. `0` in prefs means stock, since
     * stock is exactly what "don't touch it" produces.
     */
    private val _pointerSpeedChoice = MutableStateFlow<Int?>(null)
    val pointerSpeedChoice: StateFlow<Int?> = _pointerSpeedChoice.asStateFlow()

    /**
     * Static device facts, resolved once: which mechanisms this device can honor.
     * Device facts never change at runtime, so there is no reason to re-probe them
     * per activation — and every version gate below reads from here instead of
     * scattering `Build.VERSION` checks.
     */
    private val deviceCapabilities: DeviceCapabilities.Flags by lazy {
        DeviceCapabilities.detect(
            DeviceCapabilities.DeviceInfo(
                manufacturer = Build.MANUFACTURER,
                brand = Build.BRAND,
                hardware = Build.HARDWARE,
                board = Build.BOARD,
                sdkInt = Build.VERSION.SDK_INT
            )
        )
    }

    private val _animationScales = MutableStateFlow(Triple(1f, 1f, 1f))
    val animationScales: StateFlow<Triple<Float, Float, Float>> = _animationScales.asStateFlow()


    /**
     * Serializes Gaming Mode toggles.
     *
     * The switch can be driven from three places at once — the in-app card, the foreground
     * service's notification Stop button, and `onTaskRemoved` when the app is swiped away — and
     * enable/disable both read-modify-write the same `affected_pkgs` record. Interleaved, an
     * enable's suspends land after a disable's clear and no later revert knows about them, which
     * leaves apps frozen with an empty record: exactly the "still suspended after turning it
     * off" leak.
     */
    private val toggleMutex = Mutex()


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    private val prefs = context.getSharedPreferences("gaming_engine_prefs", Context.MODE_PRIVATE)

    private val compiler = ArtCompiler(context, shellRunner, prefs, scope, ::hasInventoryConsent)
    val boosterLog = compiler.boosterLog
    val boosterLogEntries = compiler.boosterLogEntries
    val boosterState = compiler.boosterState
    val boosterHistory = compiler.boosterHistory

    suspend fun runArtOptimization(mode: String = "speed-profile", force: Boolean = false) =
        compiler.runArtOptimization(mode, force)

    suspend fun runSinglePackageOptimization(pkg: String): SingleCompileResult =
        compiler.runSinglePackageOptimization(pkg)

    suspend fun cancelArtOptimization() = compiler.cancelArtOptimization()

    fun cancelArtOptimizationNow(): Boolean = compiler.cancelArtOptimizationNow()

    /** Routing for the engine's own log lines; the compiler owns the single log sink. */
    private fun logBooster(type: BoosterLogType, pkg: String?, msg: String) =
        compiler.logBooster(type, pkg, msg)

    init {
        val sessionOpen = prefs.getBoolean("is_active", false)
        val hasSessionRecord =
            !prefs.getStringSet("affected_pkgs", emptySet()).isNullOrEmpty() ||
                !prefs.getStringSet("appops_prior", emptySet()).isNullOrEmpty()
        // A record without the flag is still an open session (older builds deleted the flag before
        // verifying), so the power button must offer the revert for it too.
        _sessionOpen.value = sessionOpen || hasSessionRecord
        val isFixedPerf = prefs.getBoolean("fixed_perf_manual", false)
        _interventionDownscale.value =
            GameInterventions.sanitizeDownscale(prefs.getFloat("intervention_downscale", 0f))
        _pointerSpeedChoice.value = PointerSpeed.sanitize(prefs.getInt("pointer_speed_choice", 0))
        
        if (sessionOpen || hasSessionRecord) {
            // Never claim Active from the flag alone: the process that wrote it is gone. Startup
            // reconciliation reads the boot count and the package manager's own suspension state,
            // then publishes either a clean Idle or an Interrupted session with recovery evidence.
            _state.value = GamingModeState.Checking
            _recovery.value = GamingModeRecovery(isChecking = true)
            _isFixedPerformanceMode.value = sessionOpen && isFixedPerf
            scope.launch { reconcilePersistedSession() }
        } else if (isFixedPerf) {
            _isFixedPerformanceMode.value = true
            scope.launch { reapplyFixedPerformanceMode() }
        }

        _alwaysFinishActivities.value = getAlwaysFinishActivities() == 1
        _backgroundProcessLimit.value = getGlobalString("activity_manager_constants")?.contains("max_cached_processes=1") == true

        refreshAnimationScales()

        scope.launch { compiler.loadHistory() }
    }

    // Single-purpose reader: every caller reads the always-finish flag, so there is
    // no key parameter to warn about.
    private fun getAlwaysFinishActivities(): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ALWAYS_FINISH_ACTIVITIES, 0)
        } catch (_: Exception) { 0 }
    }

    /**
     * Whether the user has granted this app notification access, which is what decides the
     * system will ever bind [GamingNotificationListener]. Reported as the difference between
     * "armed" and "not applicable" in [GamingModeReport.notificationSuppression].
     */
    private fun isNotificationListenerEnabled(): Boolean =
        com.catsmoker.app.application.privacy.GamingAccessConsent.allows(context,
            com.catsmoker.app.application.privacy.GamingAccessConsent.NOTIFICATIONS) &&
        androidx.core.app.NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)

    /**
     * Re-binds [GamingNotificationListener] with a disable/enable pulse on its component.
     *
     * Best-effort and silent by design: a refused toggle must not fail activation, and there
     * is nothing to report either way — the arming read right after says whether the listener
     * is granted, which is the only fact the report carries.
     */
    private suspend fun pulseNotificationListenerBinding() {
        runCatching {
            val component = ComponentName(context, GamingNotificationListener::class.java)
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            delay(100)
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun getGlobalString(key: String): String? {
        return Settings.Global.getString(context.contentResolver, key)
    }

    val googleSafeToSuspend = listOf(
        "com.google.android.youtube",
        "com.google.android.apps.photos",
        "com.google.android.apps.maps",
        "com.google.android.gm",
        "com.google.android.apps.messaging",
        "com.google.android.calendar",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.bard",
        "com.google.android.apps.nbu.files",
        "com.google.android.apps.wellbeing",
        "com.google.android.projection.gearhead",
        "com.google.android.apps.authenticator2",
        "com.google.android.apps.restore",
        "com.android.chrome"
    )

    val systemCritical = listOf(
        "com.vivo.pem",                // Power Event Manager
        "com.vivo.abe",                // App Behavior Engine
        "com.vivo.daemonService",      // Hardware daemon
        "com.vivo.sps",                // System Power Service
        "com.vivo.pie",                // Framework extension
        "com.vivo.fingerprintui",
        "com.vivo.fingerprint",
        "com.vivo.fingerprintvit",
        "com.vivo.faceui",
        "com.vivo.faceunlock",
        "com.vivo.systemuiplugin",
        "com.vivo.networkstate",
        "com.vivo.connbase",
        "com.android.systemui",
        "com.android.phone",
        "com.mediatek.ims"              // VoLTE
    )

    val gamingDaemons = listOf(
        "com.vivo.gamecube",
        "com.vivo.gamewatch",
        "com.vivo.game",
        "com.iqoo.powersaving",        // Prevents thermal throttling
        "com.microsoft.deviceintegrationservice"  // ThermalInfoService bridge
    )

    

    // The vivo whitelist keys moved to [OemPackageResolver] (with two more the reference writes
    // and this engine used to skip); the constants there are the same strings these keys were.
    private val vivoGameCubeApps = OemPackageResolver.KEY_GAME_CUBE_APPS
    private val vivoSpeedModeApps = OemPackageResolver.KEY_SPEED_MODE_APPS
    private val vivoHighRefreshRateApps = OemPackageResolver.KEY_VIVO_HIGH_REFRESH_RATE_APPS
    private val vivoScreenRefreshRateAppsList = OemPackageResolver.KEY_VIVO_SCREEN_REFRESH_RATE_APPS_LIST

    private val hardWhitelist = setOf(
        context.packageName,            // Catsmoker itself — must never suspend itself
        "moe.shizuku.privileged.api",
        "moe.shizuku.client",           // Shizuku client/app
        "com.topjohnwu.magisk",
        "com.topjohnwu.magisk.alpha",   // Magisk Alpha
        "org.matrix.vector.manager",    // Vector (Xposed framework manager, related to LSPosed)
        "org.lsposed.manager",          // LSPosed manager
        "me.weishu.kernelsu",           // KernelSU
        "me.weishu.kernelsu.next",      // KernelSU Next
        "me.bmax.apatch",               // APatch
        "moe.yuki.su",                  // YukiSU
        "com.kitsune.mask",             // Kitsune Mask
        "com.termux",                   // Termux
        "com.termux.api",               // Termux API
        "com.termux.boot",              // Termux Boot
        "xyz.ladb",                     // LADB
        "eu.kanade.ladb",               // LADB (legacy)
        "com.bugjaeger",                // Bugjaeger
        "com.bugjaeger.m",              // Bugjaeger Mobile
        "me.aShellYou.ashell",          // aShellYou
        "me.ashell.ashell",             // aShell
        "com.adbshell",                 // ADBShell
        "com.adb.appcontrol",           // ADBAppControl
        // Xposed / hook frameworks
        "de.robv.android.xposed.installer", // Original Xposed / EdXposed installer
        "me.weishu.riru",               // Riru
        "com.taichi.framework",         // TaiChi
        "com.virtualxposed",            // VirtualXposed
        "io.virtualapp",                // VirtualApp
        "com.lspatch",                  // LSPatch
        "re.frida.server",              // Frida server
        "com.sandhook",                 // SandHook
        "com.yahfa",                    // YAHFA
        "com.dexposed"                  // Dexposed
    )

    suspend fun toggleGamingMode(active: Boolean, packageName: String? = null) {
        toggleMutex.withLock {
            withContext(Dispatchers.IO) {
                if (active) {
                    enableGamingMode(packageName)
                } else {
                    disableGamingMode()
                }
            }
        }
    }

    /**
     * Wakes the apps a previous session recorded as suspended and restores the background
     * app-ops it changed — without touching the settings snapshot.
     *
     * Why this exists separately from the power button: after a reboot the settings levers are
     * already back at stock, but the package manager keeps suspensions. Making the user run a
     * full revert just to wake apps would re-write an old snapshot over settings the system has
     * since changed. This action does only the app half, which is the part the device cannot fix
     * by itself.
     *
     * It restores the record, never a blind sweep: `pm unsuspend` on every suspend target would
     * wake apps this app never froze. Idempotent — a package already awake is skipped by the
     * package manager's own state, and an empty record reports a clean result without writing.
     *
     * @return verified counts, never a bare success flag. When the package manager will not
     *   answer, the untouched record is reported as unverified instead of as a confirmed failure.
     */
    suspend fun restoreSuspendedApps(): GamingModeRestoreResult = toggleMutex.withLock {
        withContext(Dispatchers.IO) {
            val state = _state.value
            if (state is GamingModeState.Active ||
                state is GamingModeState.Enabling ||
                state is GamingModeState.Disabling ||
                state is GamingModeState.Checking
            ) {
                // The same record the active session is still writing; restoring it now would
                // race its suspend loop and could wake games the user just asked to freeze.
                return@withContext GamingModeRestoreResult(refusedWhileActive = true)
            }

            val recorded = prefs.getStringSet("affected_pkgs", emptySet()).orEmpty()
            val appOpsBefore = recordedAppOpsPackages()

            val failedToWake = mutableSetOf<String>()
            for (pkg in recorded.sorted()) {
                if (!unsuspendOne(pkg)) failedToWake += pkg
            }

            // The command verdict is one source; the package manager's own per-user state is the
            // other. Only recorded packages are considered, so an app frozen by another tool is
            // never claimed or woken here.
            val evidence = querySuspendedPackages()
            val remaining = when {
                evidence != null -> recorded.filterTo(mutableSetOf()) { it in evidence }
                else -> failedToWake
            }
            if (remaining.isEmpty()) {
                prefs.edit { remove("affected_pkgs") }
            } else {
                prefs.edit { putStringSet("affected_pkgs", remaining) }
            }

            restoreAppOpsRecord()
            val appOpsLeft = recordedAppOpsPackages()

            val settingsStillApplied = prefs.getBoolean("is_active", false) && !sessionRebooted()
            val clean = remaining.isEmpty() && appOpsLeft.isEmpty() && !settingsStillApplied
            if (clean) {
                prefs.edit {
                    putBoolean("is_active", false)
                    putBoolean("fixed_perf_manual", false)
                    remove("affected_pkgs")
                    remove("appops_prior")
                    remove("session_boot_count")
                }
                _isFixedPerformanceMode.value = false
                _report.value = GamingModeReport()
                _state.value = GamingModeState.Idle
            } else {
                // Something (usually the settings snapshot) still needs the power button; keep
                // the interrupted state so the card does not claim a clean stop.
                _state.value = GamingModeState.Interrupted(remaining.size)
            }
            _sessionOpen.value = !clean

            _suspendedPackages.value = remaining
            _recovery.value = GamingModeRecovery(
                pendingPackages = remaining.sorted(),
                pendingAppOps = appOpsLeft,
                verified = evidence != null
            )

            GamingModeRestoreResult(
                restoredPackages = (recorded - remaining).size,
                stillSuspended = remaining.size,
                restoredAppOps = (appOpsBefore - appOpsLeft.toSet()).size,
                failedAppOps = appOpsLeft.size,
                verified = evidence != null
            )
        }
    }

    /**
     * Opens the persisted session before the first lever is written.
     *
     * The flag used to be written only after every step succeeded, so a crash halfway through
     * activation left suspended apps with no session record at all — invisible to recovery and
     * to every later deactivation. Clearing the per-session records here also guarantees a fresh
     * activation cannot inherit a previous session's leftovers; the guard in [enableGamingMode]
     * refuses to reach this point while a record is still outstanding.
     */
    private fun beginSession() {
        prefs.edit {
            putBoolean("is_active", true)
            putInt("session_boot_count", currentBootCount())
            putLong("session_started_at", System.currentTimeMillis())
            remove("affected_pkgs")
            remove("appops_prior")
        }
        _sessionOpen.value = true
    }

    /**
     * This boot's `Settings.Global.BOOT_COUNT`, or 0 when the platform will not report it.
     *
     * 0 means "unknown" everywhere it is used, and both reconciliation and recovery treat two
     * unknown counts as the same boot: reverting a snapshot that the reboot already reset is
     * harmless, while skipping the revert on a live session leaves settings applied with no way
     * back.
     */
    private fun currentBootCount(): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrDefault(0)

    /** Whether the persisted session began before the current boot. */
    private fun sessionRebooted(): Boolean {
        val atStart = prefs.getInt("session_boot_count", 0)
        val now = currentBootCount()
        return atStart > 0 && now > 0 && atStart != now
    }

    /** Packages whose background app-op a session changed and has not verified back yet. */
    private fun recordedAppOpsPackages(): List<String> =
        AppOpsRecord.decode(prefs.getStringSet("appops_prior", emptySet()) ?: emptySet()).keys.sorted()

    private suspend fun enableGamingMode(packageName: String?) {
        _state.value = GamingModeState.Enabling(0f, context.getString(R.string.gt_eng_init))
        shellRunner.refreshShizukuPermission()
        val isRoot = shellRunner.isRootAvailable()
        val hasShizuku = shellRunner.shizukuHasPermission.value
        if (!isRoot && !hasShizuku) {
            _state.value = GamingModeState.Error(context.getString(R.string.gt_eng_no_priv))
            return
        }
        // A previous session has not been closed: starting another one would capture its
        // half-applied values as the user's "original" and could suspend apps twice. Recovery
        // (or a full deactivation) has to come first.
        if (_recovery.value.isNeeded ||
            _state.value is GamingModeState.Interrupted ||
            _sessionOpen.value
        ) {
            _state.value = GamingModeState.Error(context.getString(R.string.gt_gm_recovery_first))
            return
        }
        try {
            _state.value = GamingModeState.Enabling(0.05f, context.getString(R.string.gt_eng_snapshot))
            // Read the user's real values first — before the cache trim, the suspends and every
            // `settings put` below — so deactivation restores their configuration and not ours.
            val restrictedBefore = runCatching { backgroundDataRestrictor.isEngaged() }
                .getOrDefault(false)
            if (!captureAndSaveSnapshot(packageName)) {
                _state.value = GamingModeState.Error(context.getString(R.string.gt_eng_snapshot_fail))
                return
            }
            // Read-only evidence pass for the OEM refresh and per-SoC keys the counter-example
            // references exposed but this app does not lever yet. It writes nothing; it puts the
            // device's own answer in the booster log so a future gated lever starts from
            // evidence instead of a copied script.
            logDeviceVendorEvidence(packageName)
            val unavailable = mutableListOf<GamingModeNotice>()

            // From here on a crash must be detectable: record the session and the boot it belongs
            // to before the first lever is written. Writing `is_active` only at the end is what
            // let a mid-activation death leave suspended apps with no session record at all.
            beginSession()

            _state.value = GamingModeState.Enabling(0.15f, context.getString(R.string.gt_eng_trim))
            execute(PrivilegeCommands.TRIM_CACHES)
            execute(PrivilegeCommands.COMPACT_BACKGROUND)
            runCatching { execute(PrivilegeCommands.PINNER_REPIN) }

            _state.value = GamingModeState.Enabling(0.35f, context.getString(R.string.gt_eng_suspend))
            // The automatic sweep plus the user's own extra list, minus the user's keep list.
            // `SuspendSweep.resolveTargets` owns that combination and its precedence — an app
            // the user keeps running is never frozen, even if it also sits on the extra list
            // (the UI keeps the two stores mutually exclusive; the engine resolves a stale
            // overlap as kept). Both halves share the one `affected_pkgs` record below, so
            // deactivation wakes everything through the same verified path with no second
            // bookkeeping — and kept apps are never in it because they were never frozen.
            val targets = SuspendSweep.resolveTargets(
                sweepTargets = getSuspendTargets(packageName),
                extraPicks = suspendListStore.getSuspendPackages(),
                exempt = suspendExemptStore.getExemptPackages(),
                activeGamePkg = packageName,
                selfPkg = context.packageName
            )
            val currentlyAffected = prefs.getStringSet("affected_pkgs", emptySet())?.toMutableSet() ?: mutableSetOf()
            // The package manager's own per-package suspension state, read once before the loop:
            // apps someone else already suspended are skipped, so this session never records them
            // as its own and deactivation never unsuspends an app it never froze. An unreadable
            // answer behaves as before (nothing known suspended) rather than skipping work on a
            // guess; a package we then suspend gets recorded and woken by us either way.
            val preSuspended = querySuspendedPackages().orEmpty()
            val freshTargets = targets.filter { it !in preSuspended }
            var suspendedNow = 0
            var suspendFailures = 0
            for (pkg in freshTargets) {
                if (pkg in currentlyAffected) continue
                // Record only what actually got suspended. disableGamingMode unsuspends exactly
                // this set, so a package listed here that was never frozen makes the revert lie —
                // and a package frozen but not listed would be left frozen for good.
                val result = shellRunner.execSafeResult(*PrivilegeCommands.suspendArgs(pkg))
                if (SuspendVerdict.isSuspendConfirmed(result.exitCode, result.stdout)) {
                    currentlyAffected.add(pkg)
                    // Persist the moment it is confirmed, not after the whole loop: a crash halfway
                    // through used to leave frozen packages with no record, which made them
                    // unrecoverable and invisible to every later deactivation.
                    prefs.edit { putStringSet("affected_pkgs", currentlyAffected) }
                    suspendedNow++
                } else {
                    suspendFailures++
                }
            }
            _suspendedPackages.value = currentlyAffected
            if (suspendFailures > 0) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_suspend, listOf(suspendFailures))
            }

            // M42: the background-op layer beneath the suspend sweep. Every package this run
            // attempts to freeze also gets `RUN_IN_BACKGROUND` set to `ignore`, its prior mode
            // read first — so on ROMs that refuse `pm suspend` the restriction still lands, and
            // in every case deactivation restores exactly what was there (a package with no
            // override reads as `default`, stored and written back as that word). Unreadable
            // priors are skipped rather than guessed at, and — like the suspend record above —
            // each entry is persisted the moment it is confirmed, so a crash mid-activation
            // cannot leave an unrecorded write behind.
            val appOpsRecord = AppOpsRecord.decode(
                prefs.getStringSet("appops_prior", emptySet()) ?: emptySet()
            ).toMutableMap()
            var appOpsFailures = 0
            for (pkg in freshTargets) {
                // An entry means an earlier session already recorded this package; its original
                // prior is preserved and never re-read over ours.
                if (appOpsRecord.containsKey(pkg)) continue
                val priorAnswer = runCatching {
                    shellRunner.execSafeResult(*PrivilegeCommands.appOpsGetArgs(pkg))
                }.getOrNull()
                val priorMode = priorAnswer?.takeIf { it.isSuccess }?.stdout
                    ?.let { AppOpsVerdict.parseMode(it) } ?: continue
                val setAnswer = runCatching {
                    shellRunner.execSafeResult(*PrivilegeCommands.appOpsSetArgs(pkg, "ignore"))
                }.getOrNull()
                val readBack = if (setAnswer != null && setAnswer.isSuccess) {
                    runCatching { shellRunner.execSafeResult(*PrivilegeCommands.appOpsGetArgs(pkg)) }.getOrNull()
                } else null
                val confirmed = readBack != null && readBack.isSuccess &&
                    readBack.stdout.let { AppOpsVerdict.parseMode(it) } == "ignore"
                if (confirmed) {
                    appOpsRecord[pkg] = priorMode
                    prefs.edit { putStringSet("appops_prior", AppOpsRecord.encode(appOpsRecord)) }
                } else {
                    appOpsFailures++
                }
            }
            if (appOpsFailures > 0) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_appops, listOf(appOpsFailures))
            }

            _state.value = GamingModeState.Enabling(0.6f, context.getString(R.string.gt_eng_focus))
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            var dndEngaged = false
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                // The filter change travels through ZenModeHelper and a config write, so reading it
                // back on the next line often still returns the old value — that race is why DND
                // "sometimes failed" while actually being applied. Poll briefly instead.
                dndEngaged = awaitInterruptionFilter(nm)
                if (!dndEngaged) unavailable += GamingModeNotice.Res(R.string.gt_eng_un_dnd_off)
            } else {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_dnd_perm)
            }

            // Second layer beyond DND, from the reference's GamingNotificationListener: some
            // OEM skins post their own alerts around the interruption filter, and cancelling
            // the notification directly does not depend on the filter being honoured. Arming is
            // all activation does — the listener (bound by the system only once the user has
            // granted notification access) does the cancelling, including a purge of what is
            // already on screen.
            //
            // OriginOS/Vivo coma fix, also from the reference: a listener the system let fall
            // asleep does not wake on being armed, so arming fresh starts with a disable/enable
            // pulse on our own component to re-bind it. An app can always toggle its own
            // components — no permission involved — and a ROM that refuses the toggle still
            // arms below; the pulse is best-effort, never a gate.
            if (!_notificationSuppressionActive.value && isNotificationListenerEnabled()) {
                pulseNotificationListenerBinding()
            }
            val notificationSuppression = isNotificationListenerEnabled()
            _notificationSuppressionActive.value = notificationSuppression

            _state.value = GamingModeState.Enabling(0.9f, context.getString(R.string.gt_eng_hw))
            // Refresh lock via the shared RefreshRateController (F3): one writer for
            // min/peak_refresh_rate — Gaming Mode and "Always fastest" can no longer diverge.
            // Target is always the panel's measured maximum: maximum refresh, never a cap.
            val lock = refreshController.lockToPeak()
            val maxHz = lock.lockedHz
            val peakOk = lock.peakOk
            val minOk = lock.minOk
            val lockedHz = if (peakOk || minOk) maxHz else null
            if (lockedHz == null) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_refresh)
            }
            // vivo's own display-mode switch, written beside the standard rate keys: OriginOS
            // honours it over them on some builds. The mode is the enumerated one at the same
            // measured peak — never the reference's hardcoded id (see [VivoDisplayMode]).
            val vivoLock = refreshController.lockVendorDisplayMode()
            if (vivoLock.applicable && !vivoLock.ok) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_vivo)
            }
            // Touch sampling boost. Only OEMs that ship the key honour it; captureAndSaveSnapshot
            // already recorded the old value, so disableGamingMode puts it back.
            val touchOk = putSettingVerified("touch_response_speed", "2")
            if (!touchOk) unavailable += GamingModeNotice.Res(R.string.gt_eng_un_touch)
            // The user's touch-speed choice, if any. Same snapshot/restore contract as the boost
            // above; a refused write is reported rather than counted as done.
            var pointerSpeedApplied: Int? = null
            PointerSpeed.sanitize(_pointerSpeedChoice.value)?.let { speed ->
                if (putSettingVerified("pointer_speed", speed.toString())) {
                    pointerSpeedApplied = speed
                } else {
                    unavailable += GamingModeNotice.Res(R.string.gt_eng_un_pointer, listOf(speed.toString()))
                }
            }

            val fixedPerfOk = shellRunner
                .execSafeResult(*PrivilegeCommands.fixedPerformanceArgs(true))
                .isSuccess
            if (!fixedPerfOk) unavailable += GamingModeNotice.Res(R.string.gt_eng_un_fixed)

            // Qualcomm's vendor GPU mode. The payload is the reference daemon's whole job (see
            // [GamingModeReport.gpuPerformanceMode] for the lineage); gated on the property
            // existing at all, so non-Qualcomm silicon is "not applicable" rather than a refused
            // switch it never had.
            val gpuPerfOk = applyVendorGpuPerformance()
            if (gpuPerfOk == false) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_gpu)
            }

            // Qualcomm's game FPS hint, from the same reference's boot script (see
            // [GamingModeReport.qtiGameFps] for the lineage and the persist-twin decision).
            val qtiFpsOk = applyQtiGameFpsHint(maxHz)
            if (qtiFpsOk == false) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_qti)
            }

            // MediaTek's game-mode knobs (GED boost parameters + PPM policy states). Gated on
            // the nodes existing at all — non-MediaTek silicon is "not applicable" — root-only
            // (a shell-uid session cannot write /sys), and every write is read back; see
            // [GamingModeReport.mediaTekGameMode] for the lineage.
            val mtkNodes = readLastSnapshot()?.mediatekGameNodes.orEmpty()
            val mtkOk = applyMediaTekGameMode(mtkNodes)
            if (mtkOk == false) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_mtk)
            }

            // Snapdragon widen-only channel: policy ceilings and game cpusets ([SnapdragonCpuWiden])
            // plus the schedutil rate limit / hispeed jump / iowait boost, msm_performance touch
            // boost and CPU ceilings, core_ctl floors and ceilings and cpu_boost input boost
            // ([SnapdragonKernelNodes]). Gated on recorded priors — an empty map is "not
            // applicable" — root-only (a shell-uid session cannot write /sys), and every write is
            // read back; see [GamingModeReport.snapdragonCpuMode] for the lineage.
            val snapNodes = readLastSnapshot()?.snapdragonCpuNodes.orEmpty()
            val snapOk = applySnapdragonCpuMode(snapNodes)
            if (snapOk == false) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_snap)
            }

            // The five SmartPack-derived kernel levers. All snapshot-gated (an empty prior map is
            // "not applicable", never a write), root-only and read-back verified. Spectrum runs
            // before the governor so the immediate CPU state ends at `performance` even when a
            // kernel's Spectrum gaming profile is its own schedutil tuning.
            val spectrum = applySpectrumProfile(readLastSnapshot()?.spectrumProfileProperty)
            if (spectrum.refused) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_spectrum)
            }
            val governorOk = applyCpuGovernorMode(readLastSnapshot()?.cpuGovernorNodes.orEmpty())
            if (governorOk == false) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_governor)
            }
            val inputBoostOk = applyCpuInputBoostNodes(readLastSnapshot()?.cpuInputBoostNodes.orEmpty())
            if (inputBoostOk == false) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_input_boost)
            }
            val storage = applyStorageTuning(readLastSnapshot()?.storageTuningNodes.orEmpty())
            if (storage.applied == false) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_storage)
            }
            val memory = applyMemoryTuning(readLastSnapshot()?.memoryTuningNodes.orEmpty())
            if (memory.applied == false) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_memory)
            }

            // The two developer options, applied through the same verified helpers the Developer
            // Options card uses — so the switches there and the state here can never disagree.
            _state.value = GamingModeState.Enabling(0.92f, context.getString(R.string.gt_eng_dev))
            val discardOk = toggleAlwaysFinishActivities(true)
            if (!discardOk) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_discard)
            }
            val processLimitOk = toggleBackgroundProcessLimit(true)
            if (!processLimitOk) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_limit)
            }

            // Metered-background data. Left entirely alone when the user's own switch was already on:
            // engaging it again would make deactivation turn off a restriction we did not own.
            _state.value = GamingModeState.Enabling(0.95f, context.getString(R.string.gt_eng_data))
            var backgroundDataRestricted: Boolean? = null
            if (restrictedBefore) {
                unavailable += GamingModeNotice.Res(R.string.gt_eng_un_data_on)
            } else {
                val outcome = runCatching {
                    backgroundDataRestrictor.enable(listOfNotNull(packageName))
                }.getOrNull()
                backgroundDataRestricted = outcome?.success == true
                if (outcome == null) {
                    unavailable += GamingModeNotice.Res(R.string.gt_eng_un_data_silent)
                } else if (!outcome.success) {
                    unavailable += GamingModeNotice.Res(R.string.gt_eng_un_data_fail, listOf(outcome.message))
                }
            }

            var networkWhitelisted: Boolean? = null
            var gameInterventionApplied: Boolean? = null
            var gameInterventionDownscale: Float? = null
            if (packageName != null) {
                execute("pm unsuspend --user 0 $packageName")
                execute("cmd activity set-bg-restriction-level --user 0 $packageName unrestricted")
                execute("am set-standby-bucket --user 0 $packageName active")
                execute(PrivilegeCommands.deviceIdleWhitelistAdd(packageName))
                networkWhitelisted = applyPerGameOptimizations(packageName)

                // The game's own frame-cap table, raised to the panel's peak — the maximum,
                // never a throttle target. Skipped whole on Android 11 and older, where game
                // interventions do not exist — that is "not applicable", reported as null, not
                // a refusal. The render scale is the user's opt-in choice (null = FPS-only
                // reference entry); it is read here, at activation, so a change mid-session
                // waits for the next run instead of rewriting the table under a running game
                // that would need a restart to honor it anyway.
                if (deviceCapabilities.supportsGameInterventions) {
                    val downscale = _interventionDownscale.value
                    val outcome = gameInterventions.apply(packageName, maxHz, downscale)
                    gameInterventionApplied = outcome.applied
                    gameInterventionDownscale = if (outcome.applied) downscale else null
                    if (!outcome.applied) {
                        unavailable += GamingModeNotice.Res(
                            R.string.gt_eng_un_framecap,
                            listOf(
                                outcome.refusal?.let {
                                    GamingModeNotice.Res(R.string.gt_eng_un_framecap_no, listOf(it))
                                }
                                    ?: GamingModeNotice.Res(R.string.gt_eng_un_framecap_kept)
                            )
                        )
                    }
                }
            }
            execute(PrivilegeCommands.DEVICE_IDLE_FORCE)
            execute(PrivilegeCommands.KILL_ALL)

            prefs.edit {
                // Fixed performance mode and `cmd game set` can leave the thermal service in an
                // overridden state after the mode is off (some vendor HALs keep the override
                // until it is explicitly cleared). Only those two paths set this flag, so the
                // deactivation-side reset runs exactly when there is something to undo.
                if (fixedPerfOk || networkWhitelisted == true) {
                    putBoolean("thermal_recovery_needed", true)
                }
            }
            // The session is live and every lever that could be verified was; the recovery prompt
            // belongs to interrupted sessions only.
            _recovery.value = GamingModeRecovery()
            _isFixedPerformanceMode.value = fixedPerfOk
            _report.value = GamingModeReport(
                fixedPerformance = fixedPerfOk,
                gpuPerformanceMode = gpuPerfOk,
                qtiGameFps = qtiFpsOk,
                mediaTekGameMode = mtkOk,
                snapdragonCpuMode = snapOk,
                cpuInputBoost = inputBoostOk,
                cpuGovernor = governorOk,
                spectrumProfile = spectrum.profile,
                storageTuning = storage.applied,
                storageReadAheadKb = storage.valueKb,
                memoryTuning = memory.applied,
                memoryMinFreeKb = memory.valueKb,
                lockedRefreshHz = lockedHz,
                touchResponseBoost = touchOk,
                pointerSpeed = pointerSpeedApplied,
                suspendedPackages = suspendedNow,
                suspendFailures = suspendFailures,
                dndEngaged = dndEngaged,
                networkWhitelisted = networkWhitelisted,
                discardActivities = discardOk,
                processLimit = processLimitOk,
                backgroundDataRestricted = backgroundDataRestricted,
                gameInterventionApplied = gameInterventionApplied,
                // The scale the read-back confirmed, or null when the intervention landed
                // without one (or did not land at all) — the row shows "raised" plus this.
                gameInterventionDownscale = gameInterventionDownscale,
                // false means "not granted", which the report renders as absent rather than
                // refused — the user has simply not switched this layer on.
                notificationSuppression = if (notificationSuppression) true else null,
                unavailable = unavailable
            )
            _state.value = GamingModeState.Active
        } catch (e: CancellationException) {
            // A cancelled activation is not a refused one: nothing said no, the job simply
            // stopped. It must never surface as "Activation failed" — the old broad catch below
            // turned a screen-off cancel into "Job was cancelled" while apps were already frozen.
            // What is on record stays on record and the card shows the interrupted session, so
            // the recovery control is offered from evidence instead of being hidden behind an
            // error message. The cancellation travels on: swallowing it would report a run this
            // coroutine did not finish.
            if (_sessionOpen.value) {
                val remaining = pendingSuspendedPackages()
                publishRecoveryEvidence(remaining)
                _state.value = GamingModeState.Interrupted(remaining.size)
            } else {
                // Nothing was written yet (cancelled during the snapshot read): there is no
                // session to recover, and claiming one would offer a restore with nothing to wake.
                _state.value = GamingModeState.Idle
            }
            throw e
        } catch (e: Exception) {
            // Whatever was written before the failure stays on record. Reporting only the error
            // would hide the packages that may be frozen right now, so the recovery evidence is
            // rebuilt from the persisted record and the card offers Restore Apps immediately.
            val remaining = pendingSuspendedPackages()
            publishRecoveryEvidence(remaining)
            _state.value = GamingModeState.Error(e.message ?: context.getString(R.string.gt_eng_activation_fail))
        }
    }

    /** Packages the persisted session recorded as suspended, in the order the card lists them. */
    private fun pendingSuspendedPackages(): List<String> =
        prefs.getStringSet("affected_pkgs", emptySet())?.sorted().orEmpty()

    /**
     * Publishes the app half of a session that stopped before reaching Active: the packages and
     * app-ops the device actually accepted stay on record (they may be frozen right now), and the
     * card's Restore Apps control reads them from here.
     */
    private fun publishRecoveryEvidence(remaining: List<String>) {
        _suspendedPackages.value = remaining.toSet()
        _recovery.value = GamingModeRecovery(
            pendingPackages = remaining,
            pendingAppOps = recordedAppOpsPackages(),
            verified = false
        )
    }

    /**
     * Waits, briefly, for the notification interruption filter to actually reach INTERRUPTION_FILTER_NONE.
     *
     * `setInterruptionFilter` returns as soon as NotificationManagerService has the request; the zen
     * state it changes is published a moment later. Reading `currentInterruptionFilter` on the very
     * next line therefore reports the *previous* filter often enough to look intermittent, which is
     * exactly how "DND sometimes fails" presented. Six 50 ms checks cover that gap without turning a
     * genuinely refused filter into a long stall.
     *
     * @return whether the filter is INTERRUPTION_FILTER_NONE now. A false here is a real failure, not a race.
     */
    private suspend fun awaitInterruptionFilter(
        nm: NotificationManager,
        attempts: Int = 6
    ): Boolean {
        repeat(attempts) {
            if (runCatching { nm.currentInterruptionFilter }.getOrNull() == NotificationManager.INTERRUPTION_FILTER_NONE) return true
            delay(50.milliseconds)
        }
        return runCatching { nm.currentInterruptionFilter }.getOrNull() == NotificationManager.INTERRUPTION_FILTER_NONE
    }

    private suspend fun disableGamingMode() {
        _state.value = GamingModeState.Disabling
        // First, before any lever is reverted: the moment suppression stops, notifications
        // arrive normally again, and a device busy reporting its own restoration would have
        // its reports eaten by the layer this mode switched on.
        _notificationSuppressionActive.value = false
        // Anything gathered here survives into the report even when a later step throws, so a
        // half-finished revert still says what it did not get to.
        val revertProblems = mutableListOf<GamingModeNotice>()
        var remainingSuspended: Set<String> = emptySet()
        var verificationAvailable = false
        try {
            // Every unsuspend is verified by what the shell answered — the same standard
            // activation holds suspends to. A package that refused to wake stays in the record
            // instead of being deleted from it, so the next deactivation retries exactly the
            // leftovers rather than forgetting frozen apps. Dropping the whole set up front is
            // what made one refused unsuspend a permanently suspended app with no trace.
            //
            // The sweep covers the whole suspend-target universe, not just the record: sessions
            // reverted by older builds deleted their record before verifying, so their leftovers
            // have no entry in `affected_pkgs`. Waking an already-awake package is a confirmed
            // no-op, which is what makes this blind sweep safe — it can only wake our own past
            // leaks. `pm` takes a single package per call, so each chunk is N commands chained
            // with `;` in one fork (the same batching BackgroundDataRestrictor uses for
            // netpolicy); a refused chunk falls back to per-package isolation rather than
            // retaining the whole chunk as still suspended.
            val affected = prefs.getStringSet("affected_pkgs", emptySet()) ?: emptySet()
            val stillSuspended = mutableSetOf<String>()
            for (chunk in (affected + getSuspendTargets(null)).distinct().chunked(UNSUSPEND_BATCH_SIZE)) {
                if (chunk.size == 1) {
                    if (!unsuspendOne(chunk[0])) stillSuspended.add(chunk[0])
                    continue
                }
                val chained = chunk.joinToString("; ") { "pm unsuspend --user 0 $it" }
                val batchResult = runCatching { shellRunner.execResult(chained) }.getOrNull()
                if (batchResult != null &&
                    batchResult.isSuccess &&
                    SuspendVerdict.isUnsuspendConfirmed(batchResult.exitCode, batchResult.stdout)
                ) continue
                for (pkg in chunk) {
                    if (!unsuspendOne(pkg)) stillSuspended.add(pkg)
                }
            }
            // Command verdicts are one source; the package manager's own per-user state is the
            // other. When it answers, it decides what remains suspended — a silent exit 0 that did
            // not actually wake the app is exactly the case a bare exit code would miss. Only the
            // session's record is carried forward: the blind sweep above can mention apps that
            // were never ours, and those must not enter the recovery prompt.
            val evidence = querySuspendedPackages()
            verificationAvailable = evidence != null
            remainingSuspended = if (evidence != null) {
                affected.filterTo(mutableSetOf()) { it in evidence }
            } else {
                stillSuspended.filterTo(mutableSetOf()) { it in affected }
            }
            if (remainingSuspended.isEmpty()) {
                prefs.edit { remove("affected_pkgs") }
            } else {
                prefs.edit { putStringSet("affected_pkgs", remainingSuspended) }
                revertProblems +=
                    GamingModeNotice.Res(R.string.gt_eng_still_suspended, listOf(remainingSuspended.size))
            }
            // M42: the background ops set on activation go back too, each restore verified by
            // reading the mode back; a package that refuses stays recorded for the next
            // deactivation instead of being silently left restricted.
            revertProblems += restoreAppOpsRecord()
            execute(PrivilegeCommands.DEVICE_IDLE_UNFORCE)
            execute(PrivilegeCommands.FIXED_PERF_OFF)
            // Do Not Disturb is restored inside revertFromSnapshot, from the filter that was recorded
            // before activation — not to INTERRUPTION_FILTER_ALL, which would cancel a DND the user set.
            revertProblems += revertFromSnapshot()
            val thermalRefusal = if (!recoverThermalOverrideIfNeeded()) {
                GamingModeNotice.Res(R.string.gt_eng_thermal)
            } else {
                null
            }
            // Deactivation normally clears the report, but a refused step is the device's own
            // refusal text and belongs in front of the user rather than in a silent state change.
            _report.value = GamingModeReport(unavailable = revertProblems + listOfNotNull(thermalRefusal))
        } catch (e: Exception) {
            // An interrupted revert keeps whatever record activation wrote; the recovery prompt is
            // built from that record rather than from this process's partial local set.
            remainingSuspended = prefs.getStringSet("affected_pkgs", emptySet()).orEmpty()
            revertProblems += GamingModeNotice.Res(
                R.string.gt_eng_revert_fail,
                listOf(e.message ?: e.javaClass.simpleName)
            )
            _report.value = GamingModeReport(unavailable = revertProblems)
        } finally {
            val appOpsLeft = recordedAppOpsPackages()
            // The session is closed only when nothing it wrote is still outstanding. A refused
            // wake-up, an unrestored app-op, or a settings revert that reported a problem keeps
            // `is_active` on record, so the card shows Interrupted and offers recovery instead of
            // claiming a clean stop.
            val clean = remainingSuspended.isEmpty() && appOpsLeft.isEmpty() && revertProblems.isEmpty()
            prefs.edit {
                putBoolean("fixed_perf_manual", false)
                if (clean) {
                    putBoolean("is_active", false)
                    remove("affected_pkgs")
                    remove("session_boot_count")
                } else {
                    putBoolean("is_active", true)
                }
            }
            _isFixedPerformanceMode.value = false
            _suspendedPackages.value = remainingSuspended
            _sessionOpen.value = !clean
            _recovery.value = GamingModeRecovery(
                pendingPackages = remainingSuspended.sorted(),
                pendingAppOps = appOpsLeft,
                verified = verificationAvailable
            )
            _state.value = if (clean) GamingModeState.Idle else GamingModeState.Interrupted(remainingSuspended.size)
        }
    }

    /**
     * Restores the `RUN_IN_BACKGROUND` op for every package whose prior mode this session
     * recorded, reading each restore back like every other lever this engine pulls.
     *
     * A package whose restore does not come back exactly as recorded is kept in the record
     * rather than dropped — the same "never delete an entry the device did not accept" rule
     * the unsuspend sweep follows — so the next deactivation retries it, and the count is
     * reported instead of silently leaving apps restricted. An absent record means nothing
     * was ever written, not an empty restore.
     */
    private suspend fun restoreAppOpsRecord(): List<GamingModeNotice> {
        val record = AppOpsRecord.decode(
            prefs.getStringSet("appops_prior", emptySet()) ?: emptySet()
        )
        if (record.isEmpty()) return emptyList()
        val stillRestricted = mutableMapOf<String, String>()
        for ((pkg, priorMode) in record) {
            val set = runCatching {
                shellRunner.execSafeResult(*PrivilegeCommands.appOpsSetArgs(pkg, priorMode))
            }.getOrNull()
            val readBack = if (set != null && set.isSuccess) {
                runCatching { shellRunner.execSafeResult(*PrivilegeCommands.appOpsGetArgs(pkg)) }.getOrNull()
            } else null
            val restored = readBack != null && readBack.isSuccess &&
                readBack.stdout.let { AppOpsVerdict.parseMode(it) } == priorMode
            if (!restored) stillRestricted[pkg] = priorMode
        }
        if (stillRestricted.isEmpty()) {
            prefs.edit { remove("appops_prior") }
        } else {
            prefs.edit { putStringSet("appops_prior", AppOpsRecord.encode(stillRestricted)) }
        }
        return if (stillRestricted.isEmpty()) emptyList()
        else listOf(GamingModeNotice.Res(R.string.gt_eng_still_restricted, listOf(stillRestricted.size)))
    }

    /**
     * Clears any thermal override the session may have left behind.
     *
     * Fixed performance mode and the GameManager game mode (`cmd game set`) can leave the
     * thermal service holding an override after every other lever is reverted — on some vendor
     * HALs the override stays engaged until it is explicitly cleared, which presents as the
     * phone running hot long after Gaming Mode is off. Activation records that one of those
     * paths was accepted (`thermal_recovery_needed`); this runs once, on deactivation, and
     * clears the flag only when `cmd thermalservice reset` actually answered with success.
     *
     * Modelled on the reference project's `EsportsOptimizationEngine.recoverThermalOverrideIfNeeded`
     * (`reference/gamingtools/booster`): guarded by a needs-recovery flag rather than run
     * unconditionally, judged by the exit code, and idempotent — a reset that never became
     * needed is a success, not a skipped failure.
     *
     * @return false when a reset was needed and the device refused it.
     */
    private suspend fun recoverThermalOverrideIfNeeded(): Boolean {
        if (!prefs.getBoolean("thermal_recovery_needed", false)) return true
        val result = runCatching {
            shellRunner.execSafeResult("cmd", "thermalservice", "reset")
        }.getOrNull() ?: return false
        if (!result.isSuccess) return false
        prefs.edit { remove("thermal_recovery_needed") }
        return true
    }

    /**
     * Result of a manual RAM boost.
     *
     * @param freedMb megabytes of available memory gained, measured before and after. null when the
     *   device would not let us read the memory figures at all — the UI must say "unknown" rather
     *   than print a zero that looks like a measurement.
     * @param stoppedCount packages `am force-stop` actually accepted.
     * @param attemptedCount packages it was asked to stop.
     */
    data class RamBoostResult(
        val freedMb: Long?,
        val stoppedCount: Int,
        val attemptedCount: Int
    )

    /**
     * Frees background memory and measures the result.
     *
     * The "before" reading is taken *first*, before any cache trimming, so the number covers the
     * whole operation. `MemAvailable` from /proc/meminfo is the kernel's own estimate of what can be
     * handed out without swapping — the same source the reference RamMonitor parses — with
     * [android.app.ActivityManager.MemoryInfo] as the fallback when the file cannot be read.
     */
    suspend fun manualBoostRam(): RamBoostResult {
        val availBefore = readAvailableMemoryBytes()
        var stoppedCount = 0
        var attempted = 0
        try {
            execute(PrivilegeCommands.TRIM_CACHES)
            execute(PrivilegeCommands.COMPACT_BACKGROUND)
            val targets = getSuspendTargets(null)
            attempted = targets.size
            for (pkg in targets) {
                // execute() never throws for a command that merely failed, so counting attempts
                // would report apps that were never stopped. Only a zero exit counts.
                if (shellRunner.execSafeResult(*PrivilegeCommands.forceStopArgs(pkg)).isSuccess) stoppedCount++
            }
            execute(PrivilegeCommands.KILL_ALL)
        } catch (_: Exception) {}
        // The kills are asynchronous; give the kernel a moment to reclaim before measuring again.
        delay(500.milliseconds)
        val availAfter = readAvailableMemoryBytes()
        val freedMb = if (availBefore != null && availAfter != null) {
            ((availAfter - availBefore) / (1024L * 1024L)).coerceAtLeast(0L)
        } else {
            null
        }
        return RamBoostResult(freedMb = freedMb, stoppedCount = stoppedCount, attemptedCount = attempted)
    }

    /** @return available bytes, or null when neither /proc/meminfo nor ActivityManager could answer. */
    private fun readAvailableMemoryBytes(): Long? =
        com.catsmoker.app.platform.memory.MemAvailableReader.readAvailableBytesWithFallback(context)

    private fun parseMemAvailableBytes(): Long? =
        com.catsmoker.app.platform.memory.MemAvailableReader.readAvailableBytes()

    /**
     * Turns Android's fixed-performance mode on or off, and says whether the device accepted it.
     *
     * `cmd power set-fixed-performance-mode-enabled` reaches PowerManagerService, which calls
     * `PowerHAL.setMode(MODE_FIXED_PERFORMANCE)`. That mode asks the SoC vendor's power HAL to hold
     * clocks at a fixed operating point instead of letting the governor scale them with load — the
     * point of it is *consistency*, not peak speed, and on many HALs the fixed point is deliberately
     * below the boost ceiling to keep the device thermally sustainable.
     *
     * Two real limits, both reported rather than hidden:
     * - The command was added in Android 11. On older builds `cmd power` does not know it.
     * - `MODE_FIXED_PERFORMANCE` is optional for vendors. A HAL that does not implement it accepts the
     *   call and does nothing, so a success here means "the framework accepted the request", which is
     *   as much as any caller — including Android's own CTS — can determine.
     */
    suspend fun toggleFixedPerformanceMode(enabled: Boolean): FixedPerformanceOutcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return FixedPerformanceOutcome(
                accepted = false,
                message = context.getString(R.string.gt_eng_fp_old, Build.VERSION.RELEASE)
            )
        }
        if (!shellRunner.hasPrivilege()) {
            return FixedPerformanceOutcome(
                accepted = false,
                message = context.getString(R.string.gt_needs_root_shizuku_change)
            )
        }

        val result = shellRunner.execSafeResult(
            *PrivilegeCommands.fixedPerformanceArgs(enabled)
        )
        // `cmd power` prints its complaint and returns non-zero when it does not recognise the
        // sub-command, so the exit code is a real answer here rather than the always-0 that
        // `settings put` gives.
        val accepted = result.isSuccess &&
            !result.stderr.contains("Unknown", ignoreCase = true) &&
            !result.stdout.contains("Unknown", ignoreCase = true)

        if (accepted && enabled) {
            // Housekeeping that pairs with the mode rather than being part of it: drop the memory
            // background apps are sitting on, and pin the framework so it is not paged back in
            // mid-frame. Neither is required for the mode to work.
            execute(PrivilegeCommands.COMPACT_BACKGROUND)
            execute(PrivilegeCommands.PINNER_REPIN)
        } else if (accepted) {
            execute(PrivilegeCommands.DEVICE_IDLE_UNFORCE)
        }

        val state = accepted && enabled
        prefs.edit { putBoolean("fixed_perf_manual", state) }
        _isFixedPerformanceMode.value = state

        return FixedPerformanceOutcome(
            accepted = accepted,
            message = if (accepted) {
                if (enabled) {
                    context.getString(R.string.gt_eng_fp_ok)
                } else {
                    context.getString(R.string.gt_eng_fp_off)
                }
            } else {
                context.getString(
                    R.string.gt_eng_fp_no,
                    result.text.trim().ifBlank { context.getString(R.string.gt_eng_fp_no_reason) }
                )
            }
        )
    }

    /**
     * Chooses the render scale the next Gaming Mode activation writes into the game's
     * `game_overlay` intervention, or null for the FPS-only reference entry.
     *
     * Out-of-range values are sanitized to null rather than refused: the stored result is
     * always either a scale the platform accepts or off. Pure preference write — no privilege,
     * no device contact — so it cannot fail and returns the stored value.
     */
    fun setInterventionDownscale(factor: Float?): Float? {
        val sanitized = GameInterventions.sanitizeDownscale(factor)
        prefs.edit { putFloat("intervention_downscale", sanitized ?: 0f) }
        _interventionDownscale.value = sanitized
        return sanitized
    }

    /**
     * Chooses the touch speed for the next Gaming Mode activation, or null for stock.
     *
     * Out-of-range values sanitize to stock rather than being refused: the stored result is
     * always either a speed the platform accepts or untouched. Pure preference write — returns
     * the stored value.
     */
    fun setPointerSpeedChoice(speed: Int?): Int? {
        val stored = PointerSpeed.sanitize(speed)
        prefs.edit { putInt("pointer_speed_choice", stored ?: 0) }
        _pointerSpeedChoice.value = stored
        return stored
    }

    /** @return true when the setting actually holds the requested value afterwards. */
    suspend fun toggleAlwaysFinishActivities(enabled: Boolean): Boolean {
        val target = if (enabled) 1 else 0
        execute("settings put global always_finish_activities $target")
        // `settings put` is silent on success, so confirm by reading the value back.
        val applied = getAlwaysFinishActivities() == target
        _alwaysFinishActivities.value = applied && enabled
        return applied
    }

    /**
     * Merges (or removes) `max_cached_processes` inside `activity_manager_constants` instead of
     * replacing the whole CSV, so any other constants the ROM set survive.
     * @return true when the change is visible in settings afterwards.
     */
    suspend fun toggleBackgroundProcessLimit(enabled: Boolean): Boolean {
        val current = getGlobalString("activity_manager_constants").orEmpty()
        val merged = if (enabled) {
            upsertCsvKey(current)
        } else {
            removeCsvKey(current)
        }
        if (merged.isBlank()) {
            execute("settings delete global activity_manager_constants")
        } else {
            execute("settings put global activity_manager_constants $merged")
        }
        val readBack = getGlobalString("activity_manager_constants").orEmpty()
        val applied = readBack.contains("max_cached_processes=1") == enabled
        _backgroundProcessLimit.value = applied && enabled
        return applied
    }

    // The standalone refresh-rate control lives in GameDeveloperOptions, which implements Android's
    // own "Force peak refresh rate" switch (min_refresh_rate as a float, verified by read-back).
    // Gaming mode still writes both min and peak itself, and still restores both from its snapshot.


    /**
     * The packages the package manager currently reports as suspended for user 0, or null when it
     * would not answer usably.
     *
     * One `dumpsys package` call covers every package; the previous implementation ran
     * `pm list packages -s`, where `-s` means *system* packages, so it returned hundreds of
     * running system apps and the trustworthiness canary discarded the answer every time. Null is
     * propagated rather than collapsed to an empty set: "nothing is suspended" and "could not
     * read" must not lead to the same recovery decision.
     */
    private suspend fun querySuspendedPackages(): Set<String>? {
        // Returning unknown preserves recorded recovery targets; refusal must not silently
        // enumerate the entire package inventory or prevent targeted undo commands.
        if (!hasInventoryConsent()) return null
        val result = runCatching {
            shellRunner.execSafeResult("dumpsys", "package")
        }.getOrNull() ?: return null
        if (result.exitCode != 0) return null
        return SuspendVerdict.parseDumpsysSuspended(result.stdout)
    }


    /**
     * Reads the three animation scales out of `Settings.Global`.
     *
     * The `1f` fallbacks are the platform's own defaults for these keys — an unset key genuinely
     * means "normal speed" — so this is the real effective value, not a placeholder. Same getters
     * the reference project uses in `SettingsManager.getWindowAnimationScale` and friends.
     */
    fun refreshAnimationScales() {
        val cr = context.contentResolver
        val w = Settings.Global.getFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
        val t = Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        val a = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        _animationScales.value = Triple(w, t, a)
    }

    /**
     * True when this app has a channel that can write `Settings.Global`.
     *
     * Root and Shizuku both run `settings put`; a plain install cannot, unless `WRITE_SECURE_SETTINGS`
     * was granted over adb, which is the reference project's own fallback path. Anything else and the
     * write will be refused, so the UI is told up front rather than after a silent failure.
     */
    fun canWriteAnimationScales(): Boolean =
        shellRunner.hasPrivilege() || hasWriteSecureSettings()

    private fun hasWriteSecureSettings(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Writes one animation scale and confirms it by reading the value back.
     *
     * Both routes are the reference project's (`SettingsManager.setGlobalFloat`): the privileged
     * shell first, then a direct `Settings.Global.putFloat` for a build where `WRITE_SECURE_SETTINGS`
     * has been granted. `settings put` exits 0 without checking that the key was accepted, so the
     * read-back — not the exit code — decides what this reports.
     *
     * @return true only when the setting now actually holds [value].
     */
    suspend fun setAnimationScale(scale: AnimationScaleKind, value: Float): Boolean =
        withContext(Dispatchers.IO) {
            // Two decimals, matching the reference, so 0.5 is written as "0.50" rather than in
            // whatever form Float.toString() happens to pick.
            val formatted = String.format(java.util.Locale.US, "%.2f", value)

            if (shellRunner.hasPrivilege()) {
                shellRunner.execSafeResult("settings", "put", "global", scale.key, formatted)
            }
            if (!readsBack(scale, value) && hasWriteSecureSettings()) {
                runCatching { Settings.Global.putFloat(context.contentResolver, scale.key, value) }
            }

            val applied = readsBack(scale, value)
            refreshAnimationScales()
            applied
        }

    /** Compares numerically: a ROM may store "0.5" for a written "0.50", and both mean the same. */
    private fun readsBack(scale: AnimationScaleKind, value: Float): Boolean {
        val current = runCatching {
            Settings.Global.getFloat(context.contentResolver, scale.key, Float.NaN)
        }.getOrDefault(Float.NaN)
        return !current.isNaN() && kotlin.math.abs(current - value) < 0.005f
    }


    suspend fun execute(command: String): String = shellRunner.exec(command)

    // Partial visibility is fine: invisible packages are excluded, never suspended.
    private fun getSuspendTargets(activeGamePkg: String?): List<String> {
        if (!hasInventoryConsent()) return emptyList()
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appPrefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val userGames = appPrefs.getStringSet("user_games", emptySet()) ?: emptySet()
        val removedGames = appPrefs.getStringSet("removed_games", emptySet()) ?: emptySet()
        val launcherPkgs = getLauncherPackages()
        val userApps = installedApps.filter { ai ->
            val pkg = ai.packageName
            val isLibraryGame = (ai.category == ApplicationInfo.CATEGORY_GAME || pkg in userGames) && pkg !in removedGames
            val isWhitelisted = pkg == activeGamePkg || isLibraryGame || pkg in hardWhitelist ||
                pkg in systemCritical || pkg in gamingDaemons || pkg in launcherPkgs
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && !isWhitelisted
        }.map { it.packageName }
        val googleApps = googleSafeToSuspend.filter { pkg ->
            val isInLibrary = pkg in userGames || try {
                pm.getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_GAME && pkg !in removedGames
            } catch (_: Exception) { false }
            pkg != activeGamePkg && pkg !in launcherPkgs && !isInLibrary &&
                installedApps.any { it.packageName == pkg }
        }
        // OEM bloat is FLAG_SYSTEM, so the user-app sweep above can never reach it — this list is
        // the only path a preinstalled package has into the sweep. Curated per vendor in
        // [OemPackageResolver] (the reference's OemPackageResolver.VIVO_SAFE_TO_SUSPEND, verbatim),
        // and already disjoint from [systemCritical]/[gamingDaemons], whose load-bearing vivo
        // processes must stay out.
        val oemApps = OemPackageResolver.packagesToSuspend(
            isVivoOrIqoo = isVivoOrIqoo(),
            isInstalled = { pkg -> installedApps.any { it.packageName == pkg } }
        ).filter { pkg ->
            pkg != activeGamePkg && pkg !in launcherPkgs &&
                pkg !in userGames &&
                try {
                    pm.getApplicationInfo(pkg, 0).category != ApplicationInfo.CATEGORY_GAME
                } catch (_: Exception) { true }
        }
        // The user's keep list is subtracted last, from all three halves: an app the user asked
        // to keep running is never a sweep target, so it never gets the AppOps background
        // restriction that rides this sweep either, and `manualBoostRam`'s force-stop pass
        // spares it. It also needs no wake-up on deactivation — it was never frozen.
        val exempt = suspendExemptStore.getExemptPackages()
        return (userApps + googleApps + oemApps).filterNot { it in exempt }.distinct()
    }

    /**
     * The packages the automatic sweep can reach on this device, for the keep list's picker:
     * every user-installed app plus the curated Google and OEM lists that are actually installed.
     *
     * Read-only, and deliberately the sweep's own vocabulary rather than "all installed apps":
     * a system app outside the curated lists is never suspended, so offering it as a keep target
     * would promise a protection nothing needs. The picker filters this to what it can label.
     */
    fun suspendCandidatePackages(): List<String> {
        if (!hasInventoryConsent()) return emptyList()
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val userApps = installedApps
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { it.packageName }
        val installedNames = installedApps.mapTo(mutableSetOf()) { it.packageName }
        val googleApps = googleSafeToSuspend.filter { it in installedNames }
        val oemApps = OemPackageResolver.packagesToSuspend(
            isVivoOrIqoo = isVivoOrIqoo(),
            isInstalled = { pkg -> pkg in installedNames }
        )
        return (userApps + googleApps + oemApps).distinct()
    }

    private fun getLauncherPackages(): Set<String> {
        if (!hasInventoryConsent()) return emptySet()
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    private fun hasInventoryConsent() = com.catsmoker.app.application.privacy.GamingAccessConsent.allows(
        context, com.catsmoker.app.application.privacy.GamingAccessConsent.INVENTORY
    )

    /**
     * Reads the user's real state and stores it, before activation writes anything at all.
     *
     * Called as the very first step of [enableGamingMode] — ahead of the cache trim, the suspends and
     * every `settings put` — so what lands here is the user's own configuration rather than a value
     * this app had already changed. A second activation over an active one would otherwise capture
     * the optimized values as if they were the originals, and deactivation would "restore" those.
     */
    private suspend fun captureAndSaveSnapshot(packageName: String?): Boolean {
        val minRefresh = readSettingOrNull("system", "min_refresh_rate") ?: return false
        val peakRefresh = readSettingOrNull("system", "peak_refresh_rate") ?: return false
        val touchSpeed = readSettingOrNull("system", "touch_response_speed") ?: return false
        // No abort here: pointer_speed is only ever written when the user picked a speed, so
        // an unreadable key means "leave it alone", not "un-restorable, touch nothing".
        val pointerSpeed = readSettingOrNull("system", "pointer_speed")
        val displayMode = readSettingOrNull("secure", "user_preferred_display_mode_id") ?: return false
        // The two developer options Gaming Mode now also sets. Both usually come back
        // `existed = false`, which is a real answer and tells the revert to delete rather than write.
        val alwaysFinish = readSettingOrNull("global", Settings.Global.ALWAYS_FINISH_ACTIVITIES)
        val amConstants = readSettingOrNull("global", "activity_manager_constants")
        // Whether the user's own Background Data Restriction switch was already on. Recorded before
        // activation touches netpolicy, so deactivation can tell "we engaged it" from "they did".
        val restrictedBefore = runCatching { backgroundDataRestrictor.isEngaged() }.getOrDefault(false)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        // The Do Not Disturb filter the user had. Read before activation forces
        // INTERRUPTION_FILTER_NONE, so a priority-only DND they set themselves comes back intact.
        val originalFilter = runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .currentInterruptionFilter
        }.getOrNull()
        
        val cr = context.contentResolver
        val origBrightnessMode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        val origRotation = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1)

        var uid: Int? = null
        var uidWhitelistedBefore = false
        if (packageName != null) {
            uid = runCatching { context.packageManager.getApplicationInfo(packageName, 0).uid }.getOrNull()
            uidWhitelistedBefore = (uid != null) && isUidWhitelisted(uid)
        }
        val isVivo = (packageName != null) && isVivoOrIqoo()
        val vivoCube = if (isVivo) getGlobalString(vivoGameCubeApps) else null
        val vivoSpeed = if (isVivo) getGlobalString(vivoSpeedModeApps) else null
        // The two high-refresh-rate whitelist CSVs the reference also writes. Same null contract
        // as the pair above: null when there is no game or the device is not vivo/iQOO, and a
        // blank string when the key simply was not set (meaning "delete it again on the way out").
        val vivoHighRefresh = if (isVivo) getGlobalString(vivoHighRefreshRateApps) else null
        val vivoScreenRefresh = if (isVivo) getGlobalString(vivoScreenRefreshRateAppsList) else null
        // The vivo display-mode lock's second write. Recorded whenever the device is vivo/iQOO
        // (not only when a game is set): the lock runs for the session, and the revert has to
        // be able to put the key back even if the game entry vanished meanwhile.
        val vivoRefreshRateMode = if (isVivoOrIqoo()) {
            readSettingOrNull("global", VivoDisplayMode.KEY_SCREEN_REFRESH_RATE_MODE)
        } else null
        // The game's own intervention-table entry, read *before* activation writes one, so the
        // revert can put back what was there or delete ours if nothing was. Game interventions
        // exist from Android 12 only; an unreadable answer is stored as null, which the revert
        // reads as "leave the flag alone" rather than guessing at its prior shape.
        val gameOverlay = if (packageName != null && deviceCapabilities.supportsGameInterventions) {
            when (val overlay = gameInterventions.readOverlay(packageName)) {
                is GameInterventions.OverlayValue.Set -> SettingValue(overlay.value, existed = true)
                is GameInterventions.OverlayValue.Unset -> SettingValue("", existed = false)
                is GameInterventions.OverlayValue.Unreadable -> null
            }
        } else null

        // Qualcomm's vendor GPU properties, as the vendor booted with them. A blank property (the
        // normal case on non-Qualcomm silicon) records existed = false, which both the
        // activation-side gate and the revert read as "never touch this property".
        val vendorGpuMode = propSettingValue(VENDOR_GPU_MODE)
        val vendorGfxLowQuality = propSettingValue(VENDOR_GFX_LOW_QUALITY)
        // The game FPS hint too. On a Qualcomm device that never set it this records
        // existed = false — and the revert then leaves our hint in place until a reboot clears
        // the debug property area, the one deletion Android offers. See the snapshot field's
        // KDoc; recorded either way so a prior value is never lost.
        val debugVendorQtiGameFps = propSettingValue(DEBUG_VENDOR_QTI_GAME_FPS)

        // MediaTek game-mode knobs, as the vendor booted with them. Only nodes the device
        // actually has are recorded — read failures mean "never touch", and an empty map tells
        // both the activation gate and the revert there is nothing to do.
        val mediatekGameNodes = captureMediaTekGameNodes()

        // Snapdragon widen-only priors (policy ceilings with headroom + narrow game cpusets, plus
        // every schedutil / msm_performance / core_ctl / cpu_boost node a rule would write), as the
        // kernel held them. Only nodes the device actually has are recorded — read failures mean
        // "never touch", and an empty map tells both the activation gate and the revert there is
        // nothing to do.
        val snapdragonCpuNodes = captureSnapdragonCpuNodes()

        // The five SmartPack-derived families. Each capture records only nodes a rule would
        // actually change, so an empty map is the gate that keeps apply and revert out of a kernel
        // that has nothing to offer (and out of a value that is already at its target).
        val cpuInputBoostNodes = captureCpuInputBoostNodes()
        val cpuGovernorNodes = captureCpuGovernorNodes()
        val spectrumProfile = captureSpectrumProfile()
        val storageTuningNodes = captureStorageTuningNodes()
        val memoryTuningNodes = captureMemoryTuningNodes()

        val snapshot = GamingOptimizationSnapshot(
            activeGamePackage = packageName,
            activeGameUid = uid,
            timestamp = System.currentTimeMillis(),
            minRefreshRate = minRefresh,
            peakRefreshRate = peakRefresh,
            touchResponseSpeed = touchSpeed,
            pointerSpeed = pointerSpeed,
            userPreferredDisplayModeId = displayMode,
            affectedPackages = emptySet(),
            uidWhitelistedBefore = uidWhitelistedBefore,
            vivoGameCubeApps = vivoCube,
            vivoSpeedModeApps = vivoSpeed,
            vivoHighRefreshRateApps = vivoHighRefresh,
            vivoScreenRefreshRateAppsList = vivoScreenRefresh,
            vivoScreenRefreshRateMode = vivoRefreshRateMode,
            originalRingtoneVolume = currentVol,
            originalBrightnessMode = origBrightnessMode,
            originalRotation = origRotation,
            alwaysFinishActivities = alwaysFinish,
            activityManagerConstants = amConstants,
            backgroundDataRestrictedBefore = restrictedBefore,
            originalInterruptionFilter = originalFilter,
            gameOverlay = gameOverlay,
            vendorGpuMode = vendorGpuMode,
            vendorGfxLowQuality = vendorGfxLowQuality,
            debugVendorQtiGameFps = debugVendorQtiGameFps,
            mediatekGameNodes = mediatekGameNodes,
            snapdragonCpuNodes = snapdragonCpuNodes.takeIf { it.isNotEmpty() },
            cpuInputBoostNodes = cpuInputBoostNodes.takeIf { it.isNotEmpty() },
            cpuGovernorNodes = cpuGovernorNodes.takeIf { it.isNotEmpty() },
            spectrumProfileProperty = spectrumProfile?.first,
            spectrumProfile = spectrumProfile?.second,
            storageTuningNodes = storageTuningNodes.takeIf { it.isNotEmpty() },
            memoryTuningNodes = memoryTuningNodes.takeIf { it.isNotEmpty() }
        )
        prefs.edit { putString("last_snapshot", snapshot.toJson()) }
        return true
    }

    /**
     * Reads one setting for the pre-activation snapshot.
     *
     * The provider is asked first: reading `Settings.System`/`Secure`/`Global` needs no permission
     * at all, so it cannot fail for the reasons a shell can (no root, dead Shizuku binder) and it
     * costs no process fork. A key that is simply not set comes back as
     * `SettingValue("", existed = false)` — a legitimate answer, and the reason activation no
     * longer aborts on devices that never shipped `touch_response_speed`.
     *
     * @return null only when neither the provider nor a privileged shell could answer at all,
     *   which is the same "un-restorable state, do not touch anything" signal the reference uses.
     */
    private suspend fun readSettingOrNull(namespace: String, key: String): SettingValue? {
        readSettingViaProvider(namespace, key)?.let { return it }
        val result = shellRunner.execSafeResult("settings", "get", namespace, key)
        if (!result.isSuccess) return null
        return SettingValue.fromCommandOutput(result.stdout)
    }

    private fun readSettingViaProvider(namespace: String, key: String): SettingValue? {
        val cr = context.contentResolver
        return try {
            val raw = when (namespace) {
                "system" -> Settings.System.getString(cr, key)
                "secure" -> Settings.Secure.getString(cr, key)
                "global" -> Settings.Global.getString(cr, key)
                else -> return null
            }
            SettingValue.fromCommandOutput(raw.orEmpty())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Writes a setting and confirms it stuck by reading it back.
     *
     * `settings put` exits 0 with empty stdout even on ROMs that accept the command and silently
     * drop an unknown key, so the read-back is the only honest success signal. Numbers are compared
     * numerically because some providers normalise `120` to `120.0`.
     */
    // Single-namespace writer: every caller targets Settings.System, so there is no
    // namespace parameter to warn about.
    private suspend fun putSettingVerified(key: String, value: String): Boolean {
        shellRunner.execSafeResult("settings", "put", "system", key, value)
        val readBack = readSettingOrNull("system", key) ?: return false
        return SettingValue(value, existed = true).matchesAfterRestore(readBack)
    }

    /** @return true when the game's uid was actually added to the background-data whitelist. */
    private suspend fun applyPerGameOptimizations(packageName: String): Boolean {
        try {
            val snapshot = prefs.getString("last_snapshot", null)
                ?.let { GamingOptimizationSnapshot.fromJson(it) }
            val uid = snapshot?.activeGameUid
                ?: runCatching { context.packageManager.getApplicationInfo(packageName, 0).uid }.getOrNull()
                ?: return false

            // Apply volume override (mute ringtone)
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try { audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0) } catch (_: Exception) {}

            // Apply Settings overrides if possible
            if (Settings.System.canWrite(context)) {
                val cr = context.contentResolver
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
            }

            execute("cmd netpolicy add restrict-background-whitelist $uid")
            applyPerformanceGameMode(packageName)
            if (isVivoOrIqoo()) {
                // All four whitelist CSVs the reference writes, not just the first two: the skin's
                // high-refresh-rate lists decide whether the panel holds its peak for the game, so
                // writing only the Game Cube pair left the game fast-pathed in one surface and an
                // ordinary app in the other two. Snapshot already recorded each key's prior value.
                for (key in listOf(vivoGameCubeApps, vivoSpeedModeApps, vivoHighRefreshRateApps, vivoScreenRefreshRateAppsList)) {
                    val current = getGlobalString(key) ?: ""
                    execute("settings put global $key ${appendToCsv(current, packageName)}")
                }
            }
            // netpolicy has no read-back on its own, but the whitelist can be listed.
            return isUidWhitelisted(uid)
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Puts the game in the platform's performance mode through the GameManager CLI.
     *
     * The engine used to send `set` with `--mode performance --fps <peak>`, which is not
     * a shape `set` accepts: `set` parses its flags as an intervention config, and `--mode`
     * reaches an integer parse as the string "performance" —
     * `NumberFormatException: For input string: "performance"` on an Android 13 POCO X3
     * NFC (2026-10-19, found while cross-checking `reference/gamingtools/TMPAD`). Mode
     * selection is its own subcommand, and the device's own `list-modes` answer decides
     * whether performance is even offered, so a ROM without that path is reported as not
     * applicable rather than silently failing. The result is logged, never asserted: the
     * engine only claims what `list-modes` read back.
     */
    private suspend fun applyPerformanceGameMode(pkg: String) {
        if (!deviceCapabilities.supportsGameInterventions) return
        val before = shellRunner.execSafeResult("cmd", "game", "list-modes", pkg)
        val offered = GameCommandParser.parseModes(before.stdout)
        if (offered != null && !offered.offers("performance")) {
            logBooster(BoosterLogType.INFO, pkg, "Game mode: performance is not offered by this ROM")
            return
        }
        val set = shellRunner.execSafeResult(
            *GameCommandParser.performanceModeArgs(pkg).toTypedArray()
        )
        if (!set.isSuccess) {
            logBooster(BoosterLogType.INFO, pkg, "Game mode: performance refused (${set.text.trim()})")
            return
        }
        val after = shellRunner.execSafeResult("cmd", "game", "list-modes", pkg)
        val current = GameCommandParser.parseModes(after.stdout)?.current
        logBooster(
            BoosterLogType.INFO,
            pkg,
            if (current == "performance") {
                "Game mode: performance"
            } else {
                "Game mode: performance not confirmed"
            }
        )
    }

    /**
     * Records, without writing, which OEM refresh keys and per-SoC properties this
     * device already carries.
     *
     * Both vocabularies come from counter-example references (JordanTweaks' refresh
     * enum, BoostX's per-SoC modules); their scripts are not ported. This pass exists so
     * a future gated lever can be argued from the device's own answer — which key is
     * real, what rate or value it holds — instead of from a copied script. A device
     * that carries none of the candidates logs nothing, which is itself an answer.
     */
    private suspend fun logDeviceVendorEvidence(pkg: String?) {
        runCatching {
            val oem = refreshController.probeOemKeys()
            if (oem.isNotEmpty()) {
                logBooster(
                    BoosterLogType.INFO,
                    pkg,
                    "OEM refresh keys: " + oem.joinToString {
                        "${it.key.namespace}/${it.key.key}=${it.hz}"
                    }
                )
            }
        }
        val vendor = when {
            deviceCapabilities.isKirin -> VendorPerformanceKeys.Vendor.KIRIN
            deviceCapabilities.isExynos -> VendorPerformanceKeys.Vendor.EXYNOS
            deviceCapabilities.isUnisoc -> VendorPerformanceKeys.Vendor.UNISOC
            deviceCapabilities.isMediaTek -> VendorPerformanceKeys.Vendor.MEDIATEK
            else -> null
        } ?: return
        val keys = VendorPerformanceKeys.candidatesFor(vendor)
        if (keys.isEmpty()) return
        val props = runCatching { shellRunner.execSafeResult("getprop") }.getOrNull() ?: return
        val readings = VendorPerformanceKeys.readPresent(props.stdout, keys)
        if (readings.isNotEmpty()) {
            logBooster(
                BoosterLogType.INFO,
                pkg,
                "Vendor keys: " + readings.joinToString { "${it.key}=${it.value ?: "unset"}" }
            )
        }
    }

    /**
     * Restores everything the snapshot recorded, reporting what refused to go back.
     *
     * @return problems for the deactivation report as notices resolved by the card at
     *   composition (see [GamingModeNotice]); empty when everything landed. A missing or
     *   unparsable snapshot answers empty rather than accusing the device — there is no record
     *   to restore from, so there is nothing to verify against.
     */
    private suspend fun revertFromSnapshot(): List<GamingModeNotice> {
        val json = prefs.getString("last_snapshot", null) ?: return emptyList()
        val snapshot = GamingOptimizationSnapshot.fromJson(json) ?: return emptyList()
        val problems = mutableListOf<GamingModeNotice>()
        
        // Restore volume
        snapshot.originalRingtoneVolume?.let { vol ->
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try { audioManager.setStreamVolume(AudioManager.STREAM_RING, vol, 0) } catch (_: Exception) {}
        }

        // Restore the Do Not Disturb filter the user actually had, which may itself have been a DND.
        snapshot.originalInterruptionFilter?.let { filter ->
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                runCatching { nm.setInterruptionFilter(filter) }
            }
        }

        // Restore Settings
        if (Settings.System.canWrite(context)) {
            val cr = context.contentResolver
            snapshot.originalBrightnessMode?.let { Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, it) }
            snapshot.originalRotation?.let { Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, it) }
        }

        restoreSetting("system", "min_refresh_rate", snapshot.minRefreshRate)?.let { problems += it }
        restoreSetting("system", "peak_refresh_rate", snapshot.peakRefreshRate)?.let { problems += it }
        restoreSetting("system", "touch_response_speed", snapshot.touchResponseSpeed)?.let { problems += it }
        restoreSetting("system", "pointer_speed", snapshot.pointerSpeed)?.let { problems += it }
        restoreSetting("secure", "user_preferred_display_mode_id", snapshot.userPreferredDisplayModeId)?.let { problems += it }
        // The vivo display-mode lock's vendor switch, back to the recorded value (or deleted
        // when it was not set). Null - every non-vivo device, and old snapshots - writes nothing.
        restoreSetting("global", VivoDisplayMode.KEY_SCREEN_REFRESH_RATE_MODE, snapshot.vivoScreenRefreshRateMode)?.let { problems += it }

        // The two developer options, back to whatever they were - including "was not set at all",
        // which restoreSetting turns into a delete rather than a write of some assumed default.
        restoreSetting("global", Settings.Global.ALWAYS_FINISH_ACTIVITIES, snapshot.alwaysFinishActivities)?.let { problems += it }
        restoreSetting("global", "activity_manager_constants", snapshot.activityManagerConstants)?.let { problems += it }
        _alwaysFinishActivities.value =
            getAlwaysFinishActivities() == 1
        _backgroundProcessLimit.value =
            getGlobalString("activity_manager_constants").orEmpty().contains("max_cached_processes=1")

        // Lift the metered-background block only if this session put it there. When the user's own
        // switch was already on before activation, turning it off here would be undoing their
        // setting — unless a previous revert retained verified-still-blocked UIDs, which are
        // ours to retry. The outcome is reported rather than dropped: a refused unblock that
        // stays silent is another "still stopped after reverting".
        if (!snapshot.backgroundDataRestrictedBefore || backgroundDataRestrictor.hasRetainedBlocks()) {
            val dataOutcome = runCatching { backgroundDataRestrictor.disable() }.getOrNull()
            if (dataOutcome == null) {
                problems += GamingModeNotice.Res(R.string.gt_eng_un_data_revert)
            } else if (!dataOutcome.success) {
                // The restrictor's own words (already resolved where it runs) — carried verbatim.
                problems += GamingModeNotice.Raw(dataOutcome.message)
            }
        }

        if (snapshot.activeGamePackage != null) {
            val pkg = snapshot.activeGamePackage
            execute("cmd activity set-bg-restriction-level --user 0 $pkg adaptive_bucket")
            execute("am set-standby-bucket --user 0 $pkg working_set")
            execute(PrivilegeCommands.deviceIdleWhitelistRemove(pkg))
            execute("cmd game reset --user 0 $pkg")
            // The intervention table goes back to whatever it held before activation — a value put
            // back verbatim, or ours deleted when there was none. Only attempted when the snapshot
            // actually recorded the prior state (null means "do not touch it at all"). A refused
            // restore is left as-is rather than retried as a blind delete: deleting anyway would
            // destroy a prior entry the device told us existed but would not accept back.
            snapshot.gameOverlay?.let { previous ->
                gameInterventions.restore(pkg, previous)
            }
        }
        if (snapshot.activeGameUid != null && !snapshot.uidWhitelistedBefore) {
            execute("cmd netpolicy remove restrict-background-whitelist ${snapshot.activeGameUid}")
        }
        if (isVivoOrIqoo() && (snapshot.activeGamePackage != null)) {
            restoreGlobalSetting(vivoGameCubeApps, snapshot.vivoGameCubeApps)
            restoreGlobalSetting(vivoSpeedModeApps, snapshot.vivoSpeedModeApps)
            restoreGlobalSetting(vivoHighRefreshRateApps, snapshot.vivoHighRefreshRateApps)
            restoreGlobalSetting(vivoScreenRefreshRateAppsList, snapshot.vivoScreenRefreshRateAppsList)
        }
        // Qualcomm GPU mode, back to what the vendor booted with. Only when the snapshot recorded
        // the property as existing — a blank one was never set, and setprop can neither create nor
        // delete a property, so there is nothing an absent one could be restored *to*.
        restoreProp(VENDOR_GPU_MODE, snapshot.vendorGpuMode)
        restoreProp(VENDOR_GFX_LOW_QUALITY, snapshot.vendorGfxLowQuality)
        // The game FPS hint. Same rule — but a hint that did not exist before cannot be deleted,
        // so this returns it only when the vendor had one, and the created hint otherwise stays
        // until the next reboot. That bounded leftover is stated in the snapshot field's KDoc and
        // in GamingModeReport.qtiGameFps's, not hidden.
        restoreProp(DEBUG_VENDOR_QTI_GAME_FPS, snapshot.debugVendorQtiGameFps)
        // MediaTek game-mode nodes, back to the snapshot values. Only nodes the snapshot
        // recorded are touched — an empty map means there was never anything to restore.
        restoreMediaTekGameNodes(snapshot.mediatekGameNodes)
        // Spectrum first: restoring the profile can make the kernel re-derive its own governor.
        restoreSpectrumProfile(snapshot.spectrumProfileProperty, snapshot.spectrumProfile)
        // Then the recorded governor. This has to precede the Snapdragon prior map: a policy's
        // `schedutil/*` tunables only accept writes while schedutil is the active governor, so
        // restoring the tuning first (as this used to) silently refused every rate-limit and
        // hispeed write whenever Gaming Mode had switched the policy to `performance`.
        restoreKernelNodeMap(snapshot.cpuGovernorNodes.orEmpty()).forEach { node ->
            problems += GamingModeNotice.Res(R.string.gt_eng_restore_refused, listOf(node))
        }
        // Snapdragon CPU nodes, back to the snapshot priors. Empty means the capture found
        // nothing widenable, so there is nothing to restore.
        restoreKernelNodeMap(snapshot.snapdragonCpuNodes.orEmpty()).forEach { node ->
            problems += GamingModeNotice.Res(R.string.gt_eng_restore_refused, listOf(node))
        }
        // The rest of the SmartPack-derived families, same measured-priors contract. Empty maps
        // are old snapshots or kernels that never carried the interface: nothing to restore.
        restoreKernelNodeMap(snapshot.cpuInputBoostNodes.orEmpty()).forEach { node ->
            problems += GamingModeNotice.Res(R.string.gt_eng_restore_refused, listOf(node))
        }
        restoreKernelNodeMap(snapshot.storageTuningNodes.orEmpty()).forEach { node ->
            problems += GamingModeNotice.Res(R.string.gt_eng_restore_refused, listOf(node))
        }
        restoreKernelNodeMap(snapshot.memoryTuningNodes.orEmpty()).forEach { node ->
            problems += GamingModeNotice.Res(R.string.gt_eng_restore_refused, listOf(node))
        }
        prefs.edit { remove("last_snapshot") }
        return problems
    }

    private suspend fun restoreGlobalSetting(key: String, original: String?) {
        if (original == null) return
        if (original.isBlank()) execute("settings delete global $key")
        else execute("settings put global $key $original")
    }

    /**
     * Whether [uid] is exempt from Data Saver, per `cmd netpolicy`.
     *
     * The command answers on a single line — `Restrict background whitelisted UIDs: 10123 10456` — so
     * the UIDs are tokens within that line. Treating each line as one UID (as this did) never matched
     * anything, which made a successful whitelist report as a failure in the activation result.
     */
    private suspend fun isUidWhitelisted(uid: Int): Boolean {
        val output = execute("cmd netpolicy list restrict-background-whitelist")
        return uid in BackgroundDataRestrictor.parseUidList(output)
    }

    /** CSV append now lives in [OemPackageResolver.appendPackage] (pure, and pinned by tests). */
    private fun appendToCsv(list: String, pkg: String): String = OemPackageResolver.appendPackage(list, pkg)

    private fun upsertCsvKey(csv: String): String {
        val kept = csv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("max_cached_processes=") }
        return (kept + "max_cached_processes=1").joinToString(",")
    }

    private fun removeCsvKey(csv: String): String {
        return csv.split(",")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("max_cached_processes=") }
            .joinToString(",")
    }

    /** One verified wake-up; false means still suspended, or the shell never answered. */
    private suspend fun unsuspendOne(pkg: String): Boolean {
        val result = runCatching {
            shellRunner.execSafeResult(*PrivilegeCommands.unsuspendArgs(pkg))
        }.getOrNull() ?: return false
        return SuspendVerdict.isUnsuspendConfirmed(result.exitCode, result.stdout)
    }

    /**
     * Restores one snapshot setting and verifies that the device accepted it.
     *
     * `settings put`/`delete` can exit 0 without changing anything, so the read-back is the only
     * honest success signal (see [SettingValue.matchesAfterRestore]). A null [sv] means the
     * snapshot did not record the key: do not touch it. A refused restore is returned as a
     * notice for the deactivation report; it is not silently swallowed.
     */
    private suspend fun restoreSetting(
        namespace: String,
        key: String,
        sv: SettingValue?
    ): GamingModeNotice? {
        if (sv == null) return null
        val cmd = if (sv.existed && sv.value.isNotBlank()) {
            "settings put $namespace $key ${sv.value}"
        } else {
            "settings delete $namespace $key"
        }
        execute(cmd)
        val readBack = readSettingOrNull(namespace, key)
            ?: return GamingModeNotice.Res(R.string.gt_eng_restore_refused, listOf("$namespace/$key"))
        return if (sv.matchesAfterRestore(readBack)) {
            null
        } else {
            GamingModeNotice.Res(R.string.gt_eng_restore_refused, listOf("$namespace/$key"))
        }
    }

    /**
     * Sets Qualcomm's vendor GPU properties for the duration of Gaming Mode and confirms them by
     * read-back — the payload `GameUnlocker-main/cpp/controller.cpp` ships its daemon for, minus
     * the daemon (see [GamingModeReport.gpuPerformanceMode]).
     *
     * @return null when the device carries neither property — not applicable, every non-Qualcomm
     *   SoC — otherwise whether every property it does carry landed at its target value.
     */
    private suspend fun applyVendorGpuPerformance(): Boolean? {
        val modePresent = readProp(VENDOR_GPU_MODE).isNotBlank()
        val lowQualityPresent = readProp(VENDOR_GFX_LOW_QUALITY).isNotBlank()
        if (!modePresent && !lowQualityPresent) return null
        var confirmed = 0
        var attempted = 0
        if (modePresent) {
            attempted++
            if (setPropVerified(VENDOR_GPU_MODE, "performance")) confirmed++
        }
        if (lowQualityPresent) {
            attempted++
            if (setPropVerified(VENDOR_GFX_LOW_QUALITY, "1")) confirmed++
        }
        return confirmed == attempted
    }

    /**
     * Hints Qualcomm's perf framework at [peakHz] through `debug.vendor.qti.game.fps`, the whole
     * payload of `GameUnlocker-main/common/post-fs-data.sh` (see [GamingModeReport.qtiGameFps]
     * for why the `persist.` twin is deliberately not set and why the value is measured rather
     * than the reference's hard-coded 120).
     *
     * @return null when the device is not Qualcomm — not applicable — otherwise whether the
     *   property read back at [peakHz].
     */
    private suspend fun applyQtiGameFpsHint(peakHz: Int): Boolean? {
        if (!isQualcommDevice()) return null
        return setPropVerified(DEBUG_VENDOR_QTI_GAME_FPS, peakHz.toString())
    }

    /**
     * Whether the device's own property dump names a Qualcomm vendor property. The reference's
     * `isQualcomm()` matched `ro.hardware` against a prefix table (qcom, kalama, taro, …); the
     * dump itself is the measured fact — every Qualcomm device carries `ro.vendor.qti.*`
     * properties because its whole vendor stack defines them — and it cannot go stale the way a
     * chipset-name list does.
     */
    private suspend fun isQualcommDevice(): Boolean {
        val dump = shellRunner.execSafeResult("getprop")
        return dump.isSuccess && dump.stdout.contains("[ro.vendor.qti.")
    }

    /** The just-written snapshot, or null when none was recorded or it no longer parses. */
    private fun readLastSnapshot(): GamingOptimizationSnapshot? =
        prefs.getString("last_snapshot", null)?.let { GamingOptimizationSnapshot.fromJson(it) }

    /**
     * Captures the MediaTek game-mode nodes' current values for the snapshot. Only nodes the
     * device actually has are recorded — read failures (absent dir, SELinux denial) mean
     * "never touch", and an empty map tells both the activation gate and the revert there is
     * nothing to do. Reads need no privilege; only the writes below are root-gated.
     */
    private suspend fun captureMediaTekGameNodes(): Map<String, String> {
        val priors = linkedMapOf<String, String>()
        for (node in MediaTekGameMode.gedParams.keys) {
            readSysfsNode(node)?.let { priors[node] = it }
        }
        readSysfsNode(MediaTekGameMode.PPM_POLICY_STATUS_NODE)?.let {
            priors[MediaTekGameMode.PPM_POLICY_STATUS_NODE] = it
        }
        return priors
    }

    /**
     * Applies the MediaTek game-mode tables (GED boost parameters + PPM policy states from
     * HunterX-Reborn-II's service payload) and confirms every write by read-back.
     *
     * @return null when [priors] is empty — no nodes, not applicable — otherwise whether every
     *   attempted node landed at its target. Root-only: a shell-uid session cannot write
     *   `/sys`, and that surfaces here as a refusal rather than as silence.
     */
    private suspend fun applyMediaTekGameMode(priors: Map<String, String>): Boolean? {
        if (priors.isEmpty()) return null
        if (!shellRunner.isRootAvailable()) return false
        val plan = MediaTekGameMode.writePlan(priors)
        var attempted = 0
        var confirmed = 0
        for ((node, target) in plan) {
            attempted++
            if (writeSysfsVerified(node, target)) confirmed++
        }
        if (priors.containsKey(MediaTekGameMode.PPM_POLICY_STATUS_NODE)) {
            for (write in MediaTekGameMode.ppmGameWrites()) {
                attempted++
                shellRunner.execSafeResult("sh", "-c", "echo $write > ${MediaTekGameMode.PPM_POLICY_STATUS_NODE}")
            }
            // The node reports status text rather than re-writable content, so the read-back
            // is a re-parse: the dump must now describe exactly the states just written.
            if (verifyPpmStates(MediaTekGameMode.ppmPolicies)) confirmed += MediaTekGameMode.ppmPolicies.size
        }
        return confirmed == attempted
    }

    /**
     * Puts the MediaTek nodes back to their snapshot values. Only nodes the snapshot recorded
     * are touched; a PPM status dump is restored by re-issuing its parsed per-index states.
     * Root-gated like the apply — without it there is nothing that could have changed them
     * through this path.
     */
    private suspend fun restoreMediaTekGameNodes(priors: Map<String, String>) {
        if (priors.isEmpty() || !shellRunner.isRootAvailable()) return
        for ((node, prior) in priors) {
            if (node == MediaTekGameMode.PPM_POLICY_STATUS_NODE) {
                for (write in MediaTekGameMode.ppmRestoreWrites(prior)) {
                    shellRunner.execSafeResult("sh", "-c", "echo $write > $node")
                }
            } else if (isSafeSysfsValue(prior)) {
                shellRunner.execSafeResult("sh", "-c", "echo $prior > $node")
            }
        }
    }

    /**
     * Captures the Snapdragon widen-only priors for the snapshot: the channel's CPU half — the
     * current `scaling_max_freq` of each policy with headroom toward its own hardware max, plus the
     * current bodies of narrow game-visible cpusets — and, from [SnapdragonKernelNodes], the
     * schedutil rate limit / hispeed jump / iowait boost of every policy, `msm_performance`'s touch
     * boost and declared CPU ceilings, every `core_ctl` cluster's floor and ceiling, and
     * `cpu_boost`'s input boost switch and per-CPU frequencies.
     *
     * Only nodes the device actually has *and* a rule would actually write are recorded — an
     * unlistable policy dir, an unreadable node, an unparseable table or a node already at its
     * widen limit means "never touch", and an empty map tells both the activation gate and the
     * revert there is nothing to do. Policy dirs are enumerated through the privileged shell
     * because locked-down kernels hide even the listing from the app UID; every value is read with
     * [readSysfsNode], which needs no privilege by itself.
     */
    private suspend fun captureSnapdragonCpuNodes(): Map<String, String> {
        val priors = linkedMapOf<String, String>()
        val policies = readSnapdragonPolicyStates()
        for ((policyDir, state) in policies) {
            if (SnapdragonCpuWiden.policyMaxTarget(state) == null) continue
            readSysfsNode("$policyDir/${SnapdragonCpuWiden.SCALING_MAX_FREQ}")?.let {
                priors["$policyDir/${SnapdragonCpuWiden.SCALING_MAX_FREQ}"] = it
            }
        }
        captureCpusetPriors(priors)
        val cpuSets = readPolicyCpuSets()
        val hardwareMaxKhz = hardwareMaxByCpu(policies, cpuSets)
        captureSchedutilPriors(priors, policies)
        captureMsmPerformancePriors(priors, hardwareMaxKhz)
        captureCoreCtlPriors(priors, cpuSets)
        captureCpuBoostPriors(priors, hardwareMaxKhz)
        return priors
    }

    /**
     * cpuset priors: the current bodies of the game-visible cpusets, recorded only where
     * [SnapdragonCpuWiden.cpusetWritePlan] would actually add CPUs. An unreadable `present` node
     * records nothing at all, because a set widened against a guessed full set could narrow.
     */
    private suspend fun captureCpusetPriors(priors: MutableMap<String, String>) {
        val fullSet = readSysfsNode(CPU_PRESENT_NODE)
            ?.let(SnapdragonCpuWiden::parseCpuSet).orEmpty()
        if (fullSet.isEmpty()) return
        val cpusetPriors = SnapdragonCpuWiden.CPUSET_CANDIDATES.mapNotNull { node ->
            readSysfsNode(node)?.let { node to it }
        }.toMap()
        for (node in SnapdragonCpuWiden.cpusetWritePlan(cpusetPriors, fullSet).keys) {
            cpusetPriors[node]?.let { priors[node] = it }
        }
    }

    /**
     * schedutil priors for every policy: the up rate limiter (removed to 0), the hispeed jump
     * (raised to the policy's own hardware max, gated on a declared `hispeed_load`) and the iowait
     * boost switch. A policy without a `schedutil` directory — a different governor — contributes
     * nothing, and neither does a node whose rule yields no write.
     */
    private suspend fun captureSchedutilPriors(
        priors: MutableMap<String, String>,
        policies: Map<String, SnapdragonCpuWiden.PolicyState>
    ) {
        for ((policyDir, state) in policies) {
            val dir = "$policyDir/${SnapdragonKernelNodes.SCHEDUTIL_DIR_NAME}"
            val upNode = "$dir/${SnapdragonKernelNodes.UP_RATE_LIMIT_US}"
            readSysfsNode(upNode)?.let { now ->
                if (SnapdragonKernelNodes.noUpRateLimit(now) != null) priors[upNode] = now
            }
            val hispeedNode = "$dir/${SnapdragonKernelNodes.HISPEED_FREQ}"
            readSysfsNode(hispeedNode)?.let { now ->
                val load = readSysfsNode("$dir/${SnapdragonKernelNodes.HISPEED_LOAD}")
                if (SnapdragonKernelNodes.hispeedTarget(now, state.availableKhz, load) != null) {
                    priors[hispeedNode] = now
                }
            }
            val iowaitNode = "$dir/${SnapdragonKernelNodes.IOWAIT_BOOST_ENABLE}"
            readSysfsNode(iowaitNode)?.let { now ->
                if (SnapdragonKernelNodes.enableIfZero(now) != null) priors[iowaitNode] = now
            }
        }
    }

    /**
     * `msm_performance` priors: the touch boost switch and the declared per-CPU ceilings. A
     * `cpu_max_freq` body whose entries are all 0 declares no cap, so the node is not recorded (and
     * therefore never written): there is no ceiling to remove.
     */
    private suspend fun captureMsmPerformancePriors(
        priors: MutableMap<String, String>,
        hardwareMaxKhz: Map<Int, Long>
    ) {
        val base = SnapdragonKernelNodes.MSM_PERFORMANCE_PARAMETERS
        val touchNode = "$base/${SnapdragonKernelNodes.TOUCHBOOST}"
        readSysfsNode(touchNode)?.let { now ->
            if (SnapdragonKernelNodes.enableIfZero(now) != null) priors[touchNode] = now
        }
        val capNode = "$base/${SnapdragonKernelNodes.CPU_MAX_FREQ}"
        readSysfsNode(capNode)?.let { now ->
            if (SnapdragonKernelNodes.raiseExistingCapPairs(now, hardwareMaxKhz).isNotEmpty()) {
                priors[capNode] = now
            }
        }
    }

    /**
     * `core_ctl` priors: every cluster's `min_cpus` and `max_cpus`, recorded only where
     * [SnapdragonKernelNodes.clusterRaise] would raise them toward the cluster's real core count
     * (`related_cpus` of the policy owning the directory). Its hotplug heuristics
     * (`busy_up_thres`, `busy_down_thres`, `offline_delay_ms`, `task_thres`, `enable`) are never
     * recorded because no rule names them.
     */
    private suspend fun captureCoreCtlPriors(
        priors: MutableMap<String, String>,
        cpuSets: Map<String, Set<Int>>
    ) {
        for (dir in readCoreCtlDirs(cpuSets.values.flatten().toSet())) {
            val clusterSize = coreCtlCpu(dir)?.let { cpu -> cpuSets.values.firstOrNull { cpu in it }?.size }
                ?: continue
            for (name in listOf(SnapdragonKernelNodes.MIN_CPUS, SnapdragonKernelNodes.MAX_CPUS)) {
                val node = "$dir/$name"
                readSysfsNode(node)?.let { now ->
                    if (SnapdragonKernelNodes.clusterRaise(now, clusterSize) != null) priors[node] = now
                }
            }
        }
    }

    /**
     * `cpu_boost` priors: the module's master switch and the per-CPU input boost frequencies. The
     * frequencies are recorded only when the device declares a boost window
     * ([SnapdragonKernelNodes.INPUT_BOOST_MS] > 0) — a boost with no duration never happens, so
     * raising its frequencies would change nothing while looking like a success — and only where
     * [SnapdragonKernelNodes.raiseBoostPairs] would actually raise them. The window itself is never
     * written: its length is the device's own tuning, and a raise would be an invented constant.
     */
    private suspend fun captureCpuBoostPriors(
        priors: MutableMap<String, String>,
        hardwareMaxKhz: Map<Int, Long>
    ) {
        val base = SnapdragonKernelNodes.CPU_BOOST_PARAMETERS
        val enabledNode = "$base/${SnapdragonKernelNodes.INPUT_BOOST_ENABLED}"
        readSysfsNode(enabledNode)?.let { now ->
            if (SnapdragonKernelNodes.enableIfZero(now) != null) priors[enabledNode] = now
        }
        val window = readSysfsNode("$base/${SnapdragonKernelNodes.INPUT_BOOST_MS}")?.toLongOrNull()
        if (window == null || window <= 0L) return
        val freqNode = "$base/${SnapdragonKernelNodes.INPUT_BOOST_FREQ}"
        readSysfsNode(freqNode)?.let { now ->
            if (SnapdragonKernelNodes.raiseBoostPairs(now, hardwareMaxKhz).isNotEmpty()) priors[freqNode] = now
        }
    }

    /**
     * `cpu_input_boost` + `/dev/stune` priors, for the nodes [CpuInputBoostNodes] would write: the
     * master switch, the per-CPU `ib_freqs` body (only with a positive declared boost window and
     * only where a frequency has headroom) and the dynamic stune boost (only where the device's own
     * static top-app boost is higher). The static boost itself is never recorded because it is
     * never written — it is the target's reference. Empty when the kernel has no such interface,
     * which both gates apply and makes the revert a no-op.
     */
    private suspend fun captureCpuInputBoostNodes(): Map<String, String> {
        val priors = linkedMapOf<String, String>()
        val parent = CpuInputBoostNodes.PARENT_CANDIDATES.firstOrNull {
            readSysfsNode("$it/${CpuInputBoostNodes.ENABLED}") != null
        } ?: return priors
        val enabledNode = "$parent/${CpuInputBoostNodes.ENABLED}"
        readSysfsNode(enabledNode)?.let { now ->
            if (SnapdragonKernelNodes.enableIfZero(now) != null) priors[enabledNode] = now
        }
        val durations = CpuInputBoostNodes.DURATION_NAMES
            .mapNotNull { name -> readSysfsNode("$parent/$name")?.let { name to it } }
            .toMap()
        if (CpuInputBoostNodes.boostWindowMs(durations) != null) {
            val freqNode = "$parent/${CpuInputBoostNodes.IB_FREQS}"
            val hardwareMaxKhz = hardwareMaxByCpu(readSnapdragonPolicyStates(), readPolicyCpuSets())
            readSysfsNode(freqNode)?.let { now ->
                if (SnapdragonKernelNodes.raiseBoostPairs(now, hardwareMaxKhz).isNotEmpty()) {
                    priors[freqNode] = now
                }
            }
        }
        val stuneParent = CpuInputBoostNodes.STUNE_BOOST_PARENTS.firstOrNull {
            readSysfsNode("$it/${CpuInputBoostNodes.DYNAMIC_STUNE_BOOST}") != null
        }
        if (stuneParent != null) {
            val node = "$stuneParent/${CpuInputBoostNodes.DYNAMIC_STUNE_BOOST}"
            readSysfsNode(node)?.let { now ->
                val topApp = readSysfsNode(CpuInputBoostNodes.TOP_APP_STUNE_BOOST)
                if (CpuInputBoostNodes.stuneBoostTarget(now, topApp) != null) priors[node] = now
            }
        }
        // `prefer_idle` is a pure enable-only latency hint. `sched_boost_enabled` is recorded only
        // when the device also declares a positive static top-app boost — enabling it over a zero
        // boost would be a switch with no magnitude behind it.
        readSysfsNode(CpuInputBoostNodes.TOP_APP_PREFER_IDLE)?.let { now ->
            if (SnapdragonKernelNodes.enableIfZero(now) != null) {
                priors[CpuInputBoostNodes.TOP_APP_PREFER_IDLE] = now
            }
        }
        val topAppBoost = readSysfsNode(CpuInputBoostNodes.TOP_APP_STUNE_BOOST)?.toLongOrNull()
        if (topAppBoost != null && topAppBoost > 0L) {
            readSysfsNode(CpuInputBoostNodes.TOP_APP_SCHED_BOOST_ENABLED)?.let { now ->
                if (SnapdragonKernelNodes.enableIfZero(now) != null) {
                    priors[CpuInputBoostNodes.TOP_APP_SCHED_BOOST_ENABLED] = now
                }
            }
        }
        return priors
    }

    /**
     * Governor priors: every policy that currently runs something other than `performance` while
     * declaring it in its own `scaling_available_governors`. A policy that does not list it
     * contributes nothing, so the apply can never write a governor the kernel did not declare.
     */
    private suspend fun captureCpuGovernorNodes(): Map<String, String> {
        val priors = linkedMapOf<String, String>()
        for (dir in readSnapdragonPolicyStates().keys) {
            val node = "$dir/${CpuGovernorMode.SCALING_GOVERNOR}"
            val current = readSysfsNode(node) ?: continue
            val available = readSysfsNode("$dir/${CpuGovernorMode.AVAILABLE_GOVERNORS}")
            if (CpuGovernorMode.performanceTarget(current, available) != null) priors[node] = current
        }
        return priors
    }

    /**
     * The Spectrum profile property and its prior value, or null when the interface is missing,
     * unsupported, already at gaming, or the property does not exist.
     *
     * The existence gate is the important one: `setprop` cannot delete a `persist.*` property, so
     * creating one would be a permanent device change no revert could undo. A kernel that
     * advertises support without shipping a profile property is left entirely alone.
     */
    private suspend fun captureSpectrumProfile(): Pair<String, String>? {
        val support = readProp(SpectrumProfile.SUPPORT_PROP)
        val vendorSupport = readProp(SpectrumProfile.VENDOR_SUPPORT_PROP)
        if (!SpectrumProfile.supported(support, vendorSupport)) return null
        val property = SpectrumProfile.profileProperty(vendorSupport.trim() == "1")
        val prior = readProp(property).trim()
        if (prior.isEmpty()) return null
        if (SpectrumProfile.requestedProfile(prior) == null) return null
        return property to prior
    }

    /**
     * `/data` read-ahead priors: the queue node of the block device the kernel's own mount table
     * names, recorded only when the queue declares a maximum above the current window. An
     * unresolvable mount, a non-storage source (tmpfs, loop, zram) or an unreadable queue records
     * nothing, which makes the apply a no-op rather than a guess.
     */
    private suspend fun captureStorageTuningNodes(): Map<String, String> {
        val priors = linkedMapOf<String, String>()
        val mounts = readSysfsNode("/proc/mounts") ?: return priors
        val disk = StorageTuning.dataBlockDeviceName(StorageTuning.dataMountSource(mounts))
            ?: return priors
        val queue = StorageTuning.queueDir(disk)
        val current = readSysfsNode("$queue/${StorageTuning.READ_AHEAD_KB}") ?: return priors
        val maxSectors = readSysfsNode("$queue/${StorageTuning.MAX_SECTORS_KB}") ?: return priors
        if (StorageTuning.readAheadTarget(current, maxSectors) != null) {
            priors["$queue/${StorageTuning.READ_AHEAD_KB}"] = current
        }
        return priors
    }

    /**
     * VM watermark priors: `min_free_kbytes` when 1 % of the device's own `MemTotal` is above it,
     * and `extra_free_kbytes` when the device's own base reserve is above it. A device already at
     * or above either target records nothing and is never lowered.
     */
    private suspend fun captureMemoryTuningNodes(): Map<String, String> {
        val priors = linkedMapOf<String, String>()
        val memTotal = MemoryTuning.memTotalKb(readSysfsNode(MemoryTuning.MEM_INFO))
        val minFree = readSysfsNode(MemoryTuning.MIN_FREE_KBYTES) ?: return priors
        if (MemoryTuning.minFreeTarget(minFree, memTotal) != null) {
            priors[MemoryTuning.MIN_FREE_KBYTES] = minFree
        }
        readSysfsNode(MemoryTuning.EXTRA_FREE_KBYTES)?.let { extra ->
            if (MemoryTuning.extraFreeTarget(extra, minFree) != null) {
                priors[MemoryTuning.EXTRA_FREE_KBYTES] = extra
            }
        }
        return priors
    }

    /**
     * Applies the Snapdragon widen-only channel and confirms every write by read-back. The channel
     * is [SnapdragonCpuWiden] (policy ceilings raised to each policy's own hardware max;
     * game-visible cpusets unioned toward the full set) plus [SnapdragonKernelNodes] (the schedutil
     * up rate limiter removed and the hispeed jump raised; `msm_performance`'s touch boost enabled
     * and its declared CPU ceilings raised; `core_ctl` floors and ceilings raised to each cluster's
     * real core count; `cpu_boost`'s input boost enabled and its per-CPU frequencies raised toward
     * each policy's max).
     *
     * @return null when [priors] is empty — nothing widenable, not applicable — false when root is
     *   unavailable or any attempted node refused, true only when every attempted node read back at
     *   its target. Null as well when the priors evaporated before anything was attempted (a thermal
     *   daemon moving ceilings between capture and apply): reporting "widened" with zero writes
     *   would manufacture a success, and "refused" would blame the device for a race — not
     *   applicable is the honest answer either way. Writes stay strictly inside the snapshot priors:
     *   a node the capture never recorded is never written, even if a fresh read would widen it, and
     *   every target is recomputed from the device's live state at write time. One family's refusal
     *   never blocks the writes of the others; it does make the whole row read as refused, because
     *   the row is one switch.
     */
    private suspend fun applySnapdragonCpuMode(priors: Map<String, String>): Boolean? {
        if (priors.isEmpty()) return null
        if (!shellRunner.isRootAvailable()) return false
        val policies = readSnapdragonPolicyStates()
        val cpuSets = readPolicyCpuSets()
        val hardwareMaxKhz = hardwareMaxByCpu(policies, cpuSets)
        val outcomes = listOfNotNull(
            applyPolicyCeilings(priors, policies),
            applyCpusets(priors),
            applySchedutilNodes(priors, policies),
            applySwitchNode(priors, "${SnapdragonKernelNodes.MSM_PERFORMANCE_PARAMETERS}/" +
                SnapdragonKernelNodes.TOUCHBOOST),
            applyMsmCpuCaps(priors, hardwareMaxKhz),
            applyCoreCtlNodes(priors, cpuSets),
            applySwitchNode(priors, "${SnapdragonKernelNodes.CPU_BOOST_PARAMETERS}/" +
                SnapdragonKernelNodes.INPUT_BOOST_ENABLED),
            applyCpuBoostFreqNode(priors, hardwareMaxKhz)
        )
        if (outcomes.isEmpty()) return null
        return outcomes.all { it }
    }

    /** Policy ceilings: every captured policy's `scaling_max_freq` widened to its own hardware max. */
    private suspend fun applyPolicyCeilings(
        priors: Map<String, String>,
        policies: Map<String, SnapdragonCpuWiden.PolicyState>
    ): Boolean? = applyNodePlan(
        SnapdragonCpuWiden.policyMaxWritePlan(
            policies.filterKeys { dir ->
                priors.containsKey("$dir/${SnapdragonCpuWiden.SCALING_MAX_FREQ}")
            }
        )
    )

    /** Game-visible cpusets, unioned toward the kernel's present set. */
    private suspend fun applyCpusets(priors: Map<String, String>): Boolean? {
        val fullSet = readSysfsNode(CPU_PRESENT_NODE)
            ?.let(SnapdragonCpuWiden::parseCpuSet).orEmpty()
        return applyNodePlan(SnapdragonCpuWiden.cpusetWritePlan(priors, fullSet))
    }

    /**
     * schedutil writes: the up rate limiter removed to 0, the hispeed jump raised to the policy's
     * own hardware max (only where the device still declares a 1..100 `hispeed_load`) and the iowait
     * boost turned on. Every target is recomputed from a fresh read, and a node the prior map does
     * not carry is skipped — so a value that moved between capture and apply is never overwritten
     * with a stale plan.
     */
    private suspend fun applySchedutilNodes(
        priors: Map<String, String>,
        policies: Map<String, SnapdragonCpuWiden.PolicyState>
    ): Boolean? {
        val plan = linkedMapOf<String, String>()
        for ((policyDir, state) in policies) {
            val dir = "$policyDir/${SnapdragonKernelNodes.SCHEDUTIL_DIR_NAME}"
            val upNode = "$dir/${SnapdragonKernelNodes.UP_RATE_LIMIT_US}"
            if (priors.containsKey(upNode)) {
                SnapdragonKernelNodes.noUpRateLimit(readSysfsNode(upNode))?.let { plan[upNode] = it }
            }
            val hispeedNode = "$dir/${SnapdragonKernelNodes.HISPEED_FREQ}"
            if (priors.containsKey(hispeedNode)) {
                val load = readSysfsNode("$dir/${SnapdragonKernelNodes.HISPEED_LOAD}")
                SnapdragonKernelNodes.hispeedTarget(readSysfsNode(hispeedNode), state.availableKhz, load)
                    ?.let { plan[hispeedNode] = it }
            }
            val iowaitNode = "$dir/${SnapdragonKernelNodes.IOWAIT_BOOST_ENABLE}"
            if (priors.containsKey(iowaitNode)) {
                SnapdragonKernelNodes.enableIfZero(readSysfsNode(iowaitNode))?.let { plan[iowaitNode] = it }
            }
        }
        return applyNodePlan(plan)
    }

    /**
     * One enable-if-zero node: written only when the snapshot recorded it and it still declares 0.
     * Null — no write — when the node was never captured, is already on, or does not parse as 0/1.
     */
    private suspend fun applySwitchNode(priors: Map<String, String>, node: String): Boolean? {
        if (!priors.containsKey(node)) return null
        val target = SnapdragonKernelNodes.enableIfZero(readSysfsNode(node)) ?: return null
        return applyNodePlan(mapOf(node to target))
    }

    /**
     * `msm_performance/cpu_max_freq`: every declared per-CPU ceiling below its own policy's hardware
     * max raised to that max (only the raised entries are written; the kernel merges them into the
     * list it holds) and confirmed by a semantic read-back. Null when the node was never captured or
     * holds no declared ceiling below the hardware max.
     */
    private suspend fun applyMsmCpuCaps(
        priors: Map<String, String>,
        hardwareMaxKhz: Map<Int, Long>
    ): Boolean? {
        val node = "${SnapdragonKernelNodes.MSM_PERFORMANCE_PARAMETERS}/" +
            SnapdragonKernelNodes.CPU_MAX_FREQ
        if (!priors.containsKey(node)) return null
        val targets = SnapdragonKernelNodes.raiseExistingCapPairs(readSysfsNode(node), hardwareMaxKhz)
        if (targets.isEmpty()) return null
        return writeSysfsPairsVerified(node, targets)
    }

    /**
     * `core_ctl` writes: every cluster's `min_cpus`/`max_cpus` raised toward its real core count
     * (`related_cpus`), one shared plan for the whole family. Null when no captured node still has
     * headroom.
     */
    private suspend fun applyCoreCtlNodes(
        priors: Map<String, String>,
        cpuSets: Map<String, Set<Int>>
    ): Boolean? {
        val plan = linkedMapOf<String, String>()
        for (dir in readCoreCtlDirs(cpuSets.values.flatten().toSet())) {
            val clusterSize = coreCtlCpu(dir)?.let { cpu -> cpuSets.values.firstOrNull { cpu in it }?.size }
                ?: continue
            for (name in listOf(SnapdragonKernelNodes.MIN_CPUS, SnapdragonKernelNodes.MAX_CPUS)) {
                val node = "$dir/$name"
                if (!priors.containsKey(node)) continue
                SnapdragonKernelNodes.clusterRaise(readSysfsNode(node), clusterSize)?.let { plan[node] = it }
            }
        }
        return applyNodePlan(plan)
    }

    /**
     * `cpu_boost/input_boost_freq`: every per-CPU boost frequency below its own policy's hardware
     * max raised to that max, gated on the device still declaring a boost window
     * (`input_boost_ms` > 0). Null when the node was never captured, the window is gone, or nothing
     * has headroom.
     */
    private suspend fun applyCpuBoostFreqNode(
        priors: Map<String, String>,
        hardwareMaxKhz: Map<Int, Long>
    ): Boolean? {
        val base = SnapdragonKernelNodes.CPU_BOOST_PARAMETERS
        val node = "$base/${SnapdragonKernelNodes.INPUT_BOOST_FREQ}"
        if (!priors.containsKey(node)) return null
        val window = readSysfsNode("$base/${SnapdragonKernelNodes.INPUT_BOOST_MS}")?.toLongOrNull()
        if (window == null || window <= 0L) return null
        val targets = SnapdragonKernelNodes.raiseBoostPairs(readSysfsNode(node), hardwareMaxKhz)
        if (targets.isEmpty()) return null
        return writeSysfsPairsVerified(node, targets)
    }

    /**
     * Applies the `cpu_input_boost` + `/dev/stune` family. Every write is recomputed from a live
     * read and limited to nodes the snapshot recorded: the master switch and the enable-only
     * top-app flags go to `1` (never off), the per-CPU `ib_freqs` body is raised only while the
     * device still declares a positive window, and the dynamic stune boost is raised toward the
     * device's own static top-app boost.
     *
     * @return null when nothing was attempted (interface gone, no headroom, or recovery found
     *   everything already at its target), false when root is unavailable or a write was refused,
     *   true only when every attempted write read back at its target.
     */
    private suspend fun applyCpuInputBoostNodes(priors: Map<String, String>): Boolean? {
        if (priors.isEmpty()) return null
        if (!shellRunner.isRootAvailable()) return false
        val plan = linkedMapOf<String, String>()
        var freqResult: Boolean? = null
        for (node in priors.keys) {
            when (node.substringAfterLast('/')) {
                CpuInputBoostNodes.ENABLED,
                "schedtune.prefer_idle" ->
                    CpuInputBoostNodes.desiredFlag(readSysfsNode(node))?.let { plan[node] = it }
                "schedtune.sched_boost_enabled" -> {
                    // Same magnitude gate as capture: never enable a static boost over a zero.
                    val topApp = readSysfsNode(CpuInputBoostNodes.TOP_APP_STUNE_BOOST)?.toLongOrNull()
                    if (topApp != null && topApp > 0L) {
                        CpuInputBoostNodes.desiredFlag(readSysfsNode(node))?.let { plan[node] = it }
                    }
                }
                CpuInputBoostNodes.IB_FREQS -> {
                    val base = node.substringBeforeLast('/')
                    val durations = CpuInputBoostNodes.DURATION_NAMES
                        .mapNotNull { name -> readSysfsNode("$base/$name")?.let { name to it } }
                        .toMap()
                    if (CpuInputBoostNodes.boostWindowMs(durations) != null) {
                        val hardwareMaxKhz = hardwareMaxByCpu(
                            readSnapdragonPolicyStates(),
                            readPolicyCpuSets()
                        )
                        val targets = SnapdragonKernelNodes.raiseBoostPairs(
                            readSysfsNode(node),
                            hardwareMaxKhz
                        )
                        if (targets.isNotEmpty()) freqResult = writeSysfsPairsVerified(node, targets)
                    }
                }
                CpuInputBoostNodes.DYNAMIC_STUNE_BOOST ->
                    CpuInputBoostNodes.desiredStuneBoost(
                        readSysfsNode(node),
                        readSysfsNode(CpuInputBoostNodes.TOP_APP_STUNE_BOOST)
                    )?.let { plan[node] = it }
            }
        }
        val outcomes = listOfNotNull(applyNodePlan(plan), freqResult)
        if (outcomes.isEmpty()) return null
        return outcomes.all { it }
    }

    /**
     * Switches every recorded policy to `performance` when it still declares that governor. The
     * target is re-read live, so a policy whose available table changed since capture is skipped
     * rather than written a name it no longer supports.
     *
     * @return null when no recorded policy needed/permits the switch, false when root is
     *   unavailable or a write was refused, true when every attempt read back `performance`.
     */
    private suspend fun applyCpuGovernorMode(priors: Map<String, String>): Boolean? {
        if (priors.isEmpty()) return null
        if (!shellRunner.isRootAvailable()) return false
        val plan = linkedMapOf<String, String>()
        for (node in priors.keys) {
            val current = readSysfsNode(node) ?: continue
            val available = readSysfsNode(
                "${node.substringBeforeLast('/')}/${CpuGovernorMode.AVAILABLE_GOVERNORS}"
            )
            CpuGovernorMode.desiredGovernor(current, available)?.let { plan[node] = it }
        }
        return applyNodePlan(plan)
    }

    /**
     * Applies the Spectrum gaming profile, accepting the kernel's own clamp to performance (2) as
     * a confirmed result. The prior value is in the snapshot; this writes only the recorded
     * property, never a guessed one.
     */
    private suspend fun applySpectrumProfile(property: String?): SpectrumOutcome {
        if (property == null) return SpectrumOutcome()
        if (!shellRunner.isRootAvailable()) return SpectrumOutcome(refused = true)
        val current = readProp(property).trim()
        val requested = SpectrumProfile.requestedProfile(current)
            ?: return if (current == SpectrumProfile.GAMING_PROFILE.toString()) {
                SpectrumOutcome(profile = SpectrumProfile.GAMING_PROFILE)
            } else {
                SpectrumOutcome()
            }
        shellRunner.execSafeResult("setprop", property, requested.toString())
        val accepted = SpectrumProfile.acceptedProfile(requested, readProp(property).trim())
            ?: return SpectrumOutcome(refused = true)
        return SpectrumOutcome(profile = accepted)
    }

    /**
     * Raises the `/data` device's read-ahead to its queue's declared maximum request size, then
     * reports the value the device read back. Only the queue the snapshot recorded is touched;
     * the device is re-resolved so a changed mount table cannot redirect the write to another
     * disk. The I/O scheduler is deliberately not changed (see [StorageTuning]).
     */
    private suspend fun applyStorageTuning(priors: Map<String, String>): NodeRaiseOutcome {
        if (priors.isEmpty()) return NodeRaiseOutcome()
        if (!shellRunner.isRootAvailable()) return NodeRaiseOutcome(applied = false)
        val node = priors.keys.firstOrNull { it.endsWith("/${StorageTuning.READ_AHEAD_KB}") }
            ?: return NodeRaiseOutcome()
        val queue = node.substringBeforeLast('/')
        val target = StorageTuning.desiredReadAhead(
            readSysfsNode(node),
            readSysfsNode("$queue/${StorageTuning.MAX_SECTORS_KB}")
        ) ?: return NodeRaiseOutcome()
        if (!writeSysfsVerified(node, target)) return NodeRaiseOutcome(applied = false)
        return NodeRaiseOutcome(
            applied = true,
            valueKb = readSysfsNode(node)?.toIntOrNull()
        )
    }

    /**
     * Raises the VM watermarks from the device's own facts: `min_free_kbytes` toward 1 % of
     * `MemTotal`, `extra_free_kbytes` toward the device's own base reserve. Both targets are
     * computed from live reads before either write, so the extra reserve can never chase our own
     * raised minimum. Values already above their target are left alone, never lowered.
     */
    private suspend fun applyMemoryTuning(priors: Map<String, String>): NodeRaiseOutcome {
        if (priors.isEmpty()) return NodeRaiseOutcome()
        if (!shellRunner.isRootAvailable()) return NodeRaiseOutcome(applied = false)
        val memTotal = MemoryTuning.memTotalKb(readSysfsNode(MemoryTuning.MEM_INFO))
            ?: return NodeRaiseOutcome()
        val plan = linkedMapOf<String, String>()
        if (priors.containsKey(MemoryTuning.MIN_FREE_KBYTES)) {
            MemoryTuning.desiredMinFree(
                readSysfsNode(MemoryTuning.MIN_FREE_KBYTES),
                memTotal
            )?.let { plan[MemoryTuning.MIN_FREE_KBYTES] = it }
        }
        if (priors.containsKey(MemoryTuning.EXTRA_FREE_KBYTES)) {
            MemoryTuning.desiredExtraFree(
                readSysfsNode(MemoryTuning.EXTRA_FREE_KBYTES),
                readSysfsNode(MemoryTuning.MIN_FREE_KBYTES)
            )?.let { plan[MemoryTuning.EXTRA_FREE_KBYTES] = it }
        }
        if (plan.isEmpty()) return NodeRaiseOutcome()
        if (applyNodePlan(plan) != true) return NodeRaiseOutcome(applied = false)
        return NodeRaiseOutcome(
            applied = true,
            valueKb = readSysfsNode(MemoryTuning.MIN_FREE_KBYTES)?.toIntOrNull()
        )
    }

    /**
     * Writes one plain node→value plan and counts the read-back. Null when the plan is empty —
     * "nothing to write", which callers merge as not applicable rather than as success or refusal.
     */
    private suspend fun applyNodePlan(plan: Map<String, String>): Boolean? {
        if (plan.isEmpty()) return null
        var attempted = 0
        var confirmed = 0
        for ((node, target) in plan) {
            attempted++
            if (writeSysfsVerified(node, target)) confirmed++
        }
        return confirmed == attempted
    }

    /**
     * Puts a recorded kernel-node map back (policy ceilings, cpusets, schedutil rate limits and
     * jumps, `msm_performance` switches and ceilings, `core_ctl` floors/ceilings, `cpu_boost`
     * switches and frequencies, governors, boost frequencies, read-ahead and VM watermarks), root-
     * gated like every apply.
     *
     * Each write is read back, because a sysfs write can be refused by the kernel or by SELinux and
     * still leave the node holding the gaming value — a policy's `schedutil` tunables, for one,
     * only accept writes while schedutil is the active governor. A prior that fails the safe-value
     * gate is not written at all.
     *
     * @return the node paths that did not read back as their recorded prior, for the deactivation
     *   report. Empty means everything landed (or there was nothing to restore).
     */
    private suspend fun restoreKernelNodeMap(priors: Map<String, String>): List<String> {
        if (priors.isEmpty() || !shellRunner.isRootAvailable()) return emptyList()
        val refused = mutableListOf<String>()
        for ((node, prior) in priors) {
            // Comma- and colon-aware gate (not the shared one: cpuset priors like `0-5,7` need
            // commas and per-CPU boost priors like `0:0 4:1804800` need colons).
            if (!SnapdragonCpuWiden.isSafeNodeValue(prior)) {
                refused += node
                continue
            }
            shellRunner.execSafeResult("sh", "-c", "echo $prior > $node")
            if (readSysfsNode(node)?.trim() != prior.trim()) refused += node
        }
        return refused
    }

    /**
     * Puts the Spectrum profile property back to the recorded prior. Only a property the snapshot
     * actually recorded is touched — capture refuses interfaces whose property did not exist,
     * because a `persist.*` property cannot be deleted again.
     */
    private suspend fun restoreSpectrumProfile(property: String?, prior: String?) {
        if (property == null || prior.isNullOrBlank()) return
        if (!shellRunner.isRootAvailable()) return
        shellRunner.execSafeResult("setprop", property, prior)
    }

    /** Re-reads the PPM status dump and checks it describes exactly [expected] states. */
    private suspend fun verifyPpmStates(expected: List<Pair<Int, Int>>): Boolean {
        val dump = readSysfsNode(MediaTekGameMode.PPM_POLICY_STATUS_NODE) ?: return false
        val actual = MediaTekGameMode.ppmRestoreWrites(dump)
            .mapNotNull { line ->
                val parts = line.split(" ")
                if (parts.size != 2) null
                else {
                    val idx = parts[0].toIntOrNull()
                    val on = parts[1].toIntOrNull()
                    if (idx == null || on == null) null else idx to on
                }
            }
            .toMap()
        return expected.all { (idx, on) -> actual[idx] == on }
    }

    /** One sysfs/proc node read, or null when unreadable or blank. */
    private suspend fun readSysfsNode(node: String): String? {
        val result = shellRunner.execSafeResult("cat", node)
        return if (result.isSuccess) result.stdout.trim().takeIf { it.isNotEmpty() } else null
    }

    /**
     * The kernel's cpufreq policies as widening states: current ceiling plus the
     * frequency table, both read through the privileged shell. A policy whose
     * nodes do not read is skipped — it can be neither verified nor restored,
     * so it must not be written either.
     */
    private suspend fun readSnapdragonPolicyStates(): Map<String, SnapdragonCpuWiden.PolicyState> {
        val states = linkedMapOf<String, SnapdragonCpuWiden.PolicyState>()
        val listing = shellRunner.execSafeResult("ls", CPUFREQ_DIR)
        if (!listing.isSuccess) return states
        val dirs = listing.stdout.split(WHITESPACE)
            .filter { it.startsWith("policy") }
            .map { "$CPUFREQ_DIR/$it" }
        for (dir in dirs) {
            val current = readSysfsNode("$dir/${SnapdragonCpuWiden.SCALING_MAX_FREQ}")
                ?.toLongOrNull() ?: continue
            val available = readSysfsNode("$dir/${SnapdragonCpuWiden.SCALING_AVAILABLE_FREQUENCIES}")
                ?.let(KernelInfo::parseFreqTable).orEmpty()
            states[dir] = SnapdragonCpuWiden.PolicyState(current, available)
        }
        return states
    }

    /**
     * The kernel's cpufreq policies as `<policyDir> → related CPU ids`, read through the privileged
     * shell. A policy whose `related_cpus` does not read or parse contributes no CPUs: without the
     * kernel's own cluster membership it can gate neither a `core_ctl` raise nor a per-CPU
     * frequency target, and guessing one could invert the widen into a narrowing.
     */
    private suspend fun readPolicyCpuSets(): Map<String, Set<Int>> {
        val sets = linkedMapOf<String, Set<Int>>()
        val listing = shellRunner.execSafeResult("ls", CPUFREQ_DIR)
        if (!listing.isSuccess) return sets
        for (entry in listing.stdout.split(WHITESPACE).filter { it.startsWith("policy") }) {
            val dir = "$CPUFREQ_DIR/$entry"
            val cpus = readSysfsNode("$dir/${SnapdragonCpuWiden.RELATED_CPUS}")
                ?.let(SnapdragonCpuWiden::parseCpuSet).orEmpty()
            if (cpus.isNotEmpty()) sets[dir] = cpus
        }
        return sets
    }

    /**
     * Each CPU's own policy hardware max in kHz — `cpu → max(scaling_available_frequencies)` of the
     * policy the kernel reports it in — the raise target for the per-CPU `cpu:freq` nodes. A policy
     * without a parseable table contributes nothing, so its CPUs are never written.
     */
    private fun hardwareMaxByCpu(
        policies: Map<String, SnapdragonCpuWiden.PolicyState>,
        cpuSets: Map<String, Set<Int>>
    ): Map<Int, Long> {
        val maxByCpu = linkedMapOf<Int, Long>()
        for ((dir, cpus) in cpuSets) {
            val hardwareMax = policies[dir]?.availableKhz?.maxOrNull() ?: continue
            if (hardwareMax <= 0L) continue
            for (cpu in cpus) maxByCpu[cpu] = hardwareMax
        }
        return maxByCpu
    }

    /**
     * The `core_ctl` directories the device has, asked for by candidate path in one `ls -d` call.
     * A kernel without core_ctl prints nothing (and exits non-zero), which is exactly the empty
     * answer; a directory that answers is kept only when the path really names a `core_ctl` node.
     */
    private suspend fun readCoreCtlDirs(cpus: Set<Int>): List<String> {
        if (cpus.isEmpty()) return emptyList()
        val candidates = cpus.sorted().map {
            "/sys/devices/system/cpu/cpu$it/${SnapdragonKernelNodes.CORE_CTL_DIR_NAME}"
        }
        val listing = shellRunner.execSafeResult("ls", "-d", *candidates.toTypedArray())
        return listing.stdout.split(WHITESPACE)
            .filter { it.endsWith("/${SnapdragonKernelNodes.CORE_CTL_DIR_NAME}") }
            .sorted()
    }

    /** The CPU a `core_ctl` directory sits under (`…/cpu4/core_ctl` → 4), or null when unnamed. */
    private fun coreCtlCpu(dir: String): Int? =
        dir.substringBeforeLast("/${SnapdragonKernelNodes.CORE_CTL_DIR_NAME}")
            .substringAfterLast("/")
            .removePrefix("cpu")
            .toIntOrNull()

    /** Writes one sysfs value and confirms it by read-back. Root-only (see callers). */
    private suspend fun writeSysfsVerified(node: String, value: String): Boolean {
        if (!isSafeSysfsValue(value)) return false
        shellRunner.execSafeResult("sh", "-c", "echo $value > $node")
        return readSysfsNode(node) == value
    }

    /**
     * Writes a per-CPU `cpu:freq` body — only the entries being raised; the kernel merges them into
     * the list it holds — and confirms it by a *semantic* read-back: the node must parse back with
     * every planned CPU at its target. The body is digits, colons and spaces only, so it needs no
     * quoting. Root-only (see callers). False for an empty plan: a "confirmed" with nothing written
     * would be a manufactured success.
     */
    private suspend fun writeSysfsPairsVerified(node: String, targets: Map<Int, Long>): Boolean {
        if (targets.isEmpty()) return false
        val body = SnapdragonKernelNodes.formatFreqPairs(targets)
        shellRunner.execSafeResult("sh", "-c", "echo $body > $node")
        val after = readSysfsNode(node)?.let(SnapdragonKernelNodes::parseFreqPairs).orEmpty()
        return targets.all { (cpu, target) -> after[cpu] == target }
    }

    /**
     * Whether [value] is safe to embed in a `sh -c 'echo … > node'` script. Node values are
     * numbers and short tokens in practice; anything with shell metacharacters is refused
     * rather than quoted-and-hoped.
     */
    private fun isSafeSysfsValue(value: String): Boolean =
        value.isNotBlank() && value.all { it.isLetterOrDigit() || it in "._- " }

    /** @return the property's value, or "" when it is not set or could not be read. */
    private suspend fun readProp(key: String): String {
        val result = shellRunner.execSafeResult("getprop", key)
        return if (result.isSuccess) result.stdout.trim() else ""
    }

    /**
     * `setprop` prints nothing on success and its refusal goes to stderr, so — like
     * `settings put` — the read-back is the only honest signal. Root only: a shell-uid Shizuku
     * session cannot write vendor properties, and that surfaces here as a refusal rather than
     * as silence.
     */
    private suspend fun setPropVerified(key: String, value: String): Boolean {
        shellRunner.execSafeResult("setprop", key, value)
        return readProp(key) == value
    }

    /** A system property as a [SettingValue]: blank is `existed = false`, a failed read is null. */
    private suspend fun propSettingValue(key: String): SettingValue? {
        val result = shellRunner.execSafeResult("getprop", key)
        if (!result.isSuccess) return null
        val value = result.stdout.trim()
        return if (value.isEmpty()) SettingValue("", existed = false) else SettingValue(value, existed = true)
    }

    /** Puts a property back, only when the snapshot actually recorded it as existing. */
    private suspend fun restoreProp(key: String, sv: SettingValue?) {
        if (sv == null || !sv.existed || sv.value.isBlank()) return
        shellRunner.execSafeResult("setprop", key, sv.value)
    }

    /**
     * Reconciles a persisted session with the device after process death, a crash or a reboot.
     *
     * The old path trusted `is_active`, re-applied every lever and called the result Active. That
     * was a claim about the past, not evidence about the present: a reboot resets the settings
     * while package suspensions survive in the package manager, and a crash before the flag was
     * written produced the opposite state (apps frozen, UI idle). The decision table lives in
     * [GamingModeSession]; this method supplies the device facts and publishes the outcome.
     *
     * Nothing is re-applied here. An interrupted session is reported as interrupted, its snapshot
     * stays available for the power button to revert, and [restoreSuspendedApps] handles the
     * suspension record on its own.
     */
    private suspend fun reconcilePersistedSession() {
        try {
            val recorded = prefs.getStringSet("affected_pkgs", emptySet()).orEmpty()
            val appOps = recordedAppOpsPackages()
            val suspendedNow = querySuspendedPackages()
            val verdict = GamingModeSession.reconcile(
                sessionOpen = prefs.getBoolean("is_active", false),
                bootCountAtStart = prefs.getInt("session_boot_count", 0),
                currentBootCount = currentBootCount(),
                recorded = recorded,
                suspendedNow = suspendedNow,
                appOpsRecorded = appOps.toSet()
            )

            // Drop record entries the package manager says are awake. Keeping them would leave a
            // permanent recovery prompt for apps that no longer need one.
            if (suspendedNow != null && verdict.pendingPackages.size != recorded.size) {
                if (verdict.pendingPackages.isEmpty()) {
                    prefs.edit { remove("affected_pkgs") }
                } else {
                    prefs.edit { putStringSet("affected_pkgs", verdict.pendingPackages.toSet()) }
                }
            }

            _suspendedPackages.value = verdict.pendingPackages.toSet()
            _recovery.value = GamingModeRecovery(
                isChecking = false,
                pendingPackages = verdict.pendingPackages,
                pendingAppOps = verdict.pendingAppOps,
                verified = verdict.verified
            )

            if (!verdict.keepSession) {
                prefs.edit {
                    putBoolean("is_active", false)
                    remove("affected_pkgs")
                    remove("session_boot_count")
                }
                _isFixedPerformanceMode.value = false
                _report.value = GamingModeReport()
                _sessionOpen.value = false
                _state.value = GamingModeState.Idle
                return
            }

            if (verdict.rebootedSinceStart) {
                // The reboot already reset the settings levers; only the app record can survive.
                prefs.edit { putBoolean("fixed_perf_manual", false) }
                _isFixedPerformanceMode.value = false
            }
            _sessionOpen.value = true
            _state.value = GamingModeState.Interrupted(verdict.pendingPackages.size)
        } catch (_: Exception) {
            // Reconciliation itself failed. Do not claim Active and do not erase the record: show
            // it as unverified evidence and let the user restore it deliberately.
            val recorded = prefs.getStringSet("affected_pkgs", emptySet()).orEmpty().sorted()
            _suspendedPackages.value = recorded.toSet()
            _recovery.value = GamingModeRecovery(
                isChecking = false,
                pendingPackages = recorded,
                pendingAppOps = recordedAppOpsPackages(),
                verified = false
            )
            _sessionOpen.value = true
            _state.value = GamingModeState.Interrupted(recorded.size)
        }
    }

    private suspend fun reapplyFixedPerformanceMode() {
        val applied = runCatching {
            shellRunner.execSafeResult(*PrivilegeCommands.fixedPerformanceArgs(true)).isSuccess
        }.getOrDefault(false)
        _isFixedPerformanceMode.value = applied
    }

    /**
     * The verified outcome of a single-value node raise: null [applied] means "not applicable"
     * (nothing to do, or an unreadable device fact), false means the device refused, true means
     * the read-back confirmed it. [valueKb] is the number the device reported after the write.
     */
    private data class NodeRaiseOutcome(
        val applied: Boolean? = null,
        val valueKb: Int? = null
    )

    /** The verified outcome of a Spectrum write: refused, or the profile the kernel confirmed. */
    private data class SpectrumOutcome(
        val refused: Boolean = false,
        val profile: Int? = null
    )

    private companion object {
        val WHITESPACE = Regex("\\s+")

        /** A bounded wait for a root compile request; a hung compiler must not stall the UI. */

        /** Packages per `pm unsuspend` fork in the deactivation sweep; mirrors UID_BATCH_SIZE. */
        private const val UNSUSPEND_BATCH_SIZE = 25

        /** Qualcomm's vendor GPU mode knob, and its texture-quality companion. */
        const val VENDOR_GPU_MODE = "vendor.gpu.mode"
        const val VENDOR_GFX_LOW_QUALITY = "vendor.gfx.low_quality"

        /**
         * Qualcomm's game FPS hint, set by Gaming Mode to the panel's measured peak. The
         * `persist.vendor.qti.game.fps` twin from the reference's `post-fs-data.sh` is
         * deliberately absent — see [GamingModeReport.qtiGameFps].
         */
        const val DEBUG_VENDOR_QTI_GAME_FPS = "debug.vendor.qti.game.fps"

        /** cpufreq policy root enumerating one dir per CPU cluster (`policy0`, …). */
        const val CPUFREQ_DIR = "/sys/devices/system/cpu/cpufreq"

        /** Kernel-reported present CPUs (`0-7`), the widening target for game cpusets. */
        const val CPU_PRESENT_NODE = "/sys/devices/system/cpu/present"
    }
}
