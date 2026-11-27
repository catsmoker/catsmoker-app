package com.catsmoker.app.screens.editgamefiles.editor

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three long editors were one scrolling form each, which is how a preference changed at the top
 * needed five swipes to reach APPLY. These assertions pin the grouping that replaced it.
 *
 * Source guards are a weak instrument, and they are used here only because the alternative is a
 * Compose UI harness this project does not have. Each assertion names the concrete way the grouping
 * could silently regress, so tripping one reads as a question rather than as an obstacle.
 */
class EditorTabsTest {

    private fun editor(path: String): String =
        File("src/main/java/com/catsmoker/app/screens/editgamefiles/$path").readText()

    @Test
    fun eachEditorDeclaresItsOwnEnumSoIdsSurviveTranslation() {
        listOf(
            "games/grid/GridScreen.kt" to "GridEditorTab",
            "games/starrail/HsrGraphicsScreen.kt" to "HsrEditorTab",
            "games/wutheringwaves/WuwaConfigScreen.kt" to "WuwaEditorTab",
        ).forEach { (path, name) ->
            val source = editor(path)
            assertTrue(
                "$path declares its own tab enum",
                source.contains("private enum class $name(@StringRes val label: Int")
            )
            // rememberEditorTab keys the saved selection on the enum name, so a translation can
            // never reset the open tab; the specs are built from the same entries.
            assertTrue("$path builds specs from the enum", source.contains("entries.map {"))
            assertTrue("$path draws its row", source.contains(".row()"))
        }
    }

    @Test
    fun gridGroupsDisplayQualityAndSwitches() {
        val grid = editor("games/grid/GridScreen.kt")
        // Frame rate and resolution answer "how fast", the ladder answers "how does it look",
        // the switches are per-file booleans. All three groups must still be reachable.
        assertTrue(grid.contains("GridEditorTab.DISPLAY.name -> {"))
        assertTrue(grid.contains("GridEditorTab.QUALITY.name -> {"))
        assertTrue(grid.contains("SWITCHES(R.string.gf_tab_switches"))
        // The third group is the else branch: adding a fourth tab without a branch would leave it
        // showing the switches, so the fallback is deliberately the least surprising group.
        assertTrue(grid.contains("else -> {"))
    }

    @Test
    fun starRailKeepsNoTabRowInTheFlatFormat() {
        val hsr = editor("games/starrail/HsrGraphicsScreen.kt")
        // The flat file (live HSR >= 4.x) carries a quality tier and a resolution: two cards, not
        // three groups. A row there would offer two empty views.
        assertTrue(hsr.contains("if (!flat) hsrTab.row()"))
        // Cards the flat format still needs must stay outside the tab gates, or a flat user lands
        // on an empty editor.
        assertTrue(hsr.contains("if (flat || hsrTab.selected == HsrEditorTab.DISPLAY.name) {"))
    }

    @Test
    fun wutheringWavesReplacedItsMoreOptionsToggleWithATab() {
        val wuwa = editor("games/wutheringwaves/WuwaConfigScreen.kt")
        // The toggle and the tab answer the same question; keeping both would leave two ways to
        // reach the engine levers, one of them invisible.
        assertFalse(wuwa.contains("showAdvanced"))
        assertTrue(wuwa.contains("WuwaEditorTab.ADVANCED.name"))
        // Packs, backups, the installed profile and auto-tune are operations rather than settings,
        // so they sit behind TOOLS and the settings tabs stay about settings.
        assertTrue(wuwa.contains("WuwaEditorTab.TOOLS.name"))
        assertTrue(wuwa.contains("CommunityPacksCard("))
        assertTrue(wuwa.contains("AutoTuneCard("))
    }

    @Test
    fun wutheringWavesDeployCardHasOnePathToAWriteWhenEmbedded() {
        val wuwa = editor("games/wutheringwaves/WuwaConfigScreen.kt")
        // The pinned bar's sheet carries both channels. While the card also drew them, a deploy had
        // two starting points with different surrounding text. Standalone routes have no bar, so the
        // card keeps its buttons there — the same `onBack != null` test the inline fallback uses.
        val gate = wuwa.indexOf("if (onBack != null) {")
        val shellButton = wuwa.indexOf("onDeploy(WuwaDeployChannel.SHELL)")
        val safButton = wuwa.indexOf("onDeploy(WuwaDeployChannel.SAF)")
        assertTrue("the shell button sits inside the standalone gate", gate in 0 until shellButton)
        assertTrue("the SAF button sits inside the standalone gate", gate in 0 until safButton)
        // The explanation is documentation on the one card visible from every tab: collapsed rather
        // than deleted, so the words stay one tap away.
        assertTrue(wuwa.contains("R.string.gf_wuwa_deploy_what"))
        assertTrue(wuwa.contains("CollapsibleExplainer("))
    }

    @Test
    fun switchingViewReturnsToTheTop() {
        // Both scroll owners publish the seam: the destination for embedded editors, the layout
        // for standalone routes. A tab switch that kept the old offset landed mid-card.
        val tabs = File("src/main/java/com/catsmoker/app/screens/editgamefiles/editor/EditorTabs.kt").readText()
        assertTrue(tabs.contains("val LocalEditorScrollToTop"))
        assertTrue(tabs.contains("scrollToTop()"))
        val layout = File("src/main/java/com/catsmoker/app/screens/editgamefiles/editor/GameEditorLayout.kt").readText()
        assertTrue(layout.contains("LocalEditorScrollToTop provides"))
    }
}
