package com.catsmoker.app.screens.home

import com.catsmoker.app.R

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.application.navigation.Routes
import com.catsmoker.app.integrations.ads.AdBanner
import com.catsmoker.app.screens.settings.rememberAvailableUpdateName
import com.catsmoker.app.telemetry.model.FpsSource
import com.catsmoker.app.telemetry.model.MetricReadStatus
import com.catsmoker.app.telemetry.model.MetricsState
import com.catsmoker.app.ui.components.QuickActionButton
import com.catsmoker.app.ui.components.SectionCard
import com.catsmoker.app.ui.components.liquidGlassFinish
import com.catsmoker.app.ui.theme.CatsmokerTheme
import com.catsmoker.app.ui.theme.NothingRed
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Composable
fun HomeRoute(onNavigate: (String) -> Unit) {
    val viewModel: HomeViewModel = hiltViewModel()
    val availableUpdateName = rememberAvailableUpdateName()
    val state by viewModel.metricsState.collectAsStateWithLifecycle()
    val fpsHistory by viewModel.fpsHistory.collectAsStateWithLifecycle()
    val cpuHistory by viewModel.cpuHistory.collectAsStateWithLifecycle()
    val ramHistory by viewModel.ramHistory.collectAsStateWithLifecycle()
    val tempHistory by viewModel.tempHistory.collectAsStateWithLifecycle()
    val pingHistory by viewModel.pingHistory.collectAsStateWithLifecycle()
    val adsEnabled by viewModel.adsEnabled.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // Configuration-aware read of the format; the count is substituted at press time.
    val exitTapFormat = stringResource(R.string.core_exit_tap)
    var backPressedCount by remember { mutableIntStateOf(0) }
    var lastBackPressedTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressedTime < 2000) {
            backPressedCount++
        } else {
            backPressedCount = 1
        }
        lastBackPressedTime = currentTime

        if (backPressedCount >= 3) {
            (context as? Activity)?.finish()
        } else {
            Toast.makeText(
                context,
                String.format(Locale.getDefault(), exitTapFormat, 3 - backPressedCount),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startMetrics()
    }

    HomeScreen(
        state = state,
        fpsHistory = fpsHistory,
        cpuHistory = cpuHistory,
        ramHistory = ramHistory,
        tempHistory = tempHistory,
        pingHistory = pingHistory,
        adsEnabled = adsEnabled,
        availableUpdateName = availableUpdateName,
        onNavigateDistribution = onNavigate,
        onOpenEditGameFiles = { onNavigate(Routes.EDIT_GAME_FILES) },
        onOpenGamingTools = { onNavigate(Routes.GAMING_TOOLS) },
        onOpenSettings = { onNavigate(Routes.SETTINGS) }
    )
}

@Composable
fun HomeScreen(
    state: MetricsState,
    fpsHistory: List<Int>,
    cpuHistory: List<Int>,
    ramHistory: List<Float>,
    tempHistory: List<Float>,
    pingHistory: List<Int>,
    adsEnabled: Boolean,
    /** Version name of a release the last check found, null when up to date. Drives the update indicator on the Settings tile. */
    availableUpdateName: String? = null,
    onNavigateDistribution: (String) -> Unit,
    onOpenEditGameFiles: () -> Unit,
    onOpenGamingTools: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isPreview = LocalInspectionMode.current
    var hydrationPhase by remember { mutableIntStateOf(if (isPreview) 4 else 3) }
    var showAdsDeferred by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isPreview) return@LaunchedEffect
        withFrameNanos { }
        hydrationPhase = 4
    }

    LaunchedEffect(adsEnabled) {
        if (isPreview || !adsEnabled) return@LaunchedEffect
        kotlinx.coroutines.delay(5.seconds)
        showAdsDeferred = true
    }

    // Responsive dashboard: portrait keeps the existing no-scroll vertical rhythm
    // (performance card on top, quick actions below, ad slot reserved at the bottom);
    // landscape switches to a two-column layout so neither column is squeezed by the
    // short viewport height.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 24.dp)
    ) {
        val landscape = maxWidth > maxHeight
        // A desktop window is wide *and* tall. The portrait column would stretch the console
        // tiles to ~700x830 dp of mostly empty gradient, and the landscape split exists only to
        // rescue short viewports, not to fill a 1920x1200 one. Expanded therefore gets its own
        // bounded composition: a fixed-height telemetry card and a grid of content-sized tiles,
        // centred and capped so the window reads as a dashboard instead of a stretched phone.
        val expanded = maxWidth >= 792.dp && maxHeight >= 560.dp
        // Phone-sized badges look like crumbs once the tiles are half a metre wide on a
        // desktop window, so the badge (and the chevron inside it) tracks the window width
        // instead of staying at the 32/40dp phone values.
        val tileIconSize = when {
            maxWidth >= 1200.dp -> 64.dp
            maxWidth >= 840.dp -> 56.dp
            maxWidth >= 600.dp -> 46.dp
            landscape -> 32.dp
            else -> 40.dp
        }
        if (expanded) {
            ExpandedDashboard(
                state = state,
                fpsHistory = fpsHistory,
                cpuHistory = cpuHistory,
                ramHistory = ramHistory,
                tempHistory = tempHistory,
                pingHistory = pingHistory,
                hydrationPhase = hydrationPhase,
                adsEnabled = adsEnabled && hydrationPhase >= 3 && showAdsDeferred,
                availableUpdateName = availableUpdateName,
                onNavigateDistribution = onNavigateDistribution,
                onOpenEditGameFiles = onOpenEditGameFiles,
                onOpenGamingTools = onOpenGamingTools,
                onOpenSettings = onOpenSettings
            )
        } else if (!landscape) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            HomeHeader(state = state, verticalPadding = 20.dp)

        // 2. Performance Monitor
        if (hydrationPhase >= 1) {
            TelemetryCard(
                state = state,
                fpsHistory = fpsHistory,
                cpuHistory = cpuHistory,
                ramHistory = ramHistory,
                tempHistory = tempHistory,
                pingHistory = pingHistory,
                hydrationPhase = hydrationPhase,
                contentPadding = 16.dp,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Quick Actions
        if (hydrationPhase >= 2) {
            QuickActionsSection(
                modifier = Modifier.fillMaxWidth().weight(1.5f),
                compact = false,
                iconSize = tileIconSize,
                availableUpdateName = availableUpdateName,
                onNavigateDistribution = onNavigateDistribution,
                onOpenEditGameFiles = onOpenEditGameFiles,
                onOpenGamingTools = onOpenGamingTools,
                onOpenSettings = onOpenSettings
            )
        } else {
            Spacer(modifier = Modifier.weight(1.5f))
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // 4. Ads (Ultra Deferred)
        val adSlotHeight = if (with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp) 90.dp else 50.dp
        if (adsEnabled && hydrationPhase >= 3 && showAdsDeferred) {
            AdBanner(modifier = Modifier.padding(bottom = 8.dp))
        } else {
            Spacer(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .height(adSlotHeight)
            )
        }
            }
        } else {
            // Landscape: two-column layout so the short viewport height does not
            // squeeze the performance card and the quick actions into one column.
            Column(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                HomeHeader(state = state, verticalPadding = 8.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (hydrationPhase >= 1) {
                        TelemetryCard(
                            state = state,
                            fpsHistory = fpsHistory,
                            cpuHistory = cpuHistory,
                            ramHistory = ramHistory,
                            tempHistory = tempHistory,
                            pingHistory = pingHistory,
                            hydrationPhase = hydrationPhase,
                            contentPadding = 12.dp,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (hydrationPhase >= 2) {
                        QuickActionsSection(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            compact = true,
                            iconSize = tileIconSize,
                            availableUpdateName = availableUpdateName,
                            onNavigateDistribution = onNavigateDistribution,
                            onOpenEditGameFiles = onOpenEditGameFiles,
                            onOpenGamingTools = onOpenGamingTools,
                            onOpenSettings = onOpenSettings
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                val adSlotHeight2 = if (with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp) 90.dp else 50.dp
                if (adsEnabled && hydrationPhase >= 3 && showAdsDeferred) {
                    AdBanner(modifier = Modifier.padding(bottom = 4.dp))
                } else {
                    Spacer(modifier = Modifier.padding(bottom = 4.dp).height(adSlotHeight2))
                }
            }
        }
    }
}

/**
 * The title row: wordmark plus the two privilege badges.
 *
 * Shared by every dashboard branch. It used to be copy-pasted per branch, which is how the
 * landscape copy drifted (placeholder `?` instead of an em dash) from the portrait one.
 */
@Composable
private fun HomeHeader(state: MetricsState, verticalPadding: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(label = stringResource(R.string.res_method_root), active = state.hasRoot, activeColor = NothingRed)
            StatusBadge(label = stringResource(R.string.res_method_shizuku), active = state.hasShizuku, activeColor = NothingRed)
        }
    }
}

/**
 * The live telemetry card: the FPS readout, four compact stats and the combined chart.
 *
 * Extracted because the phone, landscape and expanded dashboards all show exactly this card and
 * had three copies of it. [contentPadding] is the only thing they disagree on — the scale comes
 * from the caller's `Modifier`, so the same card is a weighted half of a phone column, a short
 * landscape column, or a fixed-height panel on a desktop window.
 */
@Composable
private fun TelemetryCard(
    state: MetricsState,
    fpsHistory: List<Int>,
    cpuHistory: List<Int>,
    ramHistory: List<Float>,
    tempHistory: List<Float>,
    pingHistory: List<Int>,
    hydrationPhase: Int,
    contentPadding: Dp,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier, contentPadding = contentPadding) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.dash_live_performance),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    // The number is whatever was actually measured; when nothing was, the reason
                    // takes the label's place rather than a 0 appearing here.
                    Text(
                        text = state.fps?.toString() ?: "—",
                        style = MaterialTheme.typography.displayLarge,
                        color = NothingRed
                    )
                    Text(
                        text = when {
                            state.fps == null -> stringResource(state.fpsReadStatus.labelRes)
                            // The vsync fallback counts this app's frames, not the game's, so it
                            // is never labelled plain "FPS".
                            state.fpsSource == FpsSource.Choreographer ->
                                stringResource(R.string.dash_fps_label_ui)
                            else -> stringResource(R.string.dash_fps_label)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CompactStat(
                        label = stringResource(R.string.core_metric_cpu),
                        value = state.cpuPercentage
                            ?.let { "$it%" }
                            ?: state.cpuReadStatus.compactLabel(),
                        color = Color(0xFF22C55E)
                    )
                    CompactStat(
                        label = stringResource(R.string.core_metric_ram),
                        value = state.ramUsedGb
                            ?.let { String.format(Locale.US, "%.1fG", it) }
                            ?: state.ramReadStatus.compactLabel(),
                        color = Color(0xFF3B82F6)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Label doubles as the source: SoC sensor when available, else battery.
                    CompactStat(
                        label = if (state.displayTempIsSoc) stringResource(R.string.core_metric_soc) else stringResource(R.string.core_metric_temp),
                        value = state.displayTempC
                            ?.let { "${it.toInt()}°" }
                            ?: state.displayTempReadStatus.compactLabel(),
                        color = Color(0xFFF59E0B)
                    )
                    CompactStat(
                        label = stringResource(R.string.core_metric_ping),
                        value = state.pingMs
                            ?.let { "${it}ms" }
                            ?: state.pingReadStatus.compactLabel(),
                        color = Color(0xFF8B5CF6)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (hydrationPhase >= 4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CombinedChart(
                    fpsHistory = fpsHistory,
                    // No privilege gate needed: the engine only appends readings it actually
                    // took, so an unreadable channel contributes no line.
                    cpuHistory = cpuHistory,
                    ramHistory = ramHistory,
                    tempHistory = tempHistory,
                    pingHistory = pingHistory,
                    ramTotal = state.ramTotalGb,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** Widest the dashboard is allowed to draw; beyond this the cards stop being readable spans. */
private val DashboardMaxWidth = 1040.dp
/** Fixed height of the telemetry panel on an expanded window. */
private val DashboardTelemetryHeight = 320.dp
/** Height of one console tile on an expanded window — content-sized, not window-sized. */
private val DashboardTileHeight = 152.dp

/**
 * The dashboard on an expanded window (desktop, large tablet, DeX).
 *
 * Measured before this existed, at 1920x1200 dp: the four console tiles came out ~700x830 dp
 * each, of which roughly four fifths was empty gradient, because both the tile rows and the
 * telemetry card were dividing whatever height the window happened to have. Nothing was broken
 * and nothing was off-screen — the composition simply stopped being a dashboard.
 *
 * So the expanded branch stops dividing and starts sizing: the workspace is capped at
 * [DashboardMaxWidth], the telemetry panel is a fixed [DashboardTelemetryHeight], the console
 * tiles are [DashboardTileHeight] tall, and the board is centred in the window rather than
 * pinned to the top. 1920 dp of window then reads as one composed board of real content instead
 * of a phone layout stretched in both axes, and it keeps the same shape when the window is
 * resized from 1200 dp to 720 dp tall.
 */
@Composable
private fun ExpandedDashboard(
    state: MetricsState,
    fpsHistory: List<Int>,
    cpuHistory: List<Int>,
    ramHistory: List<Float>,
    tempHistory: List<Float>,
    pingHistory: List<Int>,
    hydrationPhase: Int,
    adsEnabled: Boolean,
    availableUpdateName: String?,
    onNavigateDistribution: (String) -> Unit,
    onOpenEditGameFiles: () -> Unit,
    onOpenGamingTools: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = DashboardMaxWidth)
                .padding(vertical = 24.dp)
        ) {
            HomeHeader(state = state, verticalPadding = 0.dp)

            if (hydrationPhase >= 1) {
                TelemetryCard(
                    state = state,
                    fpsHistory = fpsHistory,
                    cpuHistory = cpuHistory,
                    ramHistory = ramHistory,
                    tempHistory = tempHistory,
                    pingHistory = pingHistory,
                    hydrationPhase = hydrationPhase,
                    contentPadding = 20.dp,
                    modifier = Modifier.fillMaxWidth().height(DashboardTelemetryHeight)
                )
            } else {
                Spacer(modifier = Modifier.height(DashboardTelemetryHeight))
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (hydrationPhase >= 2) {
                QuickActionsSection(
                    modifier = Modifier.fillMaxWidth(),
                    compact = false,
                    iconSize = 56.dp,
                    tileHeight = DashboardTileHeight,
                    availableUpdateName = availableUpdateName,
                    onNavigateDistribution = onNavigateDistribution,
                    onOpenEditGameFiles = onOpenEditGameFiles,
                    onOpenGamingTools = onOpenGamingTools,
                    onOpenSettings = onOpenSettings
                )
            }
        }

        // The ad slot is the one thing that stays pinned: it is the only element that must sit
        // against the window edge rather than travel with the centred board.
        if (adsEnabled) {
            AdBanner(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

// Isolate stable navigation controls so telemetry updates can skip this subtree.
@Composable
private fun QuickActionsSection(
    modifier: Modifier,
    compact: Boolean,
    /** Badge size for the tiles; the host picks it from the window width. */
    iconSize: Dp,
    /**
     * Fixed height for each row of tiles, or null to divide the incoming height instead.
     *
     * Portrait and landscape divide whatever space the frame gives them, which is right on a
     * phone. An expanded window is far taller than four tiles need, and dividing it turned each
     * tile into ~830 dp of empty gradient; a null here is what keeps that from happening.
     */
    tileHeight: Dp? = null,
    availableUpdateName: String?,
    onNavigateDistribution: (String) -> Unit,
    onOpenEditGameFiles: () -> Unit,
    onOpenGamingTools: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val spacing = if (compact) 8.dp else 12.dp
    // Padding has to follow the badge: a 64dp badge inside 8dp padding is edge-to-edge.
    val contentPadding = when {
        iconSize >= 56.dp -> 20.dp
        iconSize >= 46.dp -> 16.dp
        compact -> 8.dp
        else -> 12.dp
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.dash_quick_actions),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Column(
            modifier = if (tileHeight == null) Modifier.weight(1f) else Modifier,
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            Row(
                modifier = if (tileHeight == null) Modifier.weight(1f) else Modifier.height(tileHeight),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                DistributionQuickAction(onNavigateDistribution, contentPadding, iconSize)
                EditGameFilesAction(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = contentPadding,
                    iconSize = iconSize,
                    onClick = onOpenEditGameFiles
                )
            }

            Row(
                modifier = if (tileHeight == null) Modifier.weight(1f) else Modifier.height(tileHeight),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                QuickActionButton(
                    title = stringResource(R.string.Gaming_tools_title),
                    subtitle = stringResource(R.string.dash_gaming_tools_subtitle),
                    iconContainerColor = Color(0xFF10B981).copy(alpha = 0.15f),
                    iconContentColor = Color(0xFF34D399),
                    onClick = onOpenGamingTools,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    spreadGridContent = true,
                    showChevron = true,
                    contentPadding = contentPadding,
                    iconSize = iconSize,
                    icon = { Icon(Icons.Default.SportsEsports, null) }
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    QuickActionButton(
                        title = stringResource(R.string.core_settings_title),
                        subtitle = stringResource(R.string.core_settings_subtitle),
                        iconContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                        iconContentColor = Color(0xFFA78BFA),
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxSize(),
                        spreadGridContent = true,
                        showChevron = true,
                        showIndicator = availableUpdateName != null,
                        contentPadding = contentPadding,
                        iconSize = iconSize,
                        icon = { Icon(Icons.Default.Settings, null) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (compact) 4.dp else 12.dp))
    }
}

@Composable
private fun EditGameFilesAction(
    modifier: Modifier,
    contentPadding: Dp,
    iconSize: Dp,
    onClick: () -> Unit
) {
    QuickActionButton(
        title = stringResource(R.string.dash_edit_files_title),
        subtitle = stringResource(R.string.dash_edit_files_subtitle),
        iconContainerColor = Color(0xFF3B82F6).copy(alpha = 0.15f),
        iconContentColor = Color(0xFF60A5FA),
        onClick = onClick,
        modifier = modifier,
        spreadGridContent = true,
        showChevron = true,
        contentPadding = contentPadding,
        iconSize = iconSize,
        icon = { Icon(Icons.Default.FolderOpen, null) }
    )
}

@Composable
fun CombinedChart(
    fpsHistory: List<Int>,
    cpuHistory: List<Int>,
    ramHistory: List<Float>,
    tempHistory: List<Float>,
    pingHistory: List<Int>,
    /** Total RAM, used as the y-scale. Null when it could not be read. */
    ramTotal: Float?,
    modifier: Modifier = Modifier
) {
    val nothingRed = NothingRed
    val colors = MaterialTheme.colorScheme
    val gridColor = colors.onSurface.copy(alpha = 0.05f)
    val chartBackground = colors.surfaceVariant.copy(alpha = 0.5f).compositeOver(colors.surface)
    val chartShape = RoundedCornerShape(12.dp)
    
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(chartShape)
            .border(1.dp, colors.outline.copy(alpha = 0.3f), chartShape)
            .background(chartBackground)
            .liquidGlassFinish(chartShape, showRim = false, reflectionColor = colors.onSurface)
            .padding(8.dp)
            .drawWithCache {
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                
                val fpsP = createPath(fpsHistory.map { it.toFloat() }, size, 60f)
                val cpuP = createPath(cpuHistory.map { it.toFloat() }, size, 100f)
                // With no total there is no scale, and with no total there are also no samples —
                // the engine only pushes readings it actually took — so this draws nothing.
                val ramP = createPath(ramHistory, size, ramTotal ?: 0f)
                val tempP = createPath(tempHistory, size, 60f)
                val pingP = createPath(pingHistory.map { it.toFloat() }, size, 200f)

                onDrawBehind {
                    // Draw horizontal telemetry grid lines
                    val h1 = size.height * 0.33f
                    val h2 = size.height * 0.66f
                    drawLine(gridColor, start = Offset(0f, h1), end = Offset(size.width, h1), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, start = Offset(0f, h2), end = Offset(size.width, h2), strokeWidth = 1.dp.toPx())

                    drawPath(fpsP, nothingRed, style = stroke)
                    drawPath(cpuP, Color(0xFF22C55E), style = stroke)
                    drawPath(ramP, Color(0xFF3B82F6), style = stroke)
                    drawPath(tempP, Color(0xFFF59E0B), style = stroke)
                    drawPath(pingP, Color(0xFF8B5CF6), style = stroke)
                }
            }
    )
}

private fun createPath(history: List<Float>, size: androidx.compose.ui.geometry.Size, baseMax: Float): Path {
    val path = Path()
    if (history.size < 2) return path
    val w = size.width
    val h = size.height
    val currentMax = history.maxOrNull()?.coerceAtLeast(baseMax)?.coerceAtLeast(1f) ?: 1f
    
    history.forEachIndexed { i, value ->
        val x = w * i / (history.size - 1).toFloat()
        val y = h * (1f - value / currentMax)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

@Composable
fun StatusBadge(label: String, active: Boolean, activeColor: Color) {
    Box(
        modifier = Modifier
            .background(
                if (active) activeColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (active) activeColor.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun CompactStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f),
            fontSize = 9.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

/**
 * What a compact stat shows when there is no reading.
 *
 * Short enough for the dashboard row and never a number: a 0 here would be a value the device never
 * reported. The full reason is spelled out in the performance overlay, which has room for it.
 */
@Composable
private fun MetricReadStatus.compactLabel(): String = when (this) {
    MetricReadStatus.Loading -> stringResource(R.string.core_compact_loading)
    MetricReadStatus.PrivilegeDenied -> stringResource(R.string.core_compact_privilege)
    MetricReadStatus.Unsupported -> stringResource(R.string.core_compact_unsupported)
    else -> stringResource(R.string.core_compact_na)
}

/**
 * Small update-available indicator. Rendered only when an update is available.
 */
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun MainPreview() {
    CatsmokerTheme {
        HomeScreen(
            state = MetricsState(
                fps = 60,
                fpsSource = FpsSource.SurfaceFlinger,
                fpsReadStatus = MetricReadStatus.Ok,
                cpuPercentage = 45,
                ramUsedGb = 4.2f,
                ramTotalGb = 8.0f,
                ramReadStatus = MetricReadStatus.Ok,
                batteryTempC = 38f,
                batteryReadStatus = MetricReadStatus.Ok,
                powerW = 4.8f,
                powerReadStatus = MetricReadStatus.Ok,
                pingMs = 24,
                pingReadStatus = MetricReadStatus.Ok,
                cpuReadStatus = MetricReadStatus.Ok,
                hasRoot = true,
                hasShizuku = true
            ),
            fpsHistory = listOf(55, 58, 60, 59, 60, 60, 57, 58, 60),
            cpuHistory = listOf(40, 45, 50, 42, 45),
            ramHistory = listOf(4.0f, 4.2f, 4.1f),
            tempHistory = listOf(37f, 38f, 38f),
            pingHistory = listOf(20, 24, 22),
            adsEnabled = true,
            onNavigateDistribution = {},
            onOpenEditGameFiles = {},
            onOpenGamingTools = {},
            onOpenSettings = {}
        )
    }
}
