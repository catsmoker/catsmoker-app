package com.catsmoker.app.features.gamingtools.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.catsmoker.app.R
import com.catsmoker.app.shared.data.model.GameInfo

/**
 * The Gaming Mode keep list, inside the Gaming Mode card.
 *
 * This feature is a picker, not a switch: it stores a set of packages that the suspend sweep must
 * leave running (`SuspendExemptStore`) and the engine reads it at the next activation. The heading
 * carries the distinct configured count, the body starts collapsed with a short name summary, and
 * the expanded rows live in a bounded scrolling area so a large list cannot make the card
 * indefinitely tall. Folding or unfolding never edits the stored set.
 *
 * The layout lives in [GamingModeAppPickerSection] so it stays identical to the extra freeze list
 * beside it; only the copy and the absence of a per-row badge differ.
 *
 * @param canEdit whether root or Shizuku is available. Adding is disabled without one, with the
 *   reason stated on the card. Removing a pick needs no privilege because it only edits this app's
 *   own list.
 */
@Composable
fun GamingModeKeepRunningSection(
    picked: Set<String>,
    apps: List<GameInfo>,
    canEdit: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nameByPackage = remember(apps) { apps.associate { it.packageName to it.appName } }
    val rows = remember(picked, nameByPackage) { gamingModePickedApps(picked, nameByPackage) }
    val count = GamingModeAppPicker.count(picked)
    var expanded by rememberSaveable { mutableStateOf(GamingModeAppPicker.DEFAULT_EXPANDED) }

    GamingModeAppPickerSection(
        title = stringResource(R.string.gt_keep_title),
        subtitle = stringResource(R.string.gt_keep_sub),
        count = count,
        countDescription = stringResource(R.string.gt_keep_some, count),
        rows = rows,
        expanded = expanded,
        onToggleExpanded = { expanded = GamingModeAppPicker.toggle(expanded) },
        emptyText = stringResource(R.string.gt_keep_none),
        showText = stringResource(R.string.gt_keep_show_list),
        hideText = stringResource(R.string.gt_keep_hide_list),
        addText = stringResource(R.string.gt_keep_add),
        removeText = stringResource(R.string.gt_keep_remove),
        onAdd = onAdd,
        onRemove = onRemove,
        canEdit = canEdit,
        info = InfoTopics.keepRunning,
        modifier = modifier
    )
}
