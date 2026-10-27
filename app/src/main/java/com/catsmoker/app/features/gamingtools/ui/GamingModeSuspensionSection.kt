package com.catsmoker.app.features.gamingtools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.app.R
import com.catsmoker.app.shared.data.model.GameInfo

/**
 * The extra freeze list, inside the Gaming Mode card.
 *
 * It shares [GamingModeAppPickerSection] with the keep list so both sides of the suspend sweep read
 * as one control. The only added content is the per-row badge and the selected-vs-frozen count: a
 * pick is configured immediately, but only becomes "frozen" when this session's `pm suspend` was
 * confirmed.
 *
 * @param picked packages the user added to the extra list. These are frozen at activation on top
 *   of the automatic sweep.
 * @param suspended packages this app has *confirmed* suspended right now. A pick that is not in
 *   this set is configured but not frozen — the distinction the old standalone card could not show,
 *   which made a configured app look like a frozen one and vice versa.
 */
@Composable
fun GamingModeSuspensionSection(
    picked: Set<String>,
    apps: List<GameInfo>,
    suspended: Set<String>,
    canEdit: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(GamingModeAppPicker.DEFAULT_EXPANDED) }
    val rows = remember(picked, apps) {
        gamingModePickedApps(picked, apps.associate { it.packageName to it.appName })
    }
    val count = GamingModeAppPicker.count(picked)
    val frozenCount = remember(picked, suspended) { picked.count { it in suspended } }

    GamingModeAppPickerSection(
        title = stringResource(R.string.gt_suspend_title),
        subtitle = stringResource(R.string.gt_suspend_sub),
        count = count,
        countDescription = stringResource(R.string.gt_suspend_some, count),
        rows = rows,
        expanded = expanded,
        onToggleExpanded = { expanded = GamingModeAppPicker.toggle(expanded) },
        emptyText = stringResource(R.string.gt_suspend_none),
        // Selected vs actually frozen, said as two numbers. A pick only becomes "frozen" when
        // this session's pm suspend was confirmed.
        statusText = if (rows.isEmpty()) {
            null
        } else {
            stringResource(R.string.gt_suspend_count, count, frozenCount)
        },
        showText = stringResource(R.string.gt_suspend_show),
        hideText = stringResource(R.string.gt_suspend_hide),
        addText = stringResource(R.string.gt_suspend_add),
        removeText = stringResource(R.string.gt_suspend_remove),
        onAdd = onAdd,
        onRemove = onRemove,
        canEdit = canEdit,
        explainerLines = listOf(
            stringResource(R.string.gt_suspend_what_1),
            stringResource(R.string.gt_suspend_what_2)
        ),
        rowBadge = { row ->
            GamingModeSuspendBadge(isFrozen = row.packageName in suspended)
        }
    )
}

/** "frozen" / "not frozen" on a configured row, read from the confirmed suspend set only. */
@Composable
private fun GamingModeSuspendBadge(isFrozen: Boolean) {
    Text(
        text = stringResource(
            if (isFrozen) R.string.gt_suspend_badge_frozen
            else R.string.gt_suspend_badge_selected
        ),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = if (isFrozen) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isFrozen) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
