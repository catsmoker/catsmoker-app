package com.catsmoker.app.features.gamingtools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Placement lock for the Gaming Mode card reorganisation.
 *
 * The keep list must live inside the Gaming Mode card (not under Background Apps), its ViewModel
 * and store wiring must survive the move, and the read-back report must be foldable without giving
 * the fold any way to start an activation. The tests are intentionally syntactic, matching the
 * project's existing UI-rule locks.
 */
class GamingModeUiPlacementTest {

    private val screen = source("GamingToolsScreen.kt")
    private val card = source("ui/GamingModeCard.kt")
    private val details = source("ui/GamingModeDetailsSection.kt")
    private val keep = source("ui/GamingModeKeepRunningSection.kt")
    private val progress = source("ui/GamingModeProgress.kt")
    private val viewModel = source("GamingToolsViewModel.kt")

    private fun source(relativePath: String): String {
        val file = File(
            "src/main/java/com/catsmoker/app/features/gamingtools",
            relativePath
        )
        assertTrue("expected source missing: $relativePath", file.isFile)
        return file.readText()
    }

    @Test
    fun keepRunningControlMovedIntoGamingModeCard() {
        assertTrue(card.contains("GamingModeKeepRunningSection("))
        assertTrue(card.contains("picked = keptPackages"))
        assertTrue(card.contains("onAdd = onAddKeptApp"))
        assertTrue(card.contains("onRemove = onRemoveKeptApp"))
        assertTrue(keep.contains("R.string.gt_keep_title"))
        assertTrue(keep.contains("R.string.gt_keep_sub"))
        assertTrue(keep.contains("R.string.gt_keep_add"))
        assertTrue(keep.contains("R.string.gt_keep_remove"))
    }

    @Test
    fun backgroundAppsNoLongerShowsADuplicateKeepCard() {
        assertFalse("GamingToolsScreen.kt still renders the old keep card", screen.contains("R.string.gt_keep_title"))
        assertFalse("GamingToolsScreen.kt still keeps the old keep composable", screen.contains("KeepListContent"))
        assertFalse("GamingToolsScreen.kt still describes the old keep card", screen.contains("R.string.gt_keep_sub"))
    }

    @Test
    fun keepRunningFeatureStaysRegisteredAfterTheMove() {
        assertTrue(screen.contains("keptPackages = uiState.suspendExemptPackages"))
        assertTrue(screen.contains("keepCandidateApps = uiState.suspendExemptCandidateApps"))
        assertTrue(screen.contains("onAddKeptApp = onShowSuspendExemptPicker"))
        assertTrue(screen.contains("onRemoveKeptApp = onRemoveSuspendExemptPackage"))
        // The picker dialog and the business logic are untouched.
        assertTrue(screen.contains("isPickingSuspendExemptPackage"))
        assertTrue(viewModel.contains("suspendExemptStore"))
        assertTrue(viewModel.contains("fun toggleSuspendExemptPackage"))
        assertTrue(viewModel.contains("fun removeSuspendExemptPackage"))
        assertTrue(viewModel.contains("fun showSuspendExemptPicker"))
    }

    @Test
    fun detailsFoldHasNoActivationCallback() {
        assertTrue(details.contains("fun GamingModeDetailsSection("))
        assertFalse("details must not receive an activation callback", details.contains("onActivate"))
        assertFalse("details must not receive a deactivation callback", details.contains("onDeactivate"))
        assertFalse("details must not call the engine", details.contains("toggleGamingMode"))
        assertFalse("details must not call the ViewModel activation", details.contains("activateGamingMode"))
        assertTrue(details.contains("expanded = GamingModeDetails.toggle(expanded)"))
        assertTrue(card.contains("GamingModeDetailsSection(report = report)"))
    }

    @Test
    fun errorsAndRefusalsStayOutsideTheFoldedSection() {
        assertFalse("the folded list must not own NoticeBlock", details.contains("NoticeBlock"))
        assertFalse("the folded list must not own refusal notices", details.contains("gt_gm_unavailable_title"))
        assertTrue(card.contains("gamingState is GamingModeState.Error"))
        assertTrue(card.contains("NoticeBlock(text = gamingState.message"))
        assertTrue(card.contains("R.string.gt_gm_unavailable_title"))
        assertTrue(card.contains("R.string.gt_gm_revert_leftovers"))
    }

    @Test
    fun progressBarIsRenderedOnlyDuringAnOperation() {
        assertTrue(card.contains("if (GamingModeProgress.isVisible(gamingState))"))
        assertTrue(card.contains("GamingModeProgressBar(state = gamingState)"))
        assertFalse("the card must not render the bar unconditionally", card.contains("SquigglyProgressBar("))
    }

    @Test
    fun forwardAndReverseProgressUseTheSameBar() {
        assertTrue(progress.contains("SquigglyProgressBar("))
        assertTrue(progress.contains("LaunchedEffect(isDisabling)"))
        assertTrue(progress.contains("snapTo(GamingModeProgress.REVERSE_START)"))
        assertTrue(progress.contains("animateTo("))
        assertTrue(progress.contains("GamingModeProgress.REVERSE_END"))
        assertTrue(progress.contains("tween("))
        assertTrue(progress.contains("durationMillis = GamingModeProgress.REVERSE_DURATION_MS"))
        assertTrue(progress.contains("GamingModeProgress.progressFor(state)"))
    }

    @Test
    fun progressAndSuccessFeedbackRemainOnTheCard() {
        assertTrue(card.contains("isBusy"))
        assertTrue(card.contains("R.string.gt_gm_active"))
        assertTrue(card.contains("R.string.gt_gm_ready"))
    }

    @Test
    fun keepListIsCountedAndCollapsedByDefault() {
        assertTrue(keep.contains("GamingModeKeepRunning.count(picked)"))
        assertTrue(keep.contains("GamingModeKeepRunning.DEFAULT_EXPANDED"))
        assertTrue(keep.contains("rememberSaveable"))
        assertTrue(keep.contains("GamingModeKeepRunning.toggle(expanded)"))
        assertTrue(keep.contains("R.string.gt_keep_show_list"))
        assertTrue(keep.contains("R.string.gt_keep_hide_list"))
        assertTrue(keep.contains("R.string.gt_keep_summary_more"))
        assertTrue(keep.contains("R.string.gt_keep_some"))
        // Folding is presentation-only: no store call, and the privilege gate stays on Add.
        assertFalse("folding must not touch the store", keep.contains("suspendExemptStore."))
        assertTrue(keep.contains("enabled = canEdit"))
        assertTrue(keep.contains("RequirementNotice("))
    }

    @Test
    fun expandedKeepListIsBoundedAndScrollable() {
        assertTrue(keep.contains("LazyColumn"))
        assertTrue(keep.contains("heightIn(max ="))
        assertTrue(keep.contains("onRemove(row.packageName)"))
        assertTrue(keep.contains("onClick = onAdd"))
    }

    @Test
    fun detailsStillRenderTheSameReadBackRows() {
        assertTrue(details.contains("report: GamingModeReport"))
        assertTrue(details.contains("gamingModeResultLines(report)"))
        listOf(
            "gt_gm_label_gpu",
            "gt_gm_label_fps_hint",
            "gt_gm_label_mtk",
            "gt_gm_label_snap",
            "gt_gm_label_input_boost",
            "gt_gm_label_governor",
            "gt_gm_label_spectrum",
            "gt_gm_label_readahead",
            "gt_gm_label_mem",
            "gt_gm_label_display",
            "gt_gm_label_touch",
            "gt_gm_label_bg_apps",
            "gt_gm_label_dnd",
            "gt_gm_label_notif",
            "gt_gm_label_net",
            "gt_gm_label_cap",
            "gt_gm_label_discard",
            "gt_gm_label_limit",
            "gt_gm_label_other_data"
        ).forEach { key ->
            assertTrue("GamingModeDetailsSection.kt lost $key", details.contains(key))
        }
    }
}
