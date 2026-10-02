package com.catsmoker.app.features.main.engine

import android.content.Context
import com.catsmoker.app.shared.data.model.MetricsState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One recorded metrics sample: the [MetricsState] fields the session CSV carries, frozen with
 * its timestamp. Plain data so the CSV mapping stays JVM-testable without a device.
 */
data class SessionSample(
    val timestampMs: Long,
    val fps: Int?,
    val jankyFrames: Int?,
    val cpuPercent: Int?,
    val clusterEffMhz: Int?,
    val clusterPerfMhz: Int?,
    val clusterUltraMhz: Int?,
    val ramUsedGb: Float?,
    val ramTotalGb: Float?,
    val batteryTempC: Float?,
    val networkRxKbps: Float,
    val networkTxKbps: Float,
    val pingMs: Int?,
    val thermalCpuC: Float?,
    val thermalGpuC: Float?,
    val thermalNpuC: Float?,
    val thermalSkinC: Float?,
    val thermalStatus: Int,
    val topProcessName: String?,
    val topProcessCpuPercent: Float
)

/** Maps live state onto a sample. Pure for testability. */
fun sampleOf(state: MetricsState, timestampMs: Long): SessionSample = SessionSample(
    timestampMs = timestampMs,
    fps = state.fps,
    jankyFrames = state.jankyFrames,
    cpuPercent = state.cpuPercentage,
    clusterEffMhz = state.cpuClusterEffMhz,
    clusterPerfMhz = state.cpuClusterPerfMhz,
    clusterUltraMhz = state.cpuClusterUltraMhz,
    ramUsedGb = state.ramUsedGb,
    ramTotalGb = state.ramTotalGb,
    batteryTempC = state.batteryTempC,
    networkRxKbps = state.networkRxKbps,
    networkTxKbps = state.networkTxKbps,
    pingMs = state.pingMs,
    thermalCpuC = state.thermalCpuC,
    thermalGpuC = state.thermalGpuC,
    thermalNpuC = state.thermalNpuC,
    thermalSkinC = state.thermalSkinC,
    thermalStatus = state.thermalStatus,
    topProcessName = state.topProcessName,
    topProcessCpuPercent = state.topProcessCpuPercent
)

/**
 * The session-CSV document: header + one row per sample. Nulls render empty, never 0 — a
 * missing sensor and a genuine zero are different facts, and the CSV must not conflate them.
 */
object SessionCsv {

    fun header(): String =
        "timestamp,fps,janky_frames,cpu_mhz,cpu_percent,cpu_cluster_eff_mhz," +
            "cpu_cluster_perf_mhz,cpu_cluster_ultra_mhz,ram_used_gb,ram_total_gb," +
            "battery_temp_c,network_rx_kbps,network_tx_kbps,ping_ms,thermal_cpu_c," +
            "thermal_gpu_c,thermal_npu_c,thermal_skin_c,thermal_status," +
            "top_process,top_process_cpu_percent"

    fun row(s: SessionSample): String {
        fun any(v: Any?): String = v?.toString() ?: ""
        return listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(s.timestampMs)),
            any(s.fps),
            any(s.jankyFrames),
            "", // No total-MHz signal exists; the cluster columns carry the facts.
            any(s.cpuPercent),
            any(s.clusterEffMhz),
            any(s.clusterPerfMhz),
            any(s.clusterUltraMhz),
            any(s.ramUsedGb),
            any(s.ramTotalGb),
            any(s.batteryTempC),
            s.networkRxKbps.toString(),
            s.networkTxKbps.toString(),
            any(s.pingMs),
            any(s.thermalCpuC),
            any(s.thermalGpuC),
            any(s.thermalNpuC),
            any(s.thermalSkinC),
            s.thermalStatus.toString(),
            s.topProcessName ?: "",
            s.topProcessCpuPercent.toString()
        ).joinToString(",")
    }

    fun fileName(nowMs: Long): String =
        "catsmoker_session_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMs)) + ".csv"
}

/**
 * Records the metrics timeline to a shareable session CSV — for attaching to a bug report,
 * or correlating which metric lines up with an FPS drop at a specific second.
 *
 * Adapted from the reference `SessionLogger` (read in full): same document shape, same
 * filesDir + FileProvider share. Two deliberate divergences: this app has no snapshot
 * history or per-module overlay toggles, so recording samples [MetricsState] on a 1 s
 * cadence into a bounded buffer ([MAX_SAMPLES], oldest evicted) instead of slicing a
 * history — and recording never force-enables anything, because there is nothing to
 * force (all polls are always-on by design). Diagnostics only: recording touches no
 * setting and the export carries no PII beyond what the dashboard already shows.
 */
@Singleton
class SessionRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metricsEngine: MetricsEngine
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var samplingJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _sampleCount = MutableStateFlow(0)
    val sampleCount: StateFlow<Int> = _sampleCount.asStateFlow()

    private val buffer = ArrayDeque<SessionSample>()

    /** Starts a fresh recording, discarding any previous buffer. */
    fun startRecording() {
        stopSampling()
        synchronized(buffer) { buffer.clear() }
        _sampleCount.value = 0
        _isRecording.value = true
        samplingJob = scope.launch {
            while (true) {
                val sample = sampleOf(metricsEngine.state.value, System.currentTimeMillis())
                synchronized(buffer) {
                    buffer.addLast(sample)
                    while (buffer.size > MAX_SAMPLES) buffer.removeFirst()
                    _sampleCount.value = buffer.size
                }
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    /** Stops sampling; the buffer survives for export until the next start. */
    fun stopRecording() {
        stopSampling()
        _isRecording.value = false
    }

    /**
     * Writes the buffered window to `filesDir/session_logs/` and returns the file, or null
     * when there is nothing to export.
     */
    suspend fun exportCsv(nowMs: Long = System.currentTimeMillis()): File? = withContext(Dispatchers.IO) {
        val samples: List<SessionSample>
        synchronized(buffer) { samples = buffer.toList() }
        if (samples.isEmpty()) return@withContext null
        val dir = File(context.filesDir, SESSION_DIR).apply { mkdirs() }
        val file = File(dir, SessionCsv.fileName(nowMs))
        file.bufferedWriter().use { writer ->
            writer.write(SessionCsv.header())
            writer.newLine()
            for (sample in samples) {
                writer.write(SessionCsv.row(sample))
                writer.newLine()
            }
        }
        prune(dir)
        file
    }

    private fun stopSampling() {
        samplingJob?.cancel()
        samplingJob = null
    }

    /** Keeps the newest exports; session files would otherwise grow one per tap forever. */
    private fun prune(dir: File) {
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_KEPT_FILES)
            ?.forEach { runCatching { it.delete() } }
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 1000L

        /** One hour at 1 Hz; each sample is a few dozen small fields. */
        const val MAX_SAMPLES = 3600

        private const val SESSION_DIR = "session_logs"
        private const val MAX_KEPT_FILES = 10
    }
}
