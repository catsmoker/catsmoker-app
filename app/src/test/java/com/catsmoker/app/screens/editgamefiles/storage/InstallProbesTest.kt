package com.catsmoker.app.screens.editgamefiles.storage

import com.catsmoker.app.screens.editgamefiles.model.GameType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the picker's PUBG group-dot rule: any install = green; all six probed and none
 * installed = red; a missing probe anywhere = unknown, never a false red.
 */
class InstallProbesTest {

    private val variants = PUBG_VARIANTS

    @Test
    fun `pubg variants cover all six store packages including Flash`() {
        // PUBG Mobile Flash (com.tencent.igfit, formerly Lite) is the sixth variant.
        assertEquals(6, PUBG_VARIANTS.size)
        assertEquals(true, PUBG_VARIANTS.contains(GameType.PUBG_FLASH))
    }

    @Test
    fun `any installed variant shows green regardless of missing probes`() {
        // One installed, the rest unprobed — still green; "unknown" must not outvote a yes.
        assertEquals(true, pubgGroupDot(mapOf(GameType.PUBG_KRJP to true)))
        assertEquals(true, pubgGroupDot(mapOf(GameType.PUBG_GLOBAL to true, GameType.BGMI to false)))
        assertEquals(true, pubgGroupDot(variants.associateWith { false } + (GameType.PUBG_VN to true)))
    }

    @Test
    fun `all probed and none installed shows red`() {
        assertEquals(false, pubgGroupDot(variants.associateWith { false }))
    }

    @Test
    fun `empty map is unknown, not red`() {
        assertEquals(null, pubgGroupDot(emptyMap()))
    }

    @Test
    fun `one missing probe among all-absent makes the dot unknown`() {
        // Five probed and absent, one never answered — the sixth might still be installed.
        val five = variants.dropLast(1).associateWith { false }
        assertEquals(null, pubgGroupDot(five))
    }

    @Test
    fun `new state package is visible to the install probe`() {
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.isFile }?.readText() ?: error("AndroidManifest.xml not found")

        assertTrue(
            "Package visibility must allow New State to resolve as installed or missing",
            manifest.contains("""<package android:name="com.pubg.newstate" />""")
        )
    }
}
