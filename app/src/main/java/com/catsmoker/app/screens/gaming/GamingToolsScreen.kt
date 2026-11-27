package com.catsmoker.app.screens.gaming

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.R
import com.catsmoker.app.integrations.ads.GamingToolsBanner
import com.catsmoker.app.application.distribution.VariantCapabilities
import com.catsmoker.app.screens.gaming.GamingToolsViewModel
import com.catsmoker.app.screens.gaming.apps.AutoForceStopUsageNotice
import com.catsmoker.app.screens.gaming.cleaning.CleaningContent
import com.catsmoker.app.screens.gaming.cleaning.CleaningFeature
import com.catsmoker.app.screens.gaming.compilation.AppBoosterContent
import com.catsmoker.app.screens.gaming.compilation.BoosterRun
import com.catsmoker.app.screens.gaming.compilation.BoosterState
import com.catsmoker.app.screens.gaming.components.AppPickerDialog
import com.catsmoker.app.screens.gaming.components.ExpandableToolCard
import com.catsmoker.app.screens.gaming.components.FeatureToggleCard
import com.catsmoker.app.screens.gaming.components.InfoTopics
import com.catsmoker.app.screens.gaming.components.backgroundDataInfo
import com.catsmoker.app.screens.gaming.components.backgroundNetworkIcon
import com.catsmoker.app.screens.gaming.crosshair.CrosshairPicker
import com.catsmoker.app.screens.gaming.display.DisplayCard
import com.catsmoker.app.screens.gaming.graphics.AngleDriverOptions
import com.catsmoker.app.screens.gaming.graphics.AnimationScaleKind
import com.catsmoker.app.screens.gaming.graphics.DeveloperOptionsContent
import com.catsmoker.app.screens.gaming.graphics.GameDeveloperOptions
import com.catsmoker.app.screens.gaming.graphics.ResolutionChangerContent
import com.catsmoker.app.screens.gaming.mode.BoostContent
import com.catsmoker.app.screens.gaming.mode.CpuPolicyControls
import com.catsmoker.app.screens.gaming.mode.FixedPerformanceModeCard
import com.catsmoker.app.screens.gaming.mode.GamingModeCard
import com.catsmoker.app.screens.gaming.mode.GamingModeRecovery
import com.catsmoker.app.screens.gaming.mode.GamingModeReport
import com.catsmoker.app.screens.gaming.mode.GamingModeState
import com.catsmoker.app.screens.gaming.mode.SuspensionRescueDialog
import com.catsmoker.app.screens.gaming.network.BackgroundDataContent
import com.catsmoker.app.screens.gaming.network.DnsContent
import com.catsmoker.app.screens.gaming.network.DnsFeature
import com.catsmoker.app.screens.gaming.performanceoverlay.OverlayPositionContent
import com.catsmoker.app.ui.components.ScreenScaffold
import com.catsmoker.app.ui.components.SectionLabel
import com.catsmoker.app.ui.components.SectionRail
import com.catsmoker.app.ui.components.SectionTab
import com.catsmoker.app.ui.components.SectionTabRow
import com.catsmoker.app.ui.theme.CatsmokerSpacing
import com.catsmoker.app.ui.theme.CatsmokerTheme
import java.io.File

/**
 * Columns in "Your Games".
 *
 * Four is the launcher-grid density: on a 320 dp screen each cell still has a 56 dp icon and its
 * label, and a normal phone shows seven games in two rows instead of seven stacked rows.
 */
private const val GAME_GRID_COLUMNS = 4
/** How many games a collapsed "Your Games" grid shows — two full rows. */
private const val GAME_LIBRARY_PREVIEW = GAME_GRID_COLUMNS * 2
/** Columns in the wide library rail: two, because the rail is only 278 dp wide. */
private const val GAME_RAIL_COLUMNS = 2
/** Below this many games, search would be clutter rather than help. */
private const val GAME_SEARCH_THRESHOLD = 8

/**
 * The workspace's six peer views.
 *
 * Each carries a one-word tab label rather than its full heading. The long headings
 * ("GRAPHICS & DISPLAY", "NETWORK & QUIET") are section titles inside the content pane; as tab
 * captions they forced a 2274 dp chip strip into a 954 dp row, so the switcher clipped its own
 * navigation. `name` is the saved-state key and stays stable across label changes.
 */
private enum class GamingSection(@StringRes val tabLabel: Int, val icon: ImageVector) {
    PROFILE(R.string.gt_tab_profile, Icons.Default.Tune),
    GRAPHICS(R.string.gt_tab_display, Icons.Default.Monitor),
    PERFORMANCE(R.string.gt_tab_speed, Icons.Default.Speed),
    OVERLAYS(R.string.gt_tab_overlays, Icons.Default.Layers),
    NETWORK(R.string.gt_tab_network, Icons.Default.Wifi),
    MAINTENANCE(R.string.gt_tab_cleanup, Icons.Default.CleaningServices)
}
@Composable
fun GamingToolsRoute(
    onBack: () -> Unit,
    onOpenCrosshairLibrary: () -> Unit
) {
    val viewModel: GamingToolsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val suspensionRescue by viewModel.suspensionRescue.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val noDevOptions = stringResource(R.string.gt_dialog_no_dev_options)
    // The Glyph strip mirrors the loading bar on this screen. Once the screen is no longer
    // visible there is nothing left to mirror, so the LEDs are dropped rather than left running
    // behind the game the user just switched to. A service connection must never outlive the
    // surface it belongs to.
    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onHostStopped()
        }
        if (owner != null) owner.lifecycle.addObserver(observer)
        onDispose {
            if (owner != null) owner.lifecycle.removeObserver(observer)
            viewModel.onHostStopped()
        }
    }
    val gamingState by viewModel.gamingState.collectAsStateWithLifecycle()
    val gamingReport by viewModel.gamingReport.collectAsStateWithLifecycle()
    val gamingRecovery by viewModel.gamingRecovery.collectAsStateWithLifecycle()
    val isGamingSessionOpen by viewModel.isGamingSessionOpen.collectAsStateWithLifecycle()
    val suspendedPackages by viewModel.suspendedPackages.collectAsStateWithLifecycle()
    val isFixedPerformanceMode by viewModel.isFixedPerformanceMode.collectAsStateWithLifecycle()
    val boosterLog by viewModel.boosterLog.collectAsStateWithLifecycle()
    val boosterState by viewModel.boosterState.collectAsStateWithLifecycle()
    val boosterHistory by viewModel.boosterHistory.collectAsStateWithLifecycle()
    val animationScales by viewModel.animationScales.collectAsStateWithLifecycle()
    val alwaysFinishActivities by viewModel.alwaysFinishActivities.collectAsStateWithLifecycle()
    val backgroundProcessLimit by viewModel.backgroundProcessLimit.collectAsStateWithLifecycle()
    val gameDevOptions by viewModel.gameDevOptions.collectAsStateWithLifecycle()
    val interventionDownscale by viewModel.interventionDownscale.collectAsStateWithLifecycle()
    val pointerSpeedChoice by viewModel.pointerSpeedChoice.collectAsStateWithLifecycle()
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.syncState()
    }
    // Coming back from the all-files-access screen, re-run the scan so the user sees straight away
    // whether the grant actually took effect instead of having to guess.
    val storageAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.scanForJunk()
    }
    // Developer options owns the same refresh-rate overlay switch this app cannot reach without a
    // privileged channel. Coming back, the whole state is re-read rather than assumed: the user may
    // have turned it on, turned it off, or not found it at all, and only a fresh read knows which.
    val developerOptionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.syncState()
    }
    LaunchedEffect(Unit) {
        viewModel.toasts.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    GamingToolsScreen(
        uiState = uiState,
        gamingState = gamingState,
        gamingReport = gamingReport,
        gamingRecovery = gamingRecovery,
        isGamingSessionOpen = isGamingSessionOpen,
        suspendedPackages = suspendedPackages,
        isFixedPerformanceMode = isFixedPerformanceMode,
        boosterLog = boosterLog,
        boosterState = boosterState,
        boosterHistory = boosterHistory,
        animationScales = animationScales,
        alwaysFinishActivities = alwaysFinishActivities,
        backgroundProcessLimit = backgroundProcessLimit,
        gameDevOptions = gameDevOptions,
        interventionDownscale = interventionDownscale,
        pointerSpeedChoice = pointerSpeedChoice,
        onToggleOverlay = { enable ->
            if (enable && !viewModel.canDrawOverlays()) {
                overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
            } else {
                viewModel.toggleOverlay(enable)
            }
        },
        onSetOverlayMoveMode = viewModel::setOverlayMoveMode,
        onResetOverlayPosition = viewModel::resetOverlayPosition,
        onToggleCrosshair = { enable ->
            if (enable && !viewModel.canDrawOverlays()) {
                overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
            } else {
                viewModel.toggleCrosshair(enable)
            }
        },
        onOpenCrosshairLibrary = onOpenCrosshairLibrary,
        onSetCrosshairMoveMode = viewModel::setCrosshairMoveMode,
        onRecentreCrosshair = viewModel::recentreCrosshair,
        onSetBackgroundDataRestriction = viewModel::setBackgroundDataRestriction,
        onToggleDnd = { enable ->
            if (!viewModel.toggleDnd(enable)) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        },
        onPerformMaintenance = viewModel::onPerformMaintenance,
        onScanJunk = viewModel::scanForJunk,
        onSetCleanSchedule = viewModel::setCleanSchedule,
        onSetCleanInterval = viewModel::setCleanInterval,
        onAddCleanerKeepEntry = viewModel::addCleanerKeepEntry,
        onRemoveCleanerKeepEntry = viewModel::removeCleanerKeepEntry,
        onAddCleanerCleanPattern = viewModel::addCleanerCleanPattern,
        onRemoveCleanerCleanPattern = viewModel::removeCleanerCleanPattern,
        onGrantStorageAccess = {
            if (VariantCapabilities.HAS_ALL_FILES_ACCESS) {
                val intent = viewModel.allFilesAccessIntent()
                if (intent == null) {
                    // Below API 30 the cleaner runs on the runtime storage permission, which lives on
                    // the app's own settings page.
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                    )
                } else {
                    try {
                        storageAccessLauncher.launch(intent)
                    } catch (_: Exception) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
        },
        onActivateGamingMode = viewModel::activateGamingMode,
        onDeactivateGamingMode = viewModel::deactivateGamingMode,
        onRestoreGamingApps = viewModel::restoreSuspendedApps,
        onOpenSuspensionRescue = viewModel::openSuspensionRescue,
        onBoostRam = viewModel::boostRam,
        onTrimStorage = viewModel::trimStorage,
        onRunBooster = viewModel::runBooster,
        onStopBooster = viewModel::stopBooster,
        onSetDexoptSchedule = viewModel::setDexoptSchedule,
        onSetDexoptInterval = viewModel::setDexoptInterval,
        onToggleFixedPerformance = viewModel::toggleFixedPerformance,
        onSetInterventionDownscale = viewModel::setInterventionDownscale,
        onSetPointerSpeedChoice = viewModel::setPointerSpeedChoice,
        onBoostChange = viewModel::onBoostChange,
        onSetAnimationScale = viewModel::setAnimationScale,
        onToggleAlwaysFinish = viewModel::toggleAlwaysFinish,
        onToggleBackgroundLimit = viewModel::toggleBackgroundLimit,
        onSetForcePeakRefreshRate = viewModel::setForcePeakRefreshRate,
        onSetGameDefaultFrameRateDisabled = viewModel::setGameDefaultFrameRateDisabled,
        onSelectAnglePackage = viewModel::selectAnglePackage,
        onSetAngleDriver = viewModel::setAngleDriver,
        onOpenDeveloperOptions = {
            // ACTION_APPLICATION_DEVELOPMENT_SETTINGS is the Developer options screen itself. On a
            // device where it has never been unlocked the Activity does not exist, so the fallback is
            // the top-level Settings app — from which the user can reach it.
            try {
                developerOptionsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (_: Exception) {
                try {
                    developerOptionsLauncher.launch(Intent(Settings.ACTION_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(context, noDevOptions, Toast.LENGTH_LONG).show()
                }
            }
        },
        onRefreshDns = viewModel::refreshDnsStatus,
        onApplyDnsProvider = viewModel::applyDnsProvider,
        onSetDnsAutomatic = viewModel::setDnsAutomatic,
        onDisableDns = viewModel::disableDns,
        onMeasureDnsPing = viewModel::measureDnsPing,
        onLaunchGame = viewModel::launchGame,
        onSelectGame = viewModel::toggleGameSelection,
        onGlobalSelection = { viewModel.selectGame(null) },
        onOptimizeGame = viewModel::optimizeGame,
        onSelectOptimizeScope = viewModel::selectOptimizeScope,
        onRemoveGame = viewModel::removeGameFromLibrary,
        onAddGameClicked = viewModel::onAddGameClicked,
        onToggleAutoForceStop = viewModel::toggleAutoForceStop,
        onShowSuspendPicker = viewModel::showSuspendPicker,
        onRemoveExtraSuspendPackage = viewModel::removeExtraSuspendPackage,
        onShowSuspendExemptPicker = viewModel::showSuspendExemptPicker,
        onRemoveSuspendExemptPackage = viewModel::removeSuspendExemptPackage,
        onToggleGameSession = viewModel::toggleGameSession,
        onToggleGamingModeAdvanced = viewModel::toggleGamingModeAdvanced,
        
        onResWidthChange = viewModel::onResWidthChange,
        onResHeightChange = viewModel::onResHeightChange,
        onResDpiChange = viewModel::onResDpiChange,
        onResOptionSelected = viewModel::onResOptionSelected,
        onApplyResolution = viewModel::applyResolutionChanges,
        onResetResolution = viewModel::resetResolutionChanges,
        onBack = onBack,
        onSync = {
            viewModel.refreshPrivilegeState()
            viewModel.syncState()
            viewModel.syncGames()
        }
    )
    if (uiState.isPickingGame) {
        AppPickerDialog(
            apps = uiState.allApps,
            onDismiss = viewModel::dismissGamePicker,
            onAppSelected = viewModel::addGameToLibrary,
            titleRes = R.string.gt_picker_add_game
        )
    }
    if (uiState.isPickingSuspendPackage) {
        AppPickerDialog(
            apps = uiState.allApps,
            onDismiss = viewModel::dismissSuspendPicker,
            onAppSelected = viewModel::toggleExtraSuspendPackage,
            titleRes = R.string.gt_picker_add_freeze
        )
    }
    if (uiState.isPickingSuspendExemptPackage) {
        AppPickerDialog(
            // Unlike the freeze picker (user-installed apps only), this one lists the sweep's
            // full reach: curated preinstalled apps may need keeping too, and a list that hides
            // them would leave exactly the reported YouTube-in-the-library case unsolved.
            apps = uiState.suspendExemptCandidateApps,
            onDismiss = viewModel::dismissSuspendExemptPicker,
            onAppSelected = viewModel::toggleSuspendExemptPackage,
            titleRes = R.string.gt_picker_add_keep
        )
    }
    suspensionRescue?.let { state ->
        SuspensionRescueDialog(
            state = state,
            onDismiss = viewModel::closeSuspensionRescue,
            onRestore = viewModel::restoreSelectedSuspensions
        )
    }
    if (uiState.showAggressiveCleanWarning) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAggressiveCleanWarning,
            title = { Text(stringResource(R.string.gt_dialog_aggressive_title)) },
            text = { Text(stringResource(R.string.gt_dialog_aggressive_text)) },
            confirmButton = { TextButton(onClick = viewModel::confirmAggressiveClean) { Text(stringResource(R.string.gt_dialog_clean)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissAggressiveCleanWarning) { Text(stringResource(R.string.gt_dialog_cancel)) } }
        )
    }
    
    // The ViewModel decides when a target needs confirming and supplies the concrete reason — an
    // aspect ratio the panel does not have, a size above native, or an unreadable panel — so the
    // dialog states the actual hazard rather than a generic "are you sure".
    uiState.resWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = viewModel::dismissResWarning,
            title = { Text(stringResource(R.string.gt_dialog_res_title)) },
            text = { Text(warning) },
            confirmButton = { TextButton(onClick = viewModel::confirmResWarning) { Text(stringResource(R.string.gt_dialog_apply)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissResWarning) { Text(stringResource(R.string.gt_dialog_cancel)) } }
        )
    }
}
@Composable
fun GamingToolsScreen(
    uiState: GamingToolsViewModel.UiState,
    gamingState: GamingModeState,
    gamingReport: GamingModeReport,
    gamingRecovery: GamingModeRecovery,
    isGamingSessionOpen: Boolean,
    suspendedPackages: Set<String>,
    isFixedPerformanceMode: Boolean,
    boosterLog: List<String>,
    boosterState: BoosterState,
    boosterHistory: List<BoosterRun>,
    animationScales: Triple<Float, Float, Float>,
    alwaysFinishActivities: Boolean,
    backgroundProcessLimit: Boolean,
    gameDevOptions: GameDeveloperOptions.State,
    /** Render scale for the next Gaming Mode activation, or null for full resolution. */
    interventionDownscale: Float?,
    /** Touch speed for the next activation, or null for stock (untouched). */
    pointerSpeedChoice: Int?,
    onToggleOverlay: (Boolean) -> Unit,
    onSetOverlayMoveMode: (Boolean) -> Unit,
    onResetOverlayPosition: () -> Unit,
    onToggleCrosshair: (Boolean) -> Unit,
    onOpenCrosshairLibrary: () -> Unit,
    onSetCrosshairMoveMode: (Boolean) -> Unit,
    onRecentreCrosshair: () -> Unit,
    onSetBackgroundDataRestriction: (Boolean) -> Unit,
    onToggleDnd: (Boolean) -> Unit,
    onPerformMaintenance: (List<CleaningFeature.Category>) -> Unit,
    onScanJunk: () -> Unit,
    onSetCleanSchedule: (Boolean) -> Unit,
    /** Retunes the interval of the recurring sweep (hours, one of CleanerScheduleStore.INTERVAL_CHOICES). */
    onSetCleanInterval: (Int) -> Unit,
    onAddCleanerKeepEntry: (String) -> Unit,
    onRemoveCleanerKeepEntry: (String) -> Unit,
    onAddCleanerCleanPattern: (String) -> Unit,
    onRemoveCleanerCleanPattern: (String) -> Unit,
    onGrantStorageAccess: () -> Unit,
    onActivateGamingMode: () -> Unit,
    onDeactivateGamingMode: () -> Unit,
    onRestoreGamingApps: () -> Unit,
    onOpenSuspensionRescue: () -> Unit,
    onBoostRam: () -> Unit,
    onTrimStorage: () -> Unit,
    onRunBooster: (String, Boolean) -> Unit,
    onStopBooster: () -> Unit,
    /** Enrolls or removes the recurring dexopt sweep in WorkManager. */
    onSetDexoptSchedule: (Boolean) -> Unit,
    /** Retunes the interval of the recurring sweep (hours, one of DexoptScheduleStore.INTERVAL_CHOICES). */
    onSetDexoptInterval: (Int) -> Unit,
    onBoostChange: (Int) -> Unit,
    onSetAnimationScale: (AnimationScaleKind, Float) -> Unit,
    onToggleAlwaysFinish: (Boolean) -> Unit,
    onToggleBackgroundLimit: (Boolean) -> Unit,
    onSetForcePeakRefreshRate: (Boolean) -> Unit,
    onSetGameDefaultFrameRateDisabled: (Boolean) -> Unit,
    onSelectAnglePackage: (String?) -> Unit,
    onSetAngleDriver: (AngleDriverOptions.Driver) -> Unit,
    /** Opens Android's Developer options screen for the switches this app cannot reach itself. */
    onOpenDeveloperOptions: () -> Unit,
    onRefreshDns: () -> Unit,
    onApplyDnsProvider: (DnsFeature.Provider) -> Unit,
    onSetDnsAutomatic: () -> Unit,
    onDisableDns: () -> Unit,
    onMeasureDnsPing: () -> Unit,
    onToggleFixedPerformance: (Boolean) -> Unit,
    /** Chooses the render scale (or null for full resolution) for the next activation. */
    onSetInterventionDownscale: (Float?) -> Unit,
    /** Chooses the touch speed (or null for stock) for the next activation. */
    onSetPointerSpeedChoice: (Int?) -> Unit,
    onLaunchGame: (String) -> Unit,
    onSelectGame: (String) -> Unit = {},
    onGlobalSelection: () -> Unit = {},
    onRemoveGame: (String) -> Unit,
    onOptimizeGame: (String) -> Unit,
    /** Selects the single-game scope in the unified optimisation card; null = all games. */
    onSelectOptimizeScope: (String?) -> Unit,
    onAddGameClicked: () -> Unit,
    onToggleAutoForceStop: (Boolean) -> Unit,
    onShowSuspendPicker: () -> Unit,
    onRemoveExtraSuspendPackage: (String) -> Unit,
    onShowSuspendExemptPicker: () -> Unit,
    onRemoveSuspendExemptPackage: (String) -> Unit,
    onToggleGameSession: (Boolean) -> Unit,
    /** Opens or closes the Gaming Mode card's Advanced group (both app pickers). */
    onToggleGamingModeAdvanced: () -> Unit,
    
    onResWidthChange: (String) -> Unit,
    onResHeightChange: (String) -> Unit,
    onResDpiChange: (String) -> Unit,
    onResOptionSelected: (String) -> Unit,
    onApplyResolution: () -> Unit,
    onResetResolution: () -> Unit,
    onBack: () -> Unit,
    onSync: () -> Unit,
) {
    val isActive = gamingState is GamingModeState.Active
    val isBusy = gamingState is GamingModeState.Enabling ||
        gamingState is GamingModeState.Disabling ||
        gamingState is GamingModeState.Checking
    LaunchedEffect(Unit) { onSync() }
    val hasPrivilege = uiState.isRooted || uiState.isShizukuActive
    var gameQuery by rememberSaveable { mutableStateOf("") }
    var showAllGames by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(GamingSection.PROFILE) }
    val sectionStates = rememberSaveableStateHolder()
    // One popup, owned by the selected game's name button; selecting another game dismisses it.
    var openGameMenuPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var showAutomation by rememberSaveable { mutableStateOf(false) }
    var showToolSearch by rememberSaveable { mutableStateOf(false) }
    if (showAutomation) {
        com.catsmoker.app.screens.gaming.automation.AutomationDialog(
            selectedGame = uiState.games.firstOrNull { it.packageName == uiState.selectedGamePkg },
            canApply = hasPrivilege && !isBusy && !isActive && !isGamingSessionOpen && !gamingRecovery.isNeeded,
            backendLabel = when {
                uiState.isRooted -> stringResource(R.string.gt_command_backend_uid, "Root", 0)
                uiState.isShizukuActive && uiState.authorizedShizukuUid != null ->
                    stringResource(R.string.gt_command_backend_uid, "Shizuku", uiState.authorizedShizukuUid)
                uiState.authorizedShizukuUid != null -> stringResource(R.string.gt_automation_backend_unverified, uiState.authorizedShizukuUid)
                else -> stringResource(R.string.gt_command_backend_unavailable)
            },
            onApply = onActivateGamingMode,
            onDismiss = { showAutomation = false }
        )
    }
    LaunchedEffect(uiState.selectedGamePkg) { openGameMenuPkg = null }
    ScreenScaffold(
        title = stringResource(R.string.Gaming_tools_title),
        onBack = onBack,
        trailingContent = {
            // Recognition over recall: six sections is past the point where a user can guess
            // which one holds "refresh rate", and the header has the space for the answer.
            IconButton(
                onClick = { showToolSearch = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.gt_search_desc),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            com.catsmoker.app.screens.gaming.automation.AutomationEntry(onClick = { showAutomation = true })
        }
    ) {
        if (showToolSearch) {
            com.catsmoker.app.screens.gaming.components.ToolSearchDialog(
                onDismiss = { showToolSearch = false },
                onOpenSection = { id ->
                    GamingSection.entries.firstOrNull { it.name == id }?.let { section = it }
                }
            )
        }
        val selectedGame = uiState.games.firstOrNull { it.packageName == uiState.selectedGamePkg }
        // The grid's column count follows its pane, not the device: four columns is the
        // launcher density for a phone-width body, but in the 278 dp library rail that gives
        // each tile ~66 dp and cuts every game name to "CATSMOKE R LAB GA…". The rail draws two,
        // which is still a grid and still recognisable by icon, but leaves a name readable.
        val gameLibrary: @Composable ColumnScope.(Int) -> Unit = { columns ->
            // Tap changes context only; long press opens package-addressed actions on any tile.
            // Explicit removals hide both user-added and category-discovered entries.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gt_section_your_library), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onAddGameClicked) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.core_desc_add),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (uiState.games.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.gt_no_games_detected), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                if (uiState.games.size >= GAME_SEARCH_THRESHOLD) {
                    OutlinedTextField(
                        value = gameQuery,
                        onValueChange = {
                            gameQuery = it
                            showAllGames = false
                        },
                        label = { Text(stringResource(R.string.gt_library_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                val matchingGames = remember(uiState.games, gameQuery) {
                    val sorted = uiState.games
                    if (gameQuery.isBlank()) sorted
                    else sorted.filter {
                        it.appName.contains(gameQuery, ignoreCase = true) ||
                            it.packageName.contains(gameQuery, ignoreCase = true)
                    }
                }
                val visibleGames =
                    if (showAllGames) matchingGames else GameLibraryOrdering.preview(
                        matchingGames, uiState.selectedGamePkg, GAME_LIBRARY_PREVIEW, selectedGame
                    ) { it.packageName }
                if (visibleGames.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.gt_library_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    // A plain Column of chunked Rows, not LazyVerticalGrid: this section already
                    // lives inside the screen's verticalScroll, and a lazy grid there would be
                    // measured with infinite height. The list is bounded by the library size.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        visibleGames.chunked(columns).forEach { rowGames ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                rowGames.forEach { game ->
                                    GameLibraryTile(
                                        game = game,
                                        isSelected = uiState.selectedGamePkg == game.packageName,
                                        onSelect = {
                                            openGameMenuPkg = null
                                            onSelectGame(game.packageName)
                                        },
                                        isMenuOpen = openGameMenuPkg == game.packageName,
                                        onOpenMenu = { openGameMenuPkg = game.packageName },
                                        onDismissMenu = { openGameMenuPkg = null },
                                        onLaunch = onLaunchGame,
                                        onRemove = onRemoveGame,
                                        onOptimize = onOptimizeGame,
                                        isOptimizing = uiState.optimizingGamePkg == game.packageName,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // Keep the last, incomplete row aligned to the same column
                                // widths instead of stretching its tiles across the screen.
                                repeat(columns - rowGames.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (matchingGames.size > GAME_LIBRARY_PREVIEW) {
                        TextButton(
                            onClick = { showAllGames = !showAllGames },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                stringResource(
                                    if (showAllGames) R.string.gt_library_show_less
                                    else R.string.gt_library_show_all,
                                    matchingGames.size
                                )
                            )
                        }
                    }
                }
            }
        }
        val profile: @Composable ColumnScope.(Boolean, Boolean) -> Unit = { wide, twoColumn ->
            if (!wide) {
                // Four columns is a launcher row at normal text; at a large font scale the names
                // ("HONKAI: STAR RAIL") need the extra width more than the grid needs the column.
                gameLibrary(if (LocalDensity.current.fontScale > 1.15f) 3 else GAME_GRID_COLUMNS)
                Spacer(Modifier.height(CatsmokerSpacing.CardGap))
            }
            // The caption wraps to two lines on a 411 dp phone, where it used to land flush
            // against the GAMING MODE label below it — the two lines of dot-matrix text read as
            // one crowded block. LabelGap separates a heading from the block it introduces, so
            // the caption takes it as bottom padding.
            Text(stringResource(R.string.gt_context_device_controls),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = CatsmokerSpacing.LabelGap))
            // No group heading here: GamingModeCard draws "GAMING MODE" as its own eyebrow
            // 20 dp lower, so a SectionLabel saying the same thing was pure duplication above a
            // card that already names itself. The long caption below still describes the group.
            val gamingModeCard: @Composable () -> Unit = {
                GamingModeCard(
                    gamingState = gamingState,
                    report = gamingReport,
                    // Every optimization behind this switch is a `settings put`, a `cmd`, or `pm suspend`,
                    // and Android refuses all of them to an ordinary app. With neither channel the button
                    // used to be live and the activation simply failed — so it is disabled instead, and
                    // becomes live on its own as soon as root or Shizuku is granted, because `onSync`
                    // re-reads both every time this screen is shown.
                    canActivate = hasPrivilege,
                    isActive = isActive,
                    isBusy = isBusy,
                    isSessionOpen = isGamingSessionOpen,
                    recovery = gamingRecovery,
                    isRestoringApps = uiState.isRestoringApps,
                    restoreResult = uiState.restoreResult,
                    onRestoreApps = onRestoreGamingApps,
                    onOpenSuspensionRescue = onOpenSuspensionRescue,
                    onActivate = onActivateGamingMode,
                    onDeactivate = onDeactivateGamingMode,
                    sessionActive = uiState.isGameSessionActive,
                    sessionEnabled = uiState.games.isNotEmpty(),
                    onToggleSession = onToggleGameSession,
                    // The keep list travels with the engine it modifies: the next activation (manual
                    // or automatic) reads this same store.
                    keptPackages = uiState.suspendExemptPackages,
                    keepCandidateApps = uiState.suspendExemptCandidateApps,
                    onAddKeptApp = onShowSuspendExemptPicker,
                    onRemoveKeptApp = onRemoveSuspendExemptPackage,
                    // The extra freeze list is the same sweep's second input; it moved here from
                    // Background Apps so both lists and the frozen state live on one surface.
                    extraSuspendPackages = uiState.extraSuspendPackages,
                    suspendedPackages = suspendedPackages,
                    suspendCandidateApps = uiState.allApps,
                    onAddExtraSuspend = onShowSuspendPicker,
                    onRemoveExtraSuspend = onRemoveExtraSuspendPackage,
                    // Both pickers live behind one fold now; where the fold is open is a remembered
                    // preference, not a second copy of either package list.
                    advancedExpanded = uiState.isGamingModeAdvancedExpanded,
                    onToggleAdvanced = onToggleGamingModeAdvanced
                )
            }
            val fixedPerformanceCard: @Composable () -> Unit = {
                FixedPerformanceModeCard(
                    enabled = isFixedPerformanceMode,
                    onToggle = onToggleFixedPerformance,
                    isGamingModeActive = isActive
                )
            }
            val cacheCard: @Composable () -> Unit = {
                com.catsmoker.app.screens.gaming.cache.GameCacheControls(
                    uiState.selectedGamePkg,
                    selectedGame?.appName
                )
            }
            if (twoColumn) {
                // The desktop read was one 2,300px card row: title at the far left, its toggle
                // at the far right, nothing between. Two columns keep each row at a normal
                // reading width; Gaming Mode takes the larger share because the freeze lists
                // and pickers expand inside it.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        Modifier.weight(1.15f),
                        verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)
                    ) {
                        gamingModeCard()
                    }
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)
                    ) {
                        fixedPerformanceCard()
                        cacheCard()
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)) {
                    gamingModeCard()
                    fixedPerformanceCard()
                    cacheCard()
                }
            }
            if (!isBusy && !isActive && !isGamingSessionOpen &&
                !uiState.isOverlayRunning && !uiState.isCrosshairRunning && uiState.optimizingGamePkg == null
            ) {
                GamingToolsBanner(modifier = Modifier.padding(top = CatsmokerSpacing.SectionGap))
            }
        }
        val performance: @Composable (Boolean) -> Unit = { wide ->
            val cpuCard: @Composable () -> Unit = {
                ExpandableToolCard(
                    title = stringResource(R.string.gt_cpu_policies),
                    subtitle = stringResource(R.string.gt_cpu_policies_subtitle),
                    icon = Icons.Default.Memory
                ) { CpuPolicyControls() }
            }
            val soundCard: @Composable () -> Unit = {
                ExpandableToolCard(title = stringResource(R.string.gt_tool_sound_title), subtitle = stringResource(R.string.gt_tool_sound_sub), icon = Icons.AutoMirrored.Filled.VolumeUp, info = InfoTopics.boost) {
                    BoostContent(uiState.boostLevel, uiState.audioOutput, onBoostChange)
                }
            }
            val boosterCard: @Composable () -> Unit = {
                ExpandableToolCard(
                    title = stringResource(R.string.gt_tool_booster_title),
                    subtitle = stringResource(R.string.gt_tool_booster_sub),
                    icon = Icons.Default.RocketLaunch,
                    enabled = hasPrivilege,
                    requirementNote = stringResource(R.string.gt_tool_booster_need),
                    info = InfoTopics.appBooster
                ) {
                    AppBoosterContent(
                        state = boosterState,
                        log = boosterLog,
                        history = boosterHistory,
                        games = uiState.games,
                        scopePkg = uiState.optimizeScopePkg,
                        optimizingSinglePkg = uiState.optimizingGamePkg,
                        onScopeChange = onSelectOptimizeScope,
                        onOptimizeSingle = onOptimizeGame,
                        scheduleEnabled = uiState.dexoptScheduleEnabled,
                        scheduleIntervalHours = uiState.dexoptIntervalHours,
                        scheduleNextRunAt = uiState.dexoptNextRunAt,
                        onScheduleEnabledChange = onSetDexoptSchedule,
                        onScheduleIntervalChange = onSetDexoptInterval,
                        onRun = onRunBooster,
                        onStop = onStopBooster
                    )
                }
            }
            // One card for everything that lives in Android's own Developer Options. These used to
            // be scattered across three sections, which made them look like separate features
            // rather than the one screen's worth of switches they actually are.
            val developerOptionsCard: @Composable () -> Unit = {
                ExpandableToolCard(
                    title = stringResource(R.string.gt_tool_devopts_title),
                    subtitle = stringResource(R.string.gt_tool_devopts_sub),
                    icon = Icons.Default.DeveloperMode
                ) {
                    Text(stringResource(R.string.gt_context_developer),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DeveloperOptionsContent(
                        animationScales = animationScales,
                        canWriteGlobalSettings = uiState.canWriteGlobalSettings,
                        alwaysFinishActivities = alwaysFinishActivities,
                        backgroundProcessLimit = backgroundProcessLimit,
                        hasPrivilege = hasPrivilege,
                        gameDevOptions = gameDevOptions,
                        onSetAnimationScale = onSetAnimationScale,
                        onToggleAlwaysFinish = onToggleAlwaysFinish,
                        onToggleBackgroundLimit = onToggleBackgroundLimit,
                        onSetForcePeakRefreshRate = onSetForcePeakRefreshRate,
                        onSetGameDefaultFrameRateDisabled = onSetGameDefaultFrameRateDisabled,
                        games = uiState.games,
                        anglePkg = uiState.anglePkg,
                        angleDriver = uiState.angleDriver,
                        isChangingAngleDriver = uiState.isChangingAngleDriver,
                        onSelectAnglePackage = onSelectAnglePackage,
                        onSetAngleDriver = onSetAngleDriver,
                        onOpenDeveloperOptions = onOpenDeveloperOptions
                    )
                }
            }
            if (wide) {
                // Two columns as soon as the window is wide: the desktop complaint was one narrow
                // strip of full-width cards with the space beside it doing nothing.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)) {
                        cpuCard()
                        soundCard()
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)) {
                        boosterCard()
                        developerOptionsCard()
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)) {
                    cpuCard()
                    soundCard()
                    boosterCard()
                    developerOptionsCard()
                }
            }
        }
        val graphics: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)) {
                ExpandableToolCard(title = stringResource(R.string.gt_tool_screen_title), subtitle = stringResource(R.string.gt_tool_screen_sub), icon = Icons.Default.AspectRatio, info = InfoTopics.resolution(source = uiState.nativeResolution?.let { uiState.resolutionSource } ?: stringResource(R.string.gt_res_safe_source_phone))) {
                    Text(stringResource(if (uiState.selectedGamePkg == null) R.string.gt_context_resolution_global
                        else R.string.gt_context_resolution_game),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ResolutionChangerContent(
                        native = uiState.nativeResolution,
                        activeOverride = uiState.activeOverride,
                        options = uiState.resolutionOptions,
                        selectedOptionId = uiState.selectedResolutionId,
                        width = uiState.widthInput, height = uiState.heightInput, dpi = uiState.dpiInput,
                        // Only the Custom entry unlocks the fields; a preset shows what it would apply.
                        editable = uiState.resolutionEditable,
                        validationError = uiState.resValidationError,
                        isApplying = uiState.isApplyingResolution,
                        isRoot = uiState.isRooted, isShizuku = uiState.isShizukuActive,
                        log = uiState.resLog,
                        onOptionSelected = onResOptionSelected,
                        onWidthChange = onResWidthChange, onHeightChange = onResHeightChange, onDpiChange = onResDpiChange,
                        onApply = onApplyResolution, onReset = onResetResolution,
                        downscale = interventionDownscale,
                        onDownscaleChange = onSetInterventionDownscale,
                        pointerSpeed = pointerSpeedChoice,
                        onPointerSpeedChange = onSetPointerSpeedChoice,
                        tuningEnabled = hasPrivilege && !isBusy && !isActive,
                        showGameTuning = uiState.selectedGamePkg != null
                    )
                }
                // Refresh rate sits with the other screen-level controls: it is a display
                // property, not a property of the selected game's files.
                DisplayCard()
            }
        }
        val overlays: @Composable (Boolean) -> Unit = { twoColumn ->
            val overlayCard: @Composable () -> Unit = {
                ExpandableToolCard(
                    title = stringResource(R.string.gt_tool_fps_title),
                    subtitle = stringResource(R.string.gt_tool_fps_sub),
                    icon = Icons.Default.BarChart,
                    isToggleable = true,
                    isToggled = uiState.isOverlayRunning,
                    onToggleChange = onToggleOverlay,
                    forceExpand = uiState.isOverlayRunning
                ) {
                    OverlayPositionContent(
                        isRunning = uiState.isOverlayRunning,
                        isMoveMode = uiState.isOverlayMoveMode,
                        isMoved = uiState.isOverlayMoved,
                        onSetMoveMode = onSetOverlayMoveMode,
                        onReset = onResetOverlayPosition
                    )
                }
            }
            val crosshairCard: @Composable () -> Unit = {
                ExpandableToolCard(title = stringResource(R.string.gt_tool_crosshair_title), subtitle = stringResource(R.string.gt_tool_crosshair_sub), icon = Icons.Default.AddCircleOutline, isToggleable = true, isToggled = uiState.isCrosshairRunning, onToggleChange = onToggleCrosshair, forceExpand = uiState.isCrosshairRunning) {
                    CrosshairPicker(
                        selected = uiState.selectedCrosshair,
                        onOpenLibrary = onOpenCrosshairLibrary,
                        isRunning = uiState.isCrosshairRunning,
                        isMoveMode = uiState.isCrosshairMoveMode,
                        isOffCentre = uiState.isCrosshairOffCentre,
                        onSetMoveMode = onSetCrosshairMoveMode,
                        onRecentre = onRecentreCrosshair
                    )
                }
            }
            if (twoColumn) {
                // Both overlays are independent toggles with their own controls; side by side
                // they stop being two 2,300px strips with their toggles at the far right.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) { overlayCard() }
                    Column(Modifier.weight(1f)) { crosshairCard() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)) {
                    overlayCard()
                    crosshairCard()
                }
            }
        }
        val network: @Composable (Boolean) -> Unit = { twoColumn ->
            val dndCard: @Composable () -> Unit = {
                FeatureToggleCard(title = stringResource(R.string.gt_tool_dnd_title), subtitle = stringResource(R.string.gt_tool_dnd_sub), icon = Icons.Default.NotificationsOff, checked = uiState.isDndEnabled, onCheckedChange = onToggleDnd)
            }
            val networkGroup: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)) {
                dndCard()
                // Two independent ways to stop other apps using the network, and the card carries no
                // toggle of its own: each switch lives in the body with its own state and its own
                // requirement, because they are different mechanisms and either, both, or neither is a
                // valid choice. The card title used to imply one method with one switch.
                ExpandableToolCard(
                    title = stringResource(R.string.gt_tool_net_title),
                    subtitle = stringResource(R.string.gt_tool_net_sub),
                    icon = backgroundNetworkIcon(),
                    info = backgroundDataInfo()
                ) {
                    BackgroundDataContent(
                        status = uiState.backgroundDataStatus,
                        engaged = uiState.backgroundDataEngaged,
                        isChanging = uiState.isChangingBackgroundData,
                        hasPrivilege = hasPrivilege,
                        games = uiState.games,
                        onSetDataSaver = onSetBackgroundDataRestriction
                    )
                }
                ExpandableToolCard(
                    title = stringResource(R.string.gt_tool_dns_title),
                    subtitle = stringResource(R.string.gt_tool_dns_sub),
                    icon = Icons.Default.Dns,
                    info = InfoTopics.dns(
                        provider = uiState.dnsStatus?.let { status ->
                            if (status.mode == DnsFeature.Mode.PROVIDER) {
                                DnsFeature.PROVIDERS.firstOrNull { it.hostname == status.hostname }
                            } else {
                                null
                            }
                        }
                    )
                ) {
                    DnsContent(
                        status = uiState.dnsStatus,
                        isChanging = uiState.isChangingDns,
                        onRefresh = onRefreshDns,
                        onApplyProvider = onApplyDnsProvider,
                        onSetAutomatic = onSetDnsAutomatic,
                        onDisable = onDisableDns,
                        pingMs = uiState.dnsPingMs,
                        isMeasuringPing = uiState.isMeasuringDnsPing,
                        onMeasurePing = onMeasureDnsPing
                    )
                }
            }
            }
            val backgroundAppsGroup: @Composable () -> Unit = {
                SectionLabel(stringResource(R.string.gt_section_bg_apps))
                Column(verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)) {
                ExpandableToolCard(
                    title = stringResource(R.string.gt_tool_afs_title),
                    subtitle = stringResource(R.string.gt_tool_afs_sub),
                    icon = Icons.Default.Security,
                    isToggleable = true,
                    isToggled = uiState.isAutoForceStopActive,
                    onToggleChange = onToggleAutoForceStop,
                    enabled = hasPrivilege,
                    requirementNote = stringResource(R.string.gt_tool_afs_need),
                    info = InfoTopics.autoForceStop,
                    notice = { AutoForceStopUsageNotice() },
                    // No body of its own: the Q&A moved to the info sheet and the remaining
                    // Usage-access warning stays visible above the fold.
                    expandable = false
                )
                // The extra suspend list moved into the Gaming Mode card: it is the same suspend
                // sweep's second input, not a Background Apps feature. Keeping it here as well
                // meant two surfaces could edit one list and only one of them showed whether a
                // package was actually frozen.
                    // The keep list moved into the Gaming Mode card: it is the suspend sweep's
                    // exception list, not a second Background Apps feature. Its ViewModel wiring
                    // and store stay unchanged; the screen only hosts the picker dialog.
                }
            }
            if (twoColumn) {
                // Network settings and the Background Apps switch are separate mechanisms but
                // short lists; side by side neither reads as an empty full-width strip.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CatsmokerSpacing.SectionGap),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        Modifier.weight(1.25f),
                        verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)
                    ) {
                        networkGroup()
                    }
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)
                    ) {
                        backgroundAppsGroup()
                    }
                }
            } else {
                networkGroup()
                Spacer(modifier = Modifier.height(CatsmokerSpacing.SectionGap))
                backgroundAppsGroup()
            }
        }
        val maintenance: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap)) {
                ExpandableToolCard(title = stringResource(R.string.gt_tool_cleaner_title), subtitle = stringResource(R.string.gt_tool_cleaner_sub), icon = Icons.Default.DeleteSweep, info = InfoTopics.cleaning) {
                    CleaningContent(
                        isRooted = uiState.isRooted,
                        isShizukuActive = uiState.isShizukuActive,
                        cleanResult = uiState.cleanResult,
                        report = uiState.scanReport,
                        isScanning = uiState.isScanningJunk,
                        isCleaning = uiState.isCleaningJunk,
                        keepEntries = uiState.cleanerKeepEntries,
                        cleanPatterns = uiState.cleanerCleanPatterns,
                        onAddKeepEntry = onAddCleanerKeepEntry,
                        onRemoveKeepEntry = onRemoveCleanerKeepEntry,
                        onAddCleanPattern = onAddCleanerCleanPattern,
                        onRemoveCleanPattern = onRemoveCleanerCleanPattern,
                        onScan = onScanJunk,
                        onPerform = onPerformMaintenance,
                        onGrantStorageAccess = onGrantStorageAccess,
                        scheduleEnabled = uiState.cleanScheduleEnabled,
                        scheduleIntervalHours = uiState.cleanIntervalHours,
                        scheduleNextRunAt = uiState.cleanNextRunAt,
                        onScheduleEnabledChange = onSetCleanSchedule,
                        onScheduleIntervalChange = onSetCleanInterval,
                        isBoostingRam = uiState.isBoostingRam,
                        ramResult = uiState.ramResult,
                        onBoostRam = onBoostRam,
                        isTrimmingStorage = uiState.isTrimmingStorage,
                        trimResult = uiState.storageTrimResult,
                        canTrim = hasPrivilege,
                        onTrimStorage = onTrimStorage
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 792.dp // 840 dp window minus ScreenScaffold's horizontal padding
            // Two cards side by side needs more room than the library pane does: at 792dp the
            // main pane is ~460dp, where two cards would be narrower than a phone tile.
            val twoColumn = maxWidth >= 1200.dp
            // A labelled rail costs ~190 dp of width. Below this the workspace would give up more
            // reading width than the vertical navigation saves, so the tab row stays.
            val sectionRail = maxWidth >= 1180.dp
            val tabs = GamingSection.entries.map {
                SectionTab(id = it.name, label = stringResource(it.tabLabel), icon = it.icon)
            }
            val selectSection: (SectionTab) -> Unit = { picked ->
                section = GamingSection.entries.first { it.name == picked.id }
            }
            // One scroll owner per pane: the section body scrolls, the switcher never does.
            val sectionBody: @Composable (Modifier) -> Unit = { bodyModifier ->
                sectionStates.SaveableStateProvider(section.name) {
                    Column(
                        bodyModifier.fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 24.dp)
                    ) {
                        when (section) {
                            GamingSection.PROFILE -> profile(wide, twoColumn)
                            GamingSection.GRAPHICS -> graphics()
                            GamingSection.PERFORMANCE -> performance(wide)
                            GamingSection.OVERLAYS -> overlays(twoColumn)
                            GamingSection.NETWORK -> network(twoColumn)
                            GamingSection.MAINTENANCE -> maintenance()
                        }
                    }
                }
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (wide) {
                    Column(Modifier.width(278.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        gameLibrary(GAME_RAIL_COLUMNS)
                    }
                    VerticalDivider(Modifier.fillMaxHeight())
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    GamingContextBar(
                        gameName = selectedGame?.appName ?: uiState.selectedGamePkg,
                        onGlobal = onGlobalSelection
                    )
                    if (sectionRail) {
                        // Expanded window: the sections get their own column, so no tab has to
                        // share the row with six peers across a metre of screen.
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            SectionRail(
                                sections = tabs,
                                selectedId = section.name,
                                onSelect = selectSection,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            VerticalDivider(Modifier.padding(horizontal = 12.dp).fillMaxHeight())
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                sectionBody(Modifier.fillMaxHeight())
                            }
                        }
                    } else {
                        SectionTabRow(
                            sections = tabs,
                            selectedId = section.name,
                            onSelect = selectSection
                        )
                        // The pane starts a card's own padding below the tab row. Without this the
                        // first card's top edge touched the row's bottom edge exactly, and the two
                        // read as one crowded block.
                        sectionBody(Modifier.weight(1f).padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
@Composable
fun GamingToolsPreview() {
    CatsmokerTheme {
        GamingToolsScreen(
            uiState = GamingToolsViewModel.UiState(
                isRooted = true,
                isShizukuActive = true,
                games = emptyList()
            ),
            gamingState = GamingModeState.Idle,
            gamingReport = GamingModeReport(),
            gamingRecovery = GamingModeRecovery(),
            isGamingSessionOpen = false,
            suspendedPackages = emptySet(),
            isFixedPerformanceMode = false,
            boosterLog = listOf("System optimized", "Cache cleared"),
            boosterState = BoosterState(),
            boosterHistory = emptyList(),
            animationScales = Triple(1f, 1f, 1f),
            alwaysFinishActivities = false,
            backgroundProcessLimit = false,
            gameDevOptions = GameDeveloperOptions.State(),
            interventionDownscale = null,
            pointerSpeedChoice = null,
            onToggleOverlay = {},
            onSetOverlayMoveMode = {},
            onResetOverlayPosition = {},
            onToggleCrosshair = {},
            onOpenCrosshairLibrary = {},
            onSetCrosshairMoveMode = {},
            onRecentreCrosshair = {},
            onSetBackgroundDataRestriction = {},
            onToggleDnd = {},
            onPerformMaintenance = {},
            onScanJunk = {},
            onSetCleanSchedule = {},
            onSetCleanInterval = {},
            onAddCleanerKeepEntry = {},
            onRemoveCleanerKeepEntry = {},
            onAddCleanerCleanPattern = {},
            onRemoveCleanerCleanPattern = {},
            onGrantStorageAccess = {},
            onActivateGamingMode = {},
            onDeactivateGamingMode = {},
            onRestoreGamingApps = {},
            onOpenSuspensionRescue = {},
            onBoostRam = {},
            onTrimStorage = {},
            onRunBooster = { _, _ -> },
            onStopBooster = {},
            onSetDexoptSchedule = {},
            onSetDexoptInterval = {},
            onToggleFixedPerformance = {},
            onSetInterventionDownscale = {},
            onSetPointerSpeedChoice = {},
            onBoostChange = {},
            onSetAnimationScale = { _, _ -> },
            onToggleAlwaysFinish = {},
            onToggleBackgroundLimit = {},
            onSetForcePeakRefreshRate = {},
            onSetGameDefaultFrameRateDisabled = {},
            onSelectAnglePackage = {},
            onSetAngleDriver = {},
            onOpenDeveloperOptions = {},
            onRefreshDns = {},
            onApplyDnsProvider = {},
            onSetDnsAutomatic = {},
            onDisableDns = {},
            onMeasureDnsPing = {},
            onLaunchGame = {},
            onOptimizeGame = {},
            onSelectOptimizeScope = {},
            onRemoveGame = {},
            onAddGameClicked = {},
            onToggleAutoForceStop = {},
            onShowSuspendPicker = {},
            onRemoveExtraSuspendPackage = {},
            onShowSuspendExemptPicker = {},
            onRemoveSuspendExemptPackage = {},
            onToggleGameSession = {},
            onToggleGamingModeAdvanced = {},
            onResWidthChange = {},
            onResHeightChange = {},
            onResDpiChange = {},
            onResOptionSelected = {},
            onApplyResolution = {},
            onResetResolution = {},
            onBack = {},
            onSync = {}
        )
    }
}
