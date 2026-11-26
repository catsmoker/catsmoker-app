package com.catsmoker.app.screens.gaming

import com.catsmoker.app.screens.gaming.automation.automationExample
import com.catsmoker.app.screens.gaming.cache.CacheUiReport
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class GamingToolsContextUxTest {
    private fun source(relative: String) = File("src/main/java/com/catsmoker/app/screens/gaming/$relative").readText()

    @Test fun selectionDiscoveryAndGlobalControlAreReadOnlyContextChanges() {
        val vm = source("GamingToolsViewModel.kt")
        val context = vm.substringAfter("fun toggleGameSelection(").substringBefore("private fun rememberResolutionInputs")
        listOf("execSafe", "executeResolution", "activateGamingMode", "deactivateGamingMode", ".preload(").forEach {
            assertFalse(it, context.contains(it))
        }
        val discovery = vm.substringAfter("fun syncGames(").substringBefore("fun toggleGameSelection(")
        assertTrue(discovery.contains("gameSettingsStore.reconcile("))
        assertFalse(discovery.contains("games.firstOrNull()"))
        val screen = source("GamingToolsScreen.kt")
        assertTrue(screen.contains("viewModel::toggleGameSelection"))
        assertTrue(screen.contains("viewModel.selectGame(null)"))
        assertTrue(screen.contains("GameLibraryOrdering.preview("))
        assertTrue(screen.contains("showGameTuning = uiState.selectedGamePkg != null"))
        val execution = source("automation/GameAutomation.kt").substringAfter("private suspend fun runRequest(")
        assertFalse(execution.contains("settings.select("))
        assertTrue(execution.contains("settings.read(it)"))
        assertTrue(execution.contains("jobs.activate(pkg)"))
    }

    @Test fun cacheReportsAreScopedAndDoNotInventAnotherGamesSuccess() {
        val raw = """{"game":"com.game.a","result":"byte_budget","bytesRead":8388608,"elapsedMs":18,"files":[{"result":"read"},{"result":"absent_or_inaccessible"}]}"""
        assertNull(CacheUiReport.forGame(raw, "com.game.b"))
        val report = CacheUiReport.forGame(raw, "com.game.a")!!
        assertEquals(8388608L, report.bytes)
        assertEquals(18L, report.elapsedMs)
        assertEquals(1, report.skipped)
        assertEquals("byte_budget", report.result)
        assertNull(CacheUiReport.forGame("broken", "com.game.a"))
    }

    @Test fun advancedExamplesUseRealContractAndPlaceholderNotAnAccessToken() {
        val example = automationExample(2, "com.game.a")
        assertTrue(example.contains("com.catsmoker.app.GAME_AUTOMATION"))
        assertTrue(example.contains("GameAutomationReceiver"))
        assertTrue(example.contains("--es token TOKEN"))
        assertTrue(example.contains("--es game com.game.a"))
        assertFalse(example.substringAfter("--es operation RESTORE").contains("--es game"))
        assertTrue(automationExample(0, "com.game.a").contains("operation: APPLY_SAVED"))
    }

    @Test fun tokenInvalidationRequiresConfirmationAndDiagnosticUiDoesNotRevealKey() {
        val automation = source("automation/AutomationDialog.kt")
        assertTrue(automation.contains("confirm = \"revoke\""))
        assertTrue(automation.contains("confirm = \"regenerate\""))
        assertTrue(automation.contains("gt_automation_invalidate_warning"))
        assertFalse(automation.contains("Text(access.token"))
        assertTrue(automation.contains("ClipDescription.EXTRA_IS_SENSITIVE"))
        assertTrue(automation.contains("QUERY_STATUS"))
        assertTrue(automation.contains("Dialog(onDismissRequest"))
        assertTrue(automation.contains("example.substringBefore('\\n')"))
        val cache = source("cache/GameCacheControls.kt")
        assertTrue(cache.contains("CacheUiReport.forGame(status, pkg)"))
        assertTrue(cache.contains("store.update(target)"))
        assertTrue(cache.contains("report?.result == \"running\""))
    }

    @Test fun automationUsesExistingHeaderAndCacheRemainsInTheProfile() {
        val screen = source("GamingToolsScreen.kt")
        assertEquals(1, Regex("AutomationEntry\\(").findAll(screen).count())
        assertTrue(screen.substringAfter("trailingContent = {").substringBefore(") {")
            .contains("AutomationEntry("))
        val profile = screen.substringAfter("val profile:").substringBefore("val performance:")
        assertTrue(profile.contains("cache.GameCacheControls("))
        assertTrue(screen.contains("sectionStates.SaveableStateProvider(section.name)"))
        val automation = source("automation/AutomationDialog.kt").substringAfter("fun AutomationEntry(")
            .substringBefore("fun AutomationDialog(")
        assertTrue(automation.contains("TextButton("))
        assertTrue(automation.contains("min = 48.dp"))
        val cache = source("cache/GameCacheControls.kt")
        assertTrue(cache.contains("ExpandableToolCard("))
        assertFalse(cache.contains("GamingToolSheet("))
        assertFalse(cache.contains("GamingToolEntry("))
    }
}
