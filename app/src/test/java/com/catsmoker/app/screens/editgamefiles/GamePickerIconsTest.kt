package com.catsmoker.app.screens.editgamefiles

import com.catsmoker.app.screens.editgamefiles.games.genshin.GENSHIN_PACKAGE
import com.catsmoker.app.screens.editgamefiles.games.genshin.buildGenshinConfig
import com.catsmoker.app.screens.editgamefiles.games.starrail.HsrGameManager
import com.catsmoker.app.screens.editgamefiles.model.GameConfig
import com.catsmoker.app.screens.editgamefiles.model.GameType
import com.catsmoker.app.screens.editgamefiles.storage.PUBG_VARIANTS
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class GamePickerIconsTest {
    @Test
    fun `every real game has bounded distinct candidates but placeholder has none`() {
        assertTrue(pickerIconPackages(GameType.NONE).isEmpty())
        GameType.entries.filter { it != GameType.NONE }.forEach { game ->
            val candidates = pickerIconPackages(game)
            assertTrue(game.name, candidates.isNotEmpty())
            assertTrue(game.name, candidates.size <= PUBG_VARIANTS.size)
            assertEquals(game.name, candidates.distinct(), candidates)
        }
    }

    @Test
    fun `pubg icon identities match all six file destinations in picker order`() {
        assertEquals(PUBG_VARIANTS, PUBG_PACKAGES.keys.toList())
        val configs = mutableMapOf<GameType, GameConfig>()
        populateGameConfigs(configs)
        PUBG_PACKAGES.forEach { (game, packageName) ->
            assertEquals(packageName, configs.getValue(game).packageName)
            assertEquals(packageName, pickerIconPackages(game).first())
        }
    }

    @Test
    fun `selected installed pubg variant wins when global is also installed`() {
        val installed = mapOf(GameType.PUBG_GLOBAL to true, GameType.BGMI to true)
        assertEquals("com.pubg.imobile", pickerIconPackages(GameType.BGMI, installed).first())
        assertEquals("com.tencent.ig", pickerIconPackages(GameType.PUBG_GLOBAL, installed).first())
    }

    @Test
    fun `another installed pubg variant precedes missing selected global`() {
        val installed = mapOf(GameType.PUBG_GLOBAL to false, GameType.PUBG_KRJP to true)
        val candidates = pickerIconPackages(GameType.PUBG_GLOBAL, installed)
        assertEquals("com.pubg.krmobile", candidates.first())
        assertEquals(PUBG_PACKAGES.values.toSet(), candidates.toSet())
        assertEquals(mapOf(GameType.PUBG_GLOBAL to false, GameType.PUBG_KRJP to true), installed)
    }

    @Test
    fun `unknown and absent probes retain deterministic fallback order`() {
        val expected = listOf("com.tencent.igfit") + PUBG_PACKAGES.values.filter { it != "com.tencent.igfit" }
        assertEquals(expected, pickerIconPackages(GameType.PUBG_FLASH))
        assertEquals(expected, pickerIconPackages(GameType.PUBG_FLASH, PUBG_VARIANTS.associateWith { false }))
    }

    @Test
    fun `new state does not borrow a pubg mobile icon`() {
        assertEquals(listOf("com.pubg.newstate"), pickerIconPackages(GameType.PUBG_NEW_STATE))
        assertFalse("com.pubg.newstate" in PUBG_PACKAGES.values)
    }

    @Test
    fun `star rail icons reuse detection variants without changing regional order`() {
        assertEquals(HsrGameManager.KNOWN_PACKAGES.map { it.first }, pickerIconPackages(GameType.HSR))
        assertEquals(listOf(
            "com.HoYoverse.hkrpgoversea", "com.miHoYo.hkrpg", "com.HoYoverse.hkrpg",
            "com.cognosphere.hkrpg", "com.HoYoverse.hkrpgsamsung"
        ), pickerIconPackages(GameType.HSR))
    }

    @Test
    fun `single-package icons keep their existing editor identities`() {
        assertEquals(listOf(GENSHIN_PACKAGE), pickerIconPackages(GameType.GENSHIN_IMPACT))
        val genshin = buildGenshinConfig()
        assertEquals(GENSHIN_PACKAGE, genshin.packageName)
        assertEquals("/Android/data/com.miHoYo.GenshinImpact/files/", genshin.saveDir)
        assertEquals(listOf("com.kurogame.wutheringwaves.global"), pickerIconPackages(GameType.WUWA))
        assertEquals(listOf("com.feralinteractive.gridas"), pickerIconPackages(GameType.GRID))
        assertEquals(listOf("com.roblox.client"), pickerIconPackages(GameType.ROBLOX))
    }

    @Test
    fun `all icon candidates already have explicit package visibility`() {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml")).getElementsByTagName("package")
        val packages = (0 until nodes.length).map {
            nodes.item(it).attributes.getNamedItem("android:name").nodeValue
        }.toSet()
        GameType.entries.forEach { game ->
            assertTrue(game.name, packages.containsAll(pickerIconPackages(game)))
        }
    }

    @Test
    fun `picker retains scrolling dots and selection while icon work stays off main`() {
        val root = "src/main/java/com/catsmoker/app/screens/editgamefiles/"
        val selector = File(root + "GameSelector.kt").readText()
        assertTrue(selector.contains("verticalScroll(rememberScrollState()).selectableGroup()"))
        assertTrue(selector.contains("pubgGroupDot(installedGames)"))
        assertTrue(selector.contains("role = Role.RadioButton"))
        val row = selector.substringAfter("internal fun PickerRow(")
        assertTrue(row.indexOf("GamePickerIcon(") < row.indexOf("InstallDot(installed = installed)"))
        assertTrue(row.contains("Icons.Default.Check"))
        val icons = File(root + "GamePickerIcons.kt").readText()
        assertTrue(icons.contains("produceState<ImageBitmap?>"))
        assertTrue(icons.contains("withContext(Dispatchers.IO)"))
        assertTrue(icons.contains("Icons.Default.SportsEsports"))
        assertFalse(icons.contains("getInstalledApplications"))
        assertFalse(icons.contains("https://"))
    }
}
