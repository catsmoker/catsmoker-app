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
    private val suspension = source("ui/GamingModeSuspensionSection.kt")
    private val picker = source("ui/GamingModeAppPickerSection.kt")
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
        assertTrue(keep.contains("GamingModeAppPicker.count(picked)"))
        assertTrue(keep.contains("GamingModeAppPicker.DEFAULT_EXPANDED"))
        assertTrue(keep.contains("rememberSaveable"))
        assertTrue(keep.contains("GamingModeAppPicker.toggle(expanded)"))
        assertTrue(keep.contains("R.string.gt_keep_show_list"))
        assertTrue(keep.contains("R.string.gt_keep_hide_list"))
        assertTrue(picker.contains("R.string.gt_keep_summary_more"))
        assertTrue(keep.contains("R.string.gt_keep_some"))
        // Folding is presentation-only: no store call, and the privilege gate stays on Add.
        assertFalse("folding must not touch the store", keep.contains("suspendExemptStore."))
        assertTrue(picker.contains("enabled = canEdit"))
        assertTrue(picker.contains("RequirementNotice("))
    }

    @Test
    fun bothAppPickersUseTheSameSectionLayout() {
        // One app list is pointless twice: both sections render through the shared picker, so a
        // future layout change cannot make one list look different from the other.
        assertTrue(picker.contains("fun GamingModeAppPickerSection("))
        assertTrue(keep.contains("GamingModeAppPickerSection("))
        assertTrue(suspension.contains("GamingModeAppPickerSection("))
        assertTrue(picker.contains("CatsmokerOutlinedButton("))
        assertTrue(picker.contains("InfoButton(info)"))
        assertTrue(suspension.contains("GamingModeAppPicker.count(picked)"))
        assertTrue(suspension.contains("GamingModeAppPicker.DEFAULT_EXPANDED"))
        assertTrue(suspension.contains("R.string.gt_suspend_none"))
        assertTrue(suspension.contains("R.string.gt_suspend_some"))
        assertTrue("the freeze list must point at its own info topic", suspension.contains("InfoTopics.extraSuspend"))
    }

    @Test
    fun appPickerQuestionsLiveInTheInfoCatalog() {
        // The Q&A left the card body; the wording still has to exist, exactly once, in the catalog.
        val catalog = source("ui/InfoTopics.kt")
        assertTrue(catalog.contains("R.string.gt_keep_what_1"))
        assertTrue(catalog.contains("R.string.gt_keep_what_2"))
        assertTrue(catalog.contains("R.string.gt_suspend_what_1"))
        assertTrue(catalog.contains("R.string.gt_suspend_what_2"))
        assertTrue(keep.contains("InfoTopics.keepRunning"))
        assertFalse("the picker must not render an inline explainer", picker.contains("CollapsibleExplainer("))
    }

    @Test
    fun expandedAppListsAreBoundedAndScrollable() {
        assertTrue(picker.contains("LazyColumn"))
        assertTrue(picker.contains("heightIn(max ="))
        assertTrue(picker.contains("onRemove(row.packageName)"))
        assertTrue(picker.contains("onClick = onAdd"))
    }

    @Test
    fun extraSuspendListMovedIntoGamingModeCard() {
        // The extra freeze list is the suspend sweep's second input: it has to sit with the
        // automatic sweep's exceptions, not under Background Apps where it read as a separate
        // feature and became a second editor of the same store.
        assertTrue(card.contains("GamingModeSuspensionSection("))
        assertTrue(card.contains("picked = extraSuspendPackages"))
        assertTrue(card.contains("suspended = suspendedPackages"))
        assertTrue(card.contains("onAdd = onAddExtraSuspend"))
        assertTrue(card.contains("onRemove = onRemoveExtraSuspend"))
        assertFalse(
            "GamingToolsScreen.kt still renders the old standalone freeze card",
            screen.contains("R.string.gt_suspend_title")
        )
        assertFalse(
            "GamingToolsScreen.kt still keeps the old freeze composable",
            screen.contains("SuspendListContent")
        )
        // The feature is only moved: its picker, store and actions stay wired.
        assertTrue(screen.contains("isPickingSuspendPackage"))
        assertTrue(screen.contains("onShowSuspendPicker"))
        assertTrue(screen.contains("onRemoveExtraSuspendPackage"))
        assertTrue(viewModel.contains("suspendListStore"))
    }

    @Test
    fun suspensionSectionDistinguishesConfiguredFromActuallyFrozen() {
        // A pick only becomes "frozen" when this session's pm suspend was verified. The badge must
        // read the confirmed set, not the configured one, or a configured app looks frozen while a
        // device-level freeze looks like this app's doing.
        assertTrue(suspension.contains("picked.count { it in suspended }"))
        assertTrue(suspension.contains("R.string.gt_suspend_badge_frozen"))
        assertTrue(suspension.contains("R.string.gt_suspend_badge_selected"))
        assertTrue(suspension.contains("R.string.gt_suspend_count"))
        assertFalse(
            "the section must not read the configured list as the frozen truth",
            suspension.contains("val frozenCount = remember(picked) { picked.size }")
        )
    }

    @Test
    fun restoreAppsLivesInTheCardAndOnlyWhenNoSessionRuns() {
        assertTrue(card.contains("onClick = onRestoreApps"))
        assertTrue(card.contains("R.string.gt_gm_restore"))
        assertTrue(card.contains("R.string.gt_gm_restore_done"))
        assertTrue(card.contains("R.string.gt_gm_restore_partial"))
        assertTrue(card.contains("R.string.gt_gm_restore_unverified"))
        // Recovery must never be offered while a session is active: that would undo the suspends
        // the user just asked for.
        assertTrue(
            card.contains("if (!isActive && (gamingState is GamingModeState.Interrupted || recovery.isNeeded))")
        )
        assertTrue(card.contains("enabled = !isRestoringApps && !isBusy"))
        // The action is exposed by the ViewModel, not run from the screen.
        assertTrue(viewModel.contains("fun restoreSuspendedApps()"))
        assertTrue(screen.contains("onRestoreGamingApps = viewModel::restoreSuspendedApps"))
    }

    @Test
    fun interruptedSessionIsNotReportedAsReadyOrActive() {
        assertTrue(card.contains("GamingModeState.Checking"))
        assertTrue(card.contains("GamingModeState.Interrupted"))
        assertTrue(card.contains("R.string.gt_gm_checking"))
        assertTrue(card.contains("R.string.gt_gm_interrupted"))
        // The power button's action follows the session record, not just the state name: an error
        // after the session opened still needs the revert.
        assertTrue(card.contains("if (isSessionOpen) onDeactivate() else onActivate()"))
        assertTrue(screen.contains("isSessionOpen = isGamingSessionOpen"))
        assertTrue(viewModel.contains("val isGamingSessionOpen = gamingEngine.sessionOpen"))
    }

    @Test
    fun cleanerCardOwnsTheRamAndTrimControls() {
        // Three stacked cards (cleaner, RAM boost, trim) for one job made the memory section taller
        // than a screen. The controls now live in the cleaner's own content.
        assertTrue(screen.contains("fun CleaningContent("))
        assertTrue(screen.contains("onBoostRam = onBoostRam"))
        assertTrue(screen.contains("onTrimStorage = onTrimStorage"))
        assertTrue(screen.contains("canTrim = hasPrivilege"))
        // Both memory actions lead the card, above the scan they used to follow.
        val cleaner = screen.substringAfter("fun CleaningContent(")
        val scanIndex = cleaner.indexOf("onClick = onScan")
        assertTrue(
            "Reclaim RAM must sit above Scan",
            cleaner.indexOf("onClick = onBoostRam") in 0 until scanIndex
        )
        assertTrue(
            "Trim storage must sit above Scan",
            cleaner.indexOf("onClick = onTrimStorage") in 0 until scanIndex
        )
        assertFalse("RamBoostCard must be deleted, not left as a second surface", screen.contains("RamBoostCard("))
        val ramCard = File(
            "src/main/java/com/catsmoker/app/features/gamingtools/ui",
            "RamBoostCard.kt"
        )
        assertFalse("RamBoostCard.kt still exists", ramCard.exists())
    }

    @Test
    fun removalOfThePermissionToggleIsComplete() {
        assertFalse("GamingToolsScreen.kt still hosts the permission toggle", screen.contains("PermToggleContent"))
        assertFalse("GamingToolsScreen.kt still routes the picker", screen.contains("onShowPermPicker"))
        assertFalse(viewModel.contains("permToggleManager"))
        assertFalse(viewModel.contains("PermToggleManager"))
        val permDir = File(
            "src/main/java/com/catsmoker/app/features/gamingtools/tools",
            "permops"
        )
        assertFalse("permops sources still exist", permDir.exists())
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
