package com.catsmoker.app.features.editgamefiles.wuwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for the generation report (M100 verify surfacing + M105 RT flags).
 *
 * The report compares the Engine.ini text before and after the forbidden-cvar
 * strip — the only transform between the two snapshots — so every rejected key
 * is a key the strip actually removed, never an invented count. Accepted keys
 * are what the final file carries; render-pipeline families (RT/Lumen, the
 * M105 desktop-class set) are flagged informationally, never auto-removed.
 */
class WuwaGenerationReportTest {

    @Test
    fun acceptedCountsFinalKeysAndRejectedListsStrippedOnes() {
        val pre = "[SystemSettings]\nr.BloomQuality=4\nr.Streaming.PoolSize=2048\nr.Fog=1"
        val post = "[SystemSettings]\nr.BloomQuality=4\nr.Fog=1"
        val report = WuwaGenerationReport.report(pre, post)
        assertEquals(2, report.accepted)
        assertEquals(listOf("r.streaming.poolsize"), report.rejected)
        assertTrue(report.rtFlagged.isEmpty())
    }

    @Test
    fun identicalTextsMeanNothingRejected() {
        val text = "[SystemSettings]\nr.BloomQuality=4"
        val report = WuwaGenerationReport.report(text, text)
        assertEquals(1, report.accepted)
        assertTrue(report.rejected.isEmpty())
    }

    @Test
    fun sectionsCommentsAndBlanksAreNotCvars() {
        val pre = "[SystemSettings]\n; comment\n\nr.Fog=1"
        val report = WuwaGenerationReport.report(pre, pre)
        assertEquals(1, report.accepted)
    }

    @Test
    fun renderPipelineFamiliesAreFlagged() {
        val text = "[SystemSettings]\nr.BloomQuality=4\nr.Lumen.GlobalIllumination=1\nr.RayTracing.Shadows=1"
        val report = WuwaGenerationReport.report(text, text)
        assertEquals(
            listOf("r.lumen.globalillumination", "r.raytracing.shadows"),
            report.rtFlagged
        )
    }

    @Test
    fun plusCvarsLinesCountByInnerKey() {
        val pre = "[SystemSettings]\n+CVars=r.BloomQuality=4"
        val report = WuwaGenerationReport.report(pre, pre)
        assertEquals(1, report.accepted)
    }
}
