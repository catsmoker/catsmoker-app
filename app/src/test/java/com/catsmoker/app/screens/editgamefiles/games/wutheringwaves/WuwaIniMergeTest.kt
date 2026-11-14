package com.catsmoker.app.screens.editgamefiles.games.wutheringwaves

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WuwaIniMergeTest {
    @Test
    fun unrelatedSettingsCommentsAndArrayEntriesSurvive() {
        val live = "\uFEFF[Core.System]\r\nPaths=UserContent\r\n; keep this\r\n[SystemSettings]\r\nr.ShadowQuality=4\r\n" +
            "custom.Value=9\r\n[Other]\r\nUser=untouched\r\n"
        val generated = "[Core.System]\nPaths=GameContent\n[SystemSettings]\nr.ShadowQuality=1\nr.BloomQuality=0\n"
        val result = WuwaIniMerge.merge(live.toByteArray(), generated.toByteArray()).decodeToString()
        assertTrue(result.startsWith("\uFEFF[Core.System]\r\nPaths=UserContent\r\n; keep this\r\n"))
        assertTrue(result.contains("r.ShadowQuality=1\r\ncustom.Value=9\r\n[Other]\r\nUser=untouched\r\n"))
        assertTrue(result.contains("Paths=GameContent\r\n"))
        assertTrue(result.contains("r.BloomQuality=0\r\n"))
    }

    @Test
    fun cvarArraysAreMatchedByTheirOwnCvarNotByTheArrayName() {
        val live = "[Android DeviceProfile]\n+CVars=r.ShadowQuality=3\n+CVars=custom.Keep=8\n+DeviceProfileNameAndTypes=Mine,Android\n"
        val generated = "[Android DeviceProfile]\n+CVars=r.ShadowQuality=1\n+CVars=r.BloomQuality=0\n"
        val result = WuwaIniMerge.merge(live.toByteArray(), generated.toByteArray()).decodeToString()
        assertTrue(result.contains("+CVars=r.ShadowQuality=1\n+CVars=custom.Keep=8\n"))
        assertTrue(result.contains("+DeviceProfileNameAndTypes=Mine,Android\n"))
        assertEquals(1, Regex("r.ShadowQuality=").findAll(result).count())
    }

    @Test
    fun higherOrUnlimitedFpsIsNeverCappedByADeployment() {
        for (fps in listOf("240", "0", "-1")) {
            val live = "[SystemSettings]\nt.MaxFPS=$fps\n[Game]\nFrameRateLimit=$fps\n"
            val generated = "[SystemSettings]\nt.MaxFPS=120\n[Game]\nFrameRateLimit=120.000000\n"
            assertEquals(live, WuwaIniMerge.merge(live.toByteArray(), generated.toByteArray()).decodeToString())
        }
        val live = "[Game]\nFrameRateLimit=60\n"
        assertTrue(WuwaIniMerge.merge(live.toByteArray(), "[Game]\nFrameRateLimit=120\n".toByteArray())
            .decodeToString().contains("FrameRateLimit=120"))
    }

    @Test
    fun generatedConfigsForEveryPresetCanOverlayTheirOwnOutput() {
        for (preset in WuwaConfigGenerator.PRESET_ORDER) {
            val files = WuwaConfigGenerator.generate(preset, WuwaConfigGenerator.Options(),
                WuwaConfigGenerator.DeviceInfo(), null)
            for (text in listOf(files.engine, files.deviceProfiles, files.gameUserSettings)) {
                val bytes = text.toByteArray()
                assertTrue(WuwaIniMerge.merge(bytes, bytes).contentEquals(bytes))
            }
        }
    }

    @Test(expected = Exception::class)
    fun invalidUtf8IsRefusedInsteadOfCorrupted() {
        WuwaIniMerge.merge(byteArrayOf(0xC3.toByte(), 0x28), "[Game]\nValue=1\n".toByteArray())
    }
}
