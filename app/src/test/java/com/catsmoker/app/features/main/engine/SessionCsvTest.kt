package com.catsmoker.app.features.main.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the session-CSV shape against the reference `SessionLogger` 19-column format
 * (`reference/gamingtools/booster/.../metrics/SessionLogger.kt`, read before this test was
 * written): identical header so files stay comparable, app-mapped values, empty cells where
 * this app has no signal (total CPU MHz — the clusters carry the facts instead).
 */
class SessionCsvTest {

    private fun sample() = SessionSample(
        timestampMs = 1_700_000_000_000L,
        fps = 60,
        jankyFrames = 3,
        cpuPercent = 25,
        clusterEffMhz = 1700,
        clusterPerfMhz = 2400,
        clusterUltraMhz = null,
        ramUsedGb = 2.5f,
        ramTotalGb = 4f,
        batteryTempC = 33.5f,
        networkRxKbps = 120f,
        networkTxKbps = 45f,
        pingMs = 38,
        thermalCpuC = 47.7f,
        thermalGpuC = 47.7f,
        thermalNpuC = null,
        thermalSkinC = 33f,
        thermalStatus = 0,
        topProcessName = "com.game",
        topProcessCpuPercent = 12.5f
    )

    @Test
    fun headerMatchesTheReferenceShape() {
        // The reference calls this "19-col" but names 21 comma-separated fields — pin the
        // actual contract (count + order + names), not the comment.
        val header = SessionCsv.header()
        assertEquals(21, header.split(",").size)
        assertEquals(
            "timestamp,fps,janky_frames,cpu_mhz,cpu_percent,cpu_cluster_eff_mhz," +
                "cpu_cluster_perf_mhz,cpu_cluster_ultra_mhz,ram_used_gb,ram_total_gb," +
                "battery_temp_c,network_rx_kbps,network_tx_kbps,ping_ms,thermal_cpu_c," +
                "thermal_gpu_c,thermal_npu_c,thermal_skin_c,thermal_status," +
                "top_process,top_process_cpu_percent",
            header
        )
    }

    @Test
    fun rowMapsEveryField() {
        val row = SessionCsv.row(sample()).split(",")
        assertEquals(21, row.size)
        assertEquals("60", row[1])
        assertEquals("3", row[2])
        assertEquals("", row[3]) // no total-MHz signal; clusters carry it
        assertEquals("25", row[4])
        assertEquals("1700", row[5])
        assertEquals("2400", row[6])
        assertEquals("", row[7]) // unplaced ultra tier
        assertEquals("com.game", row[19])
    }

    @Test
    fun nullsRenderEmptyNeverZero() {
        val row = SessionCsv.row(sample().copy(fps = null, pingMs = null)).split(",")
        assertEquals("", row[1])
        assertEquals("", row[13])
    }

    @Test
    fun timestampIsHumanReadable() {
        val row = SessionCsv.row(sample()).split(",")
        assertTrue(row[0].matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun documentNamesAreTimestamped() {
        val name = SessionCsv.fileName(1_700_000_000_000L)
        assertTrue(name.startsWith("catsmoker_session_"))
        assertTrue(name.endsWith(".csv"))
    }
}
