package com.catsmoker.app.features.gamingtools.ui

import com.catsmoker.app.features.gamingtools.tools.GamingModeAdvancedStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The "Advanced" fold that now owns both Gaming Mode app lists.
 *
 * Two product rules are pinned here. First, the group is closed until the user asks for it, and that
 * choice is remembered across screen changes and restarts — a fresh install therefore never shows
 * the two pickers. Second, folding is presentation-only: the pickers keep their own state and
 * callbacks, so opening or closing the group can never edit a stored package list.
 */
class GamingModeAdvancedTest {

    private val card = source("ui/GamingModeCard.kt")
    private val section = source("ui/GamingModeAdvancedSection.kt")
    private val screen = source("GamingToolsScreen.kt")
    private val viewModel = source("GamingToolsViewModel.kt")
    private val store = source("tools/GamingModeAdvancedStore.kt")

    private fun source(relativePath: String): String {
        val file = File(
            "src/main/java/com/catsmoker/app/features/gamingtools",
            relativePath
        )
        assertTrue("expected source missing: $relativePath", file.isFile)
        return file.readText()
    }

    @Test
    fun startsCollapsed() {
        assertFalse(GamingModeAdvanced.DEFAULT_EXPANDED)
        assertFalse(GamingModeAdvancedStore.DEFAULT_EXPANDED)
    }

    @Test
    fun toggleFlipsTheDisclosure() {
        val opened = GamingModeAdvanced.toggle(GamingModeAdvanced.DEFAULT_EXPANDED)
        assertTrue(opened)
        assertFalse(GamingModeAdvanced.toggle(opened))
    }

    @Test
    fun chevronIsAHalfTurnWhenExpanded() {
        assertEquals(0f, GamingModeAdvanced.chevronDegrees(expanded = false), 0.001f)
        assertEquals(180f, GamingModeAdvanced.chevronDegrees(expanded = true), 0.001f)
    }

    @Test
    fun animationLastsAboutTwoHundredMilliseconds() {
        assertEquals(200, GamingModeAdvanced.DURATION_MS)
    }

    @Test
    fun bothAppPickersLiveInsideTheAdvancedGroup() {
        val group = card.indexOf("GamingModeAdvancedSection(")
        assertTrue("the card must render the Advanced group", group >= 0)
        assertTrue(
            "Apps kept running must sit inside the Advanced group",
            card.indexOf("GamingModeKeepRunningSection(") > group
        )
        assertTrue(
            "Extra suspended apps must sit inside the Advanced group",
            card.indexOf("GamingModeSuspensionSection(") > group
        )
        // Moving them must not rewire them: the same data and callbacks travel through.
        assertTrue(card.contains("picked = keptPackages"))
        assertTrue(card.contains("onAdd = onAddKeptApp"))
        assertTrue(card.contains("onRemove = onRemoveKeptApp"))
        assertTrue(card.contains("picked = extraSuspendPackages"))
        assertTrue(card.contains("suspended = suspendedPackages"))
        assertTrue(card.contains("onAdd = onAddExtraSuspend"))
        assertTrue(card.contains("onRemove = onRemoveExtraSuspend"))
    }

    @Test
    fun headerIsATappableRowWithRotatingChevronAndAnimation() {
        assertTrue(section.contains("fun GamingModeAdvancedSection("))
        assertTrue(section.contains("clickable("))
        assertTrue(section.contains("animateFloatAsState("))
        assertTrue(section.contains("tween(durationMillis = GamingModeAdvanced.DURATION_MS)"))
        assertTrue(section.contains("GamingModeAdvanced.chevronDegrees("))
        assertTrue(section.contains("rotate("))
        assertTrue(section.contains("AnimatedVisibility("))
        assertTrue(section.contains("R.string.gt_advanced_title"))
    }

    @Test
    fun headerAnnouncesExpandAndCollapse() {
        assertTrue(section.contains("R.string.gt_advanced_expand"))
        assertTrue(section.contains("R.string.gt_advanced_collapse"))
        assertTrue(section.contains("contentDescription"))
    }

    @Test
    fun expandedStateIsPersistedAndReadByTheScreen() {
        assertTrue(store.contains("getSharedPreferences("))
        assertTrue(store.contains("putBoolean("))
        assertTrue(store.contains("DEFAULT_EXPANDED"))
        assertTrue(viewModel.contains("gamingModeAdvancedStore"))
        assertTrue(viewModel.contains("isGamingModeAdvancedExpanded"))
        assertTrue(viewModel.contains("fun toggleGamingModeAdvanced()"))
        assertTrue(screen.contains("advancedExpanded = uiState.isGamingModeAdvancedExpanded"))
        assertTrue(screen.contains("onToggleGamingModeAdvanced = viewModel::toggleGamingModeAdvanced"))
        assertTrue(screen.contains("onToggleAdvanced = onToggleGamingModeAdvanced"))
    }
}
