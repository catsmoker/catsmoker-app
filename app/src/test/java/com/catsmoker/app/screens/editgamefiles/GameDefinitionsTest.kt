package com.catsmoker.app.screens.editgamefiles

import com.catsmoker.app.screens.editgamefiles.model.ConfigControl
import com.catsmoker.app.screens.editgamefiles.model.GameType
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class GameDefinitionsTest {
    @Test fun `every real game explicitly declares routing identity capabilities and limitations`() {
        assertEquals(33, fileEngineeringGames.size)
        assertEquals(GameType.entries.filter { it != GameType.NONE }.toSet(), fileEngineeringGames.keys)
        val manifest = File("src/main/AndroidManifest.xml").readText()
        fileEngineeringGames.forEach { (game, definition) ->
            assertEquals(game, definition.game)
            assertTrue(definition.packages.isNotEmpty())
            assertEquals(definition.packages.size, definition.packages.distinct().size)
            assertTrue(definition.storePackage in definition.packages)
            assertTrue(definition.capabilities.isNotEmpty())
            assertTrue(definition.access.isNotEmpty())
            assertTrue(definition.limitation.isNotBlank())
            definition.packages.forEach { assertTrue(manifest.contains("android:name=\"$it\"")) }
            if (definition.implementation == FileEngineeringImplementation.SCOPED_CONFIG) {
                val editor = additionalGameEditors.getValue(game)
                assertEquals(listOf(editor.packageName), definition.packages)
                assertTrue(editor.files.flatMap { it.fields }.any { it.control != ConfigControl.Unsupported })
            }
        }
        assertEquals(FileEngineeringReadiness.BLOCKED, fileEngineeringGames.getValue(GameType.ROBLOX).readiness)
    }

    @Test fun `installed missing unknown and show anyway share the same rule for every game`() {
        fileEngineeringGames.keys.forEach { game ->
            assertTrue(showFileEngineeringTools(game, true, false))
            assertFalse(showFileEngineeringTools(game, false, false))
            assertTrue(showFileEngineeringTools(game, false, true))
            assertTrue(showFileEngineeringTools(game, null, false))
        }
        assertFalse(showFileEngineeringTools(GameType.NONE, true, true))
    }

    @Test fun `fresh variant probe picks actual supported package without treating query failures as absence`() {
        val hsr = fileEngineeringGames.getValue(GameType.HSR)
        val installed = hsr.packages.last()
        assertEquals(GameInstallation(installed, true), hsr.probeInstallation { it == installed })
        assertEquals(GameInstallation(null, false), hsr.probeInstallation { false })
        assertEquals(GameInstallation(null, null), hsr.probeInstallation { if (it == installed) null else false })
        var installedNow = false
        val minecraft = fileEngineeringGames.getValue(GameType.MINECRAFT)
        assertEquals(false, minecraft.probeInstallation { installedNow }.installed)
        installedNow = true
        assertEquals(minecraft.storePackage, minecraft.probeInstallation { installedNow }.installedPackage)
        // Store fallback stays on the selected game's declared package, never another game.
        assertEquals("com.mojang.minecraftpe", minecraft.storePackage)
        assertEquals("com.aspyr.civvi", fileEngineeringGames.getValue(GameType.CIVILIZATION_VI).storePackage)
    }

    @Test fun `all implementation routes sit behind the universal gate and missing actions stay shared`() {
        val root = "src/main/java/com/catsmoker/app/screens/editgamefiles/"
        val screen = File(root + "EditGameFilesScreen.kt").readText()
        assertTrue(screen.indexOf("showFileEngineeringTools(") < screen.indexOf("when (definition.implementation)"))
        assertTrue(screen.contains("packageName = definition.storePackage"))
        assertTrue(screen.contains("onShowAnyway = onShowAnyway"))
        val vm = File(root + "EditGameFilesViewModel.kt").readText()
        assertTrue(vm.contains("installProbeJob?.cancel()"))
        assertTrue(vm.contains("checkingInstalls = true"))
        assertTrue(vm.contains("market://details?id=\$packageName"))
        assertTrue(vm.contains("https://play.google.com/store/apps/details?id=\$packageName"))
        assertTrue(vm.contains("fe_store_unavailable"))
    }
}
