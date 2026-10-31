package com.catsmoker.app.features.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import android.app.Activity
import android.widget.Toast
import androidx.compose.ui.platform.LocalConfiguration
import com.catsmoker.app.R
import com.catsmoker.app.shared.data.model.FpsSource
import com.catsmoker.app.shared.data.model.MetricReadStatus
import com.catsmoker.app.shared.data.model.MetricsState
import com.catsmoker.app.shared.ui.components.QuickActionButton
import com.catsmoker.app.shared.ui.components.UpdateIndicator
import com.catsmoker.app.shared.ui.components.SectionCard
import com.catsmoker.app.shared.ui.components.AdBanner
import com.catsmoker.app.system.VariantCapabilities
import com.catsmoker.app.system.navigation.Routes
import com.catsmoker.app.shared.ui.theme.CatsmokerTheme
import com.catsmoker.app.shared.ui.theme.NothingRed
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun MainRoute(onNavigate: (String) -> Unit) {
    val viewModel: MainViewModel = hiltViewModel()
    val settingsViewModel: com.catsmoker.app.features.settings.SettingsViewModel = hiltViewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    val state by viewModel.metricsState.collectAsState()
    val fpsHistory by viewModel.fpsHistory.collectAsState()
    val cpuHistory by viewModel.cpuHistory.collectAsState()
    val ramHistory by viewModel.ramHistory.collectAsState()
    val tempHistory by viewModel.tempHistory.collectAsState()
    val pingHistory by viewModel.pingHistory.collectAsState()
    val adsEnabled by viewModel.adsEnabled.collectAsState()

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

    MainScreen(
        state = state,
        fpsHistory = fpsHistory,
        cpuHistory = cpuHistory,
        ramHistory = ramHistory,
        tempHistory = tempHistory,
        pingHistory = pingHistory,
        adsEnabled = adsEnabled,
        availableUpdateName = settingsUiState.availableUpdateName,
        onOpenSpoofDevice = { onNavigate(Routes.SPOOF_DEVICE) },
        onOpenEditGameFiles = { onNavigate(Routes.EDIT_GAME_FILES) },
        onOpenGamingTools = { onNavigate(Routes.GAMING_TOOLS) },
        onOpenSettings = { onNavigate(Routes.SETTINGS) }
    )
}

@Composable
fun MainScreen(
    state: MetricsState,
    fpsHistory: List<Int>,
    cpuHistory: List<Int>,
    ramHistory: List<Float>,
    tempHistory: List<Float>,
    pingHistory: List<Int>,
    adsEnabled: Boolean,
    /** Version name of a release the last check found, null when up to date. Drives the update indicator on the Settings tile. */
    availableUpdateName: String? = null,
    onOpenSpoofDevice: () -> Unit,
    onOpenEditGameFiles: () -> Unit,
    onOpenGamingTools: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isPreview = LocalInspectionMode.current
    var hydrationPhase by remember { mutableIntStateOf(if (isPreview) 4 else 0) }
    var showAdsDeferred by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isPreview) return@LaunchedEffect
        // One-frame "breath" to let the system settle after splash removal
        kotlinx.coroutines.delay(16.milliseconds) 
        
        // Progressive Hydration Timeline
        kotlinx.coroutines.delay(50.milliseconds) 
        hydrationPhase = 1
        kotlinx.coroutines.delay(100.milliseconds) 
        hydrationPhase = 2
        kotlinx.coroutines.delay(150.milliseconds) 
        hydrationPhase = 3
        kotlinx.coroutines.delay(300.milliseconds) 
        hydrationPhase = 4
    }

    LaunchedEffect(adsEnabled) {
        if (isPreview || !adsEnabled) return@LaunchedEffect
        kotlinx.coroutines.delay(5.seconds)
        showAdsDeferred = true
    }

    val metricsAlpha by animateFloatAsState(if (hydrationPhase >= 1) 1f else 0f, tween(300), label = "metrics")
    val actionsAlpha by animateFloatAsState(if (hydrationPhase >= 2) 1f else 0f, tween(300), label = "actions")
    val chartAlpha by animateFloatAsState(if (hydrationPhase >= 3) 1f else 0f, tween(500), label = "chart")

    // No-scroll dashboard that fills the viewport with content, not gaps: the performance
    // card and the actions block split leftover height by weight (1 : 1.5), and the chart,
    // grid gap, and tiles absorb their share inside their own bounds. Fixed spacers
    // separate sections. Ad slot stays reserved at the bottom.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        // 1. Header (Always Instant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
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

        // 2. Performance Monitor (Progressive)
        if (hydrationPhase >= 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer { alpha = metricsAlpha }
            ) {
                SectionCard(modifier = Modifier.fillMaxSize(), contentPadding = 16.dp) {
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
                                // The number is whatever was actually measured; when nothing was,
                                // the reason takes the label's place rather than a 0 appearing here.
                                Text(
                                    text = state.fps?.toString() ?: "—",
                                    style = MaterialTheme.typography.displayLarge,
                                    color = NothingRed
                                )
                                Text(
                                    text = when {
                                        state.fps == null -> stringResource(state.fpsReadStatus.labelRes)
                                        // The vsync fallback counts this app's frames, not the
                                        // game's, so it is never labelled plain "FPS".
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
                                .graphicsLayer { alpha = chartAlpha }
                        ) {
                            CombinedChart(
                                fpsHistory = fpsHistory,
                                // No privilege gate needed: the engine only appends readings it
                                // actually took, so an unreadable channel contributes no line.
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
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Quick Actions (Progressive)
        if (hydrationPhase >= 2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.5f)
                    .graphicsLayer { alpha = actionsAlpha }
            ) {
                Text(
                    text = stringResource(R.string.dash_quick_actions),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Spoof card exists only where the implementation is compiled in
                    // (full variant). Play keeps the no-scroll 2x2 rhythm by letting
                    // File Engineering span the row instead.
                    if (VariantCapabilities.HAS_SPOOF) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QuickActionButton(
                                title = stringResource(R.string.dash_spoof_title),
                                subtitle = stringResource(R.string.dash_spoof_subtitle),
                                iconContainerColor = NothingRed.copy(alpha = 0.15f),
                                iconContentColor = NothingRed,
                                onClick = onOpenSpoofDevice,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                spreadGridContent = true,
                                showChevron = true,
                                contentPadding = 12.dp,
                                iconSize = 40.dp,
                                icon = { Icon(Icons.Default.SettingsInputComponent, null) }
                            )
                            QuickActionButton(
                                title = stringResource(R.string.dash_edit_files_title),
                                subtitle = stringResource(R.string.dash_edit_files_subtitle),
                                iconContainerColor = Color(0xFF3B82F6).copy(alpha = 0.15f),
                                iconContentColor = Color(0xFF60A5FA),
                                onClick = onOpenEditGameFiles,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                spreadGridContent = true,
                                showChevron = true,
                                contentPadding = 12.dp,
                                iconSize = 40.dp,
                                icon = { Icon(Icons.Default.FolderOpen, null) }
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QuickActionButton(
                                title = stringResource(R.string.dash_edit_files_title),
                                subtitle = stringResource(R.string.dash_edit_files_subtitle),
                                iconContainerColor = Color(0xFF3B82F6).copy(alpha = 0.15f),
                                iconContentColor = Color(0xFF60A5FA),
                                onClick = onOpenEditGameFiles,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                spreadGridContent = true,
                                showChevron = true,
                                contentPadding = 12.dp,
                                iconSize = 40.dp,
                                icon = { Icon(Icons.Default.FolderOpen, null) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            contentPadding = 12.dp,
                            iconSize = 40.dp,
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
                                contentPadding = 12.dp,
                                iconSize = 40.dp,
                                icon = { Icon(Icons.Default.Settings, null) }
                            )
                            if (availableUpdateName != null) {
                                // New indicator: only visible when an update is available.
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                ) {
                                    UpdateIndicator()
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        } else {
            Spacer(modifier = Modifier.weight(1.5f))
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // 4. Ads (Ultra Deferred)
        val adSlotHeight = if (LocalConfiguration.current.screenWidthDp >= 600) 90.dp else 50.dp
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
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            // Theme panel, not a fixed tint: the old white-3% wash was invisible on a
            // light card, leaving the lines floating on the card itself.
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
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

                val fpsFillP = Path().apply {
                    addPath(fpsP)
                    if (fpsHistory.size >= 2) {
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                }

                val fpsGradient = Brush.verticalGradient(
                    colors = listOf(nothingRed.copy(alpha = 0.20f), Color.Transparent),
                    startY = 0f,
                    endY = size.height
                )

                onDrawBehind {
                    // Draw horizontal telemetry grid lines
                    val h1 = size.height * 0.33f
                    val h2 = size.height * 0.66f
                    drawLine(gridColor, start = Offset(0f, h1), end = Offset(size.width, h1), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, start = Offset(0f, h2), end = Offset(size.width, h2), strokeWidth = 1.dp.toPx())

                    // Ambient gradient under live FPS path
                    if (fpsHistory.size >= 2) {
                        drawPath(fpsFillP, brush = fpsGradient)
                    }

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
        MainScreen(
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
            onOpenSpoofDevice = {},
            onOpenEditGameFiles = {},
            onOpenGamingTools = {},
            onOpenSettings = {}
        )
    }
}
