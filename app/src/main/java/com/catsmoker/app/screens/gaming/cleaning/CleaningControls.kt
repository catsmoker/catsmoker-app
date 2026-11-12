package com.catsmoker.app.screens.gaming.cleaning

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.R
import com.catsmoker.app.application.distribution.VariantCapabilities
import com.catsmoker.app.format.formatBytes
import com.catsmoker.app.screens.gaming.cleaning.CleanerScheduleStore
import com.catsmoker.app.screens.gaming.cleaning.CleaningFeature
import com.catsmoker.app.screens.gaming.components.InfoTopics
import com.catsmoker.app.screens.gaming.components.formatScheduleTime
import com.catsmoker.app.screens.gaming.components.intervalLabel
import com.catsmoker.app.ui.components.CatsmokerButton
import com.catsmoker.app.ui.components.ChipFlowRow
import com.catsmoker.app.ui.components.InfoButton
import java.io.File

@Composable
fun CleaningContent(
    isRooted: Boolean,
    isShizukuActive: Boolean,
    cleanResult: CleaningFeature.CleanResult?,
    report: CleaningFeature.ScanReport?,
    isScanning: Boolean,
    isCleaning: Boolean,
    keepEntries: Set<String>,
    cleanPatterns: Set<String>,
    onAddKeepEntry: (String) -> Unit,
    onRemoveKeepEntry: (String) -> Unit,
    onAddCleanPattern: (String) -> Unit,
    onRemoveCleanPattern: (String) -> Unit,
    onScan: () -> Unit,
    onPerform: (List<CleaningFeature.Category>) -> Unit,
    onGrantStorageAccess: () -> Unit,
    scheduleEnabled: Boolean,
    scheduleIntervalHours: Int,
    scheduleNextRunAt: Long?,
    onScheduleEnabledChange: (Boolean) -> Unit,
    onScheduleIntervalChange: (Int) -> Unit,
    // The memory half. RAM reclaim and filesystem trim live in this same card because they are
    // the same job — make room now — and three stacked cards made the memory section one and a
    // half screens tall before a single button was pressed.
    isBoostingRam: Boolean,
    ramResult: String?,
    onBoostRam: () -> Unit,
    isTrimmingStorage: Boolean,
    trimResult: String?,
    canTrim: Boolean,
    onTrimStorage: () -> Unit
) {
    var selectedCategories by remember { mutableStateOf(CleaningFeature.Category.entries.filter { !it.isAggressive }.toSet()) }
    var reviewExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    val resultsByCategory = remember(report) { report?.results?.associateBy { it.category }.orEmpty() }
    val motionMs = rememberMotionDurationMs()
    // A finished scan opens the review once: the user asked to look, and the categories are the
    // result. Collapsing it again stays collapsed — the effect only fires when the report changes.
    LaunchedEffect(report) {
        if (report != null && report.results.isNotEmpty()) reviewExpanded = true
    }
    // One line that answers "what is there?". The channel note replaces it before the first
    // scan, because then the useful fact is how much of the device can be reached at all.
    val statusText = when {
        report == null -> stringResource(
            R.string.gt_cleaner_reach,
            when {
                isRooted -> stringResource(R.string.gt_cleaner_reach_root)
                isShizukuActive -> stringResource(R.string.gt_cleaner_reach_shizuku)
                else -> stringResource(R.string.gt_cleaner_reach_plain)
            }
        )
        !report.scannedAnything -> stringResource(R.string.gt_cleaner_unreadable)
        report.results.isEmpty() -> stringResource(R.string.gt_cleaner_looked_empty)
        report.totalBytes == 0L -> stringResource(R.string.gt_cleaner_empty_only, report.totalItems)
        else -> stringResource(R.string.gt_cleaner_can_free, formatBytes(report.totalBytes), report.totalItems)
    }
    val statusColor = when {
        report == null -> MaterialTheme.colorScheme.onSurfaceVariant
        !report.scannedAnything -> Color(0xFFFFB300)
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = statusText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = if (report != null) FontWeight.SemiBold else FontWeight.Normal,
            color = statusColor
        )
        Spacer(modifier = Modifier.height(10.dp))
        // Memory and storage controls lead: they are one-tap actions, while the scan below is the
        // longer inspection flow. Both are filled primary buttons so they read as actions, not as
        // links. The divider keeps the scan's own block separate.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CatsmokerButton(
                onClick = onBoostRam,
                modifier = Modifier.weight(1f),
                enabled = !isBoostingRam
            ) {
                if (isBoostingRam) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Text(
                        stringResource(R.string.gt_ram_free),
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            }
            CatsmokerButton(
                onClick = onTrimStorage,
                modifier = Modifier.weight(1f),
                enabled = !isTrimmingStorage && canTrim
            ) {
                if (isTrimmingStorage) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Text(
                        stringResource(R.string.gt_trim_title),
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        if (!canTrim) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.gt_trim_need),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // The toast is transient; keeping the measured result on the card lets the user read it.
        listOfNotNull(ramResult, trimResult).forEach { result ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = result,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatsmokerButton(
                onClick = onScan,
                modifier = Modifier.weight(1f),
                enabled = !isScanning && !isCleaning
            ) {
                Text(if (isScanning) stringResource(R.string.gt_cleaner_scanning) else stringResource(R.string.gt_cleaner_scan))
            }
            CatsmokerButton(
                onClick = { reviewExpanded = !reviewExpanded },
                modifier = Modifier.weight(1f),
                enabled = report != null && !isScanning && !isCleaning && report.scannedAnything
            ) {
                Text(
                    if (reviewExpanded) stringResource(R.string.gt_cleaner_review_hide)
                    else stringResource(R.string.gt_cleaner_review)
                )
            }
        }
        // Review and clean are one disclosure: the category checkboxes decide what Clean
        // removes, so separating them into "review here, clean there" was two steps for one
        // decision.
        AnimatedVisibility(
            visible = reviewExpanded,
            enter = fadeIn(tween(motionMs)) + expandVertically(tween(motionMs)),
            exit = fadeOut(tween(motionMs)) + shrinkVertically(tween(motionMs))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                CleaningFeature.Category.entries.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clickable {
                                selectedCategories = if (selectedCategories.contains(category)) {
                                    selectedCategories - category
                                } else {
                                    selectedCategories + category
                                }
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedCategories.contains(category),
                            onCheckedChange = { checked ->
                                selectedCategories = if (checked) selectedCategories + category else selectedCategories - category
                            }
                        )
                        Text(
                            categoryLabel(category),
                            modifier = Modifier.weight(1f),
                            fontSize = 12.sp,
                            color = if (category.isAggressive) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                        // Only a measured size is shown as a size. "Not scanned" and "none found"
                        // are different outcomes and neither of them is 0 B. The count leads,
                        // because empty files and folders are real finds that reclaim no bytes.
                        val entry = resultsByCategory[category]
                        when {
                            report == null -> Unit
                            entry != null -> Text(
                                stringResource(R.string.gt_cleaner_found_count, entry.itemCount, formatBytes(entry.sizeBytes)),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            report.scannedAnything -> Text(stringResource(R.string.gt_cleaner_none_found), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> Text(stringResource(R.string.gt_cleaner_not_scanned), fontSize = 10.sp, color = Color(0xFFFFB300))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                CatsmokerButton(
                    onClick = { onPerform(selectedCategories.toList()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isScanning && !isCleaning && selectedCategories.isNotEmpty()
                ) {
                    Text(if (isCleaning) stringResource(R.string.gt_cleaner_cleaning) else stringResource(R.string.gt_cleaner_clean))
                }
            }
        }
        // The outcome of the last clean, in one line. This replaced a scrolling terminal view: the
        // figures are the same measured counts the log lines were built from, so nothing is lost,
        // but the result is readable at a glance instead of needing to be parsed out of a transcript.
        if (cleanResult != null) {
            Spacer(modifier = Modifier.height(10.dp))
            CleanResultRow(cleanResult)
        }
        if (report != null) {
            if (report.limitations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFB300).copy(alpha = 0.10f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.gt_cleaner_limitations_title), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                    report.limitations.forEach { limitation ->
                        Text("• $limitation", fontSize = 11.sp, color = Color(0xFFFFB300), lineHeight = 15.sp)
                    }
                }
            }
            if (report.needsAllFilesAccess && VariantCapabilities.HAS_ALL_FILES_ACCESS) {
                Spacer(modifier = Modifier.height(8.dp))
                CatsmokerButton(onClick = onGrantStorageAccess, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gt_cleaner_grant))
                }
            }
            if (keepEntries.isNotEmpty() || cleanPatterns.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                CleanerRulesSummary(
                    keepEntries = keepEntries,
                    cleanPatterns = cleanPatterns,
                    onRemoveKeepEntry = onRemoveKeepEntry,
                    onRemoveCleanPattern = onRemoveCleanPattern
                )
            }
        }
        // Schedule and rule editing sit behind one disclosure. Neither is needed to scan once,
        // and both were permanently on screen before.
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
            Text(
                if (advancedExpanded) stringResource(R.string.gt_cleaner_advanced_hide)
                else stringResource(R.string.gt_cleaner_advanced),
                fontSize = 12.sp
            )
        }
        AnimatedVisibility(
            visible = advancedExpanded,
            enter = fadeIn(tween(motionMs)) + expandVertically(tween(motionMs)),
            exit = fadeOut(tween(motionMs)) + shrinkVertically(tween(motionMs))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                CleanerScheduleSection(
                    enabled = scheduleEnabled,
                    intervalHours = scheduleIntervalHours,
                    nextRunAt = scheduleNextRunAt,
                    onEnabledChange = onScheduleEnabledChange,
                    onIntervalChange = onScheduleIntervalChange
                )
                CleanerRulesEditor(
                    keepCount = keepEntries.size,
                    patternCount = cleanPatterns.size,
                    onAddKeepEntry = onAddKeepEntry,
                    onAddCleanPattern = onAddCleanPattern
                )
            }
        }
    }
}
/**
 * Reveal animation length for the cleaner's disclosures.
 *
 * Compose animations do not read Android's animator duration scale by themselves, so a user who
 * turned animations off for accessibility would still get an expanding panel here. 0 makes the
 * reveal instant instead of merely fast.
 */
@Composable
internal fun rememberMotionDurationMs(): Int {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        if (scale <= 0f) 0 else 180
    }
}
/**
 * The user's own keep/clean rules, listed with a way to remove each.
 *
 * Shown only when at least one rule exists — the empty state is the common case and needs no UI.
 * A rule change applies from the *next* scan, not to the report already on screen, so the row
 * never pretends a rule edited the numbers above it.
 */@Composable
internal fun CleanerRulesSummary(
    keepEntries: Set<String>,
    cleanPatterns: Set<String>,
    onRemoveKeepEntry: (String) -> Unit,
    onRemoveCleanPattern: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF64B5F6).copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(R.string.gt_cleaner_rules_title), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
        keepEntries.forEach { entry ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gt_cleaner_rule_keep, entry), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onRemoveKeepEntry(entry) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.gt_cleaner_remove_keep), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
            }
        }
        cleanPatterns.forEach { pattern ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gt_cleaner_rule_clean, pattern), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onRemoveCleanPattern(pattern) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.gt_cleaner_remove_clean), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
/**
 * Collapsed entry point for adding keep entries and clean patterns.
 *
 * Both kinds are typed free-form, so the editor stays one expanded section with two fields rather
 * than two dialogs. Keep entries are plain text (a path or a bare file name); clean patterns are
 * regular-expression text matched against the whole path, and an invalid one is rejected by the
 * ViewModel with a toast rather than stored to break every later scan.
 */
@Composable
internal fun CleanerRulesEditor(
    keepCount: Int,
    patternCount: Int,
    onAddKeepEntry: (String) -> Unit,
    onAddCleanPattern: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var keepInput by remember { mutableStateOf("") }
    var patternInput by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            val ruleCount = keepCount + patternCount
            Text(
                if (expanded) stringResource(R.string.gt_cleaner_rules_hide)
                else if (ruleCount > 0) stringResource(R.string.gt_cleaner_rules_add_count, ruleCount)
                else stringResource(R.string.gt_cleaner_rules_add),
                fontSize = 12.sp
            )
        }
        if (expanded) {
            Text(
                stringResource(R.string.gt_cleaner_rules_help),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = keepInput,
                    onValueChange = { keepInput = it },
                    placeholder = { Text(stringResource(R.string.gt_cleaner_keep_hint), fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                CatsmokerButton(
                    onClick = {
                        onAddKeepEntry(keepInput)
                        keepInput = ""
                    },
                    enabled = keepInput.isNotBlank()
                ) { Text(stringResource(R.string.gt_cleaner_add)) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = patternInput,
                    onValueChange = { patternInput = it },
                    placeholder = { Text(stringResource(R.string.gt_cleaner_pattern_hint), fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                CatsmokerButton(
                    onClick = {
                        onAddCleanPattern(patternInput)
                        patternInput = ""
                    },
                    enabled = patternInput.isNotBlank()
                ) { Text(stringResource(R.string.gt_cleaner_add)) }
            }
        }
    }
}
/**
 * States the result of a clean in one line, with a green check when something was actually removed.
 *
 * The headline is deliberately not always "Cleaned N MB". Empty files and empty folders are real junk
 * that frees no bytes, so a clean can legitimately remove hundreds of items and reclaim nothing —
 * printing "Cleaned 0 B" there would read as a failure. Whatever cannot be stated as a measured figure
 * is stated as what it is instead.
 */
@Composable
internal fun CleanResultRow(result: CleaningFeature.CleanResult) {
    val success = !result.removedNothing
    val accent = if (success) Color(0xFF4CAF50) else Color(0xFFFFB300)
    val headline = when {
        result.removedNothing && result.failedItems > 0 -> stringResource(R.string.gt_cleaner_result_nothing_failed)
        result.removedNothing -> stringResource(R.string.gt_cleaner_result_nothing)
        // A size of zero after real deletions means everything removed was empty, which is a
        // different fact from having freed nothing measurable.
        result.freedBytes == 0L && result.unmeasuredItems == 0 ->
            stringResource(R.string.gt_cleaner_result_empty, result.deletedItems)
        result.unmeasuredItems > 0 ->
            stringResource(R.string.gt_cleaner_result_at_least, formatBytes(result.freedBytes))
        else -> stringResource(R.string.gt_cleaner_result_freed, formatBytes(result.freedBytes))
    }
    // Every count that is not zero gets said out loud. An item that was listed and then not removed
    // has to be accounted for somewhere, or the headline overstates what happened.
    val details = buildList {
        if (!result.removedNothing) add(Pair(R.string.gt_cleaner_detail_deleted, result.deletedItems))
        if (result.unmeasuredItems > 0) {
            add(Pair(R.string.gt_cleaner_detail_unmeasured, result.unmeasuredItems))
        }
        if (result.failedItems > 0) {
            add(
                Pair(
                    if (result.privileged) R.string.gt_cleaner_detail_failed_priv
                    else R.string.gt_cleaner_detail_failed_nopriv,
                    result.failedItems
                )
            )
        }
        if (result.alreadyGoneItems > 0) add(Pair(R.string.gt_cleaner_detail_gone, result.alreadyGoneItems))
        if (result.protectedSkips > 0) add(Pair(R.string.gt_cleaner_detail_protected, result.protectedSkips))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(headline, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        details.forEach { (res, count) ->
            Text("• " + stringResource(res, count), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), lineHeight = 15.sp)
        }
        if (result.trimmedInternalCaches) {
            Text("• " + stringResource(R.string.gt_cleaner_detail_trim), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), lineHeight = 15.sp)
        }
    }
}
/**
 * Localized label for a cleaner bucket. The enum itself carries the English words (it has no
 * Context), so the mapping lives here at the call site inside the composable.
 */
@Composable
internal fun categoryLabel(category: CleaningFeature.Category): String = stringResource(
    when (category) {
        CleaningFeature.Category.CACHE -> R.string.gt_cat_cache
        CleaningFeature.Category.TEMP -> R.string.gt_cat_temp
        CleaningFeature.Category.THUMBNAILS -> R.string.gt_cat_thumbs
        CleaningFeature.Category.EMPTY_FILES -> R.string.gt_cat_empty_files
        CleaningFeature.Category.EMPTY_DIRS -> R.string.gt_cat_empty_dirs
        CleaningFeature.Category.LOGS -> R.string.gt_cat_logs
        CleaningFeature.Category.CORPSES -> R.string.gt_cat_corpses
        CleaningFeature.Category.INSTALLERS -> R.string.gt_cat_installers
    }
)
/**
 * The recurring junk sweep's controls. Same contract as the dexopt schedule: the switch state
 * is the persisted setting *and* the enrollment, the next-run line is WorkManager's own
 * estimate, and the interval chips retune in place. The explainer states the unattended
 * boundary up front — safe buckets only, aggressive ones stay manual in every run.
 */
@Composable
internal fun CleanerScheduleSection(
    enabled: Boolean,
    intervalHours: Int,
    nextRunAt: Long?,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.gt_booster_schedule),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    when {
                        enabled && nextRunAt != null -> stringResource(R.string.gt_booster_next, formatScheduleTime(nextRunAt))
                        enabled -> stringResource(R.string.gt_booster_next_unknown)
                        else -> stringResource(R.string.gt_booster_sched_off)
                    },
                    fontSize = 11.sp,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            InfoButton(InfoTopics.cleaningSchedule)
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            ChipFlowRow {
                CleanerScheduleStore.INTERVAL_CHOICES.forEach { hours ->
                    FilterChip(
                        selected = intervalHours == hours,
                        onClick = { onIntervalChange(hours) },
                        label = { Text(intervalLabel(hours)) }
                    )
                }
            }
        }
    }
}
