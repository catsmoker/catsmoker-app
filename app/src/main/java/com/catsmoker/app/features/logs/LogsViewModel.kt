package com.catsmoker.app.features.logs

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsmoker.app.R
import com.catsmoker.app.system.shell.ShellRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRunner: ShellRunner
) : ViewModel() {

    data class LogsUiState(
        val logs: List<String> = emptyList(),
        val isLoading: Boolean = false,
        val filterQuery: String = ""
    )

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        refreshLogs()
    }

    fun refreshLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Dump the whole logcat buffer — a 500-line tail hides the lines a bug report needs.
                val output = shellRunner.exec("logcat -d")
                val lines = output.split("\n").filter { it.isNotBlank() }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(logs = lines, isLoading = false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, logs = listOf(context.getString(R.string.core_logs_fetch_error, e.message))) }
                }
            }
        }
    }

    fun onFilterQueryChanged(query: String) {
        _uiState.update { it.copy(filterQuery = query) }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            shellRunner.exec("logcat -c")
            refreshLogs()
        }
    }

    /**
     * Shares the log as a text file through the FileProvider.
     *
     * The previous version put the whole dump in `EXTRA_TEXT`. A full `logcat -d` buffer is
     * megabytes, and anything near a megabyte in an Intent extra dies in
     * `TransactionTooLargeException` — a fatal crash on the main thread, exactly as the
     * 2.9 MB parcel in the field report showed. Only a URI string crosses the binder now,
     * so the dump can be any size. Old shared files are pruned to the newest few so the
     * cache does not grow one file per tap.
     */
    fun shareLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = java.io.File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
                dir.listFiles()
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(MAX_KEPT_FILES - 1)
                    ?.forEach { runCatching { it.delete() } }
                val file = java.io.File(
                    dir,
                    "catsmoker-logs-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}.txt"
                )
                file.bufferedWriter().use { writer ->
                    _uiState.value.logs.forEach { line ->
                        writer.write(line)
                        writer.newLine()
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.core_logs_share_title))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                withContext(Dispatchers.Main) {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(intent, context.getString(R.string.core_logs_share_title)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }.onFailure { e ->
                        // No share targets installed: report, don't crash. The file stays in
                        // cache for the next attempt rather than being rebuilt.
                        Log.w(TAG, "No app to share logs with", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not write logs for sharing", e)
            }
        }
    }

    private companion object {
        const val TAG = "LogsViewModel"

        /** Cache subdir holding shared log files (served by the FileProvider `cache-path`). */
        const val SHARE_DIR = "shared"

        /** Old shared files kept; the newest write makes room before writing. */
        const val MAX_KEPT_FILES = 3
    }
}
