package com.catsmoker.app.screens.gaming.cache

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.R
import com.catsmoker.app.screens.gaming.GameSettingsStore
import com.catsmoker.app.screens.gaming.components.ExpandableToolCard
import com.catsmoker.app.screens.gaming.components.gamingOperationLabel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import org.json.JSONObject

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CacheControlsEntryPoint {
    fun settings(): GameSettingsStore
    fun preloader(): GameCachePreloader
}

@Composable
fun GameCacheControls(pkg: String?, gameName: String?) {
    if (pkg == null || LocalInspectionMode.current) {
        ExpandableToolCard(
            title = stringResource(R.string.gt_cache_experiment),
            subtitle = stringResource(R.string.gt_context_select_game),
            icon = Icons.Default.Memory,
            enabled = false,
            expandable = false
        )
        return
    }
    val context = LocalContext.current
    val entry = remember { EntryPointAccessors.fromApplication(context.applicationContext, CacheControlsEntryPoint::class.java) }
    val store = entry.settings()
    val selection by store.selection.collectAsStateWithLifecycle()
    val settings = if (selection.packageName == pkg) selection.settings else store.read(pkg)
    val scope = rememberCoroutineScope()
    val status by entry.preloader().status.collectAsStateWithLifecycle()
    val report = remember(status, pkg) { CacheUiReport.forGame(status, pkg) }
    val running = remember(status) { runCatching { JSONObject(status).optString("result") == "running" }.getOrDefault(false) }
    var advanced by rememberSaveable(pkg) { mutableStateOf(false) }
    var pickerPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val target = pickerPackage
        pickerPackage = null
        if (target == null || uris.isEmpty()) return@rememberLauncherForActivityResult
        val admitted = uris.take(8).filter { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
        }.map { it.toString() }
        // Capture the picker package; returning after a selection change cannot edit the new game.
        store.update(target) { it.copy(cacheDocuments = admitted) }
    }
    ExpandableToolCard(
        title = stringResource(R.string.gt_cache_experiment),
        subtitle = gameName ?: pkg,
        icon = Icons.Default.Memory
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.gt_cache_experimental_badge), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge)
        Text(gameName ?: pkg, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.gt_cache_intro))
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(
            value = settings.cachePreload, role = Role.Switch,
            onValueChange = { enabled -> store.update(pkg) { it.copy(cachePreload = enabled) } }),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.gt_cache_enable), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall)
            Switch(checked = settings.cachePreload, onCheckedChange = null)
        }
        Text(stringResource(R.string.gt_cache_permissions), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(stringResource(R.string.gt_cache_budget), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(8, 32, 64).forEach { size ->
                FilterChip(selected = settings.cacheBudgetMiB == size, onClick = {
                    store.update(pkg) { it.copy(cacheBudgetMiB = size) }
                }, label = { Text(stringResource(R.string.gt_cache_budget_value, size)) })
            }
        }
        Text(stringResource(R.string.gt_cache_limits), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { pickerPackage = pkg; documents.launch(arrayOf("*/*")) }) {
            Text(stringResource(R.string.gt_cache_pick_assets))
        }
        Text(stringResource(R.string.gt_cache_document_count, settings.cacheDocuments.size),
            style = MaterialTheme.typography.bodySmall)
        if (settings.cacheDocuments.isNotEmpty()) TextButton(onClick = {
            store.update(pkg) { it.copy(cacheDocuments = emptyList()) }
        }) { Text(stringResource(R.string.gt_cache_clear_assets)) }
        HorizontalDivider()
        Text(stringResource(R.string.gt_cache_last_outcome), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(if (running && report?.result != "running") R.string.gt_cache_other_running
            else gamingOperationLabel(report?.result.orEmpty())))
        if (report != null) {
            Text(stringResource(R.string.gt_cache_read_value, report.bytes / (1024.0 * 1024.0)),
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.gt_cache_elapsed, report.elapsedMs), style = MaterialTheme.typography.bodySmall)
            if (report.skipped > 0) Text(stringResource(R.string.gt_cache_skipped, report.skipped),
                style = MaterialTheme.typography.bodySmall)
        }
        if (report?.result == "running") {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { entry.preloader().cancel() }) { Text(stringResource(R.string.gt_cache_cancel)) }
        } else {
            FilledTonalButton(enabled = settings.cachePreload && !running,
                onClick = { scope.launch { entry.preloader().preload(pkg) } }) {
                Text(stringResource(R.string.gt_cache_test_only))
            }
        }
        TextButton(onClick = { advanced = !advanced }) {
            Text(stringResource(if (advanced) R.string.gt_advanced_hide else R.string.gt_cache_advanced))
        }
        if (advanced) {
            Text(stringResource(R.string.gt_cache_warning), style = MaterialTheme.typography.bodySmall)
            if (report != null) Text(status, style = MaterialTheme.typography.bodySmall)
        }
      }
    }
}
