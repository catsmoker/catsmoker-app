package com.catsmoker.app.screens.editgamefiles.editor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the seam that lets an embedded editor publish its primary action into a bar pinned
 * outside the screen's scroll container.
 *
 * The editor is composed inside File Engineering's single `verticalScroll`, so it cannot draw a
 * pinned bar itself, and the host cannot invent the apply action because it lives in the
 * per-game ViewModel. `DeployHost` is the seam: the editor publishes data, the host renders the
 * bar. Two properties make that safe and are asserted here — publishing must be idempotent (or
 * host and bar wake each other every frame), and an editor leaving the tree must clear the bar
 * (or a stale APPLY for the previous game stays pressable).
 */
class DeployBarTest {

    private val bar = File(
        "src/main/java/com/catsmoker/app/screens/editgamefiles/editor/DeployBar.kt"
    ).readText()

    private val screen = File(
        "src/main/java/com/catsmoker/app/screens/editgamefiles/EditGameFilesScreen.kt"
    ).readText()

    private fun editor(path: String) = File("src/main/java/com/catsmoker/app/screens/editgamefiles/$path").readText()

    @Test
    fun publishingIsIdempotent() {
        assertTrue("the host must compare before writing", bar.contains("if (this.view != view) this.view = view"))
        // Views must stay comparable: callbacks live beside the state, not inside it.
        assertTrue(bar.contains("data class DeployView("))
        assertFalse(
            "callbacks inside DeployView would make every frame a new value",
            bar.substringAfter("data class DeployView(").substringBefore(")").contains("-> Unit")
        )
        assertTrue(bar.contains("class DeployActions("))
    }

    @Test
    fun leavingTheTreeClearsTheBar() {
        assertTrue(bar.contains("onDispose { host.publish(null, null) }"))
        assertTrue(bar.contains("val view = host.view ?: return"))
    }

    @Test
    fun barIsDrawnOutsideTheScrollContainer() {
        assertTrue("the screen provides the host", screen.contains("CompositionLocalProvider(LocalDeployHost provides deployHost)"))
        assertTrue(screen.contains("val deployHost = remember { DeployHost() }"))
        // The bar is a sibling of the scrolling column, not a child of it: a bar inside the
        // scroll would simply scroll away.
        val body = screen.substringAfter("fun body(modifier: Modifier")
        val scrollColumn = body.indexOf(".verticalScroll(")
        val barCall = body.indexOf("DeployBar(deployHost)")
        assertTrue("the bar must come after the scrolled column", barCall > scrollColumn && scrollColumn >= 0)
        // The same owner publishes "back to the top": an editor that switches view cannot scroll
        // the destination itself, and carrying the old offset into a shorter view lands mid-card.
        assertTrue(screen.contains("LocalEditorScrollToTop provides"))
    }

    @Test
    fun editorsWithOnePrimaryWritePublishIt() {
        listOf(
            "games/starrail/HsrGraphicsScreen.kt",
            "games/grid/GridScreen.kt"
        ).forEach { path ->
            val source = editor(path)
            assertTrue("$path must publish its deploy state", source.contains("HostedDeploy("))
            // Standalone rendering must survive: the inline block is the fallback, not dead code.
            assertTrue("$path must keep its inline deploy block", source.contains("inline = {"))
            assertTrue("$path must pass undo/redo or apply through DeployActions", source.contains("DeployActions("))
        }
    }

    @Test
    fun multiTargetEditorReachesItsChannelsThroughASheet() {
        // Wuthering Waves deploys to more than one target (shell, SAF, variant, stripped), so the
        // bar cannot carry "the" apply button. It carries one DEPLOY that opens a sheet with the
        // channels, and the card stays as the place that explains what a deploy does. This pins
        // both halves so neither drifts: bar present, sheet present, card still there.
        val wuwa = editor("games/wutheringwaves/WuwaConfigScreen.kt")
        assertTrue(wuwa.contains("HostedDeploy("))
        assertTrue(wuwa.contains("WuwaDeploySheet("))
        assertTrue(wuwa.contains("R.string.gf_deploy_shell"))
        // The bar must open the sheet rather than firing a deploy directly: a bar button that
        // silently picked a channel would be choosing for the user.
        assertTrue(wuwa.contains("actions = DeployActions(apply = { showDeploySheet = true })"))
    }

    @Test
    fun restoreIsNotSweptIntoTheBar() {
        // Restore is the way back from a write, not another way to write: it stays in the flow
        // where the user can still find it after an apply.
        listOf("games/starrail/HsrGraphicsScreen.kt", "games/grid/GridScreen.kt").forEach { path ->
            val source = editor(path)
            val deploy = source.indexOf("HostedDeploy(")
            val restore = source.indexOf("GameEditorSection(stringResource(R.string.gf_restore_backup))")
            assertTrue("$path must keep a restore card", restore >= 0)
            assertTrue("$path must not hide restore inside the bar's fallback", restore > deploy)
        }
    }
}
