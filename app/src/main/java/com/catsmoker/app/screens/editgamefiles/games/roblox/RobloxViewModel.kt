package com.catsmoker.app.screens.editgamefiles.games.roblox

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsmoker.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class RobloxOperation { REFRESH, APPLY, RESTORE, CLOSE }

internal data class RobloxUiState(
    val status: RobloxStatus? = null,
    val selected: Int = 240,
    val operation: RobloxOperation? = null,
    val problem: RobloxProblem? = null,
    val installed: Boolean? = null
) {
    val busy: Boolean get() = operation != null
    val canApply: Boolean get() = !busy && installed != false && status?.let {
        !it.conflict && !it.invalidConfig && it.target != selected
    } == true
}

@HiltViewModel
class RobloxViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: RobloxManager
) : ViewModel() {
    private val state = MutableStateFlow(RobloxUiState())
    internal val uiState = state.asStateFlow()
    private val messages = Channel<Int>(Channel.BUFFERED)
    internal val events = messages.receiveAsFlow()

    init { refresh() }

    internal fun select(fps: Int) {
        if (!state.value.busy && fps == RobloxConfig.targets.last()) state.update { it.copy(selected = fps) }
    }

    internal fun refresh() = run(RobloxOperation.REFRESH) { load(resetSelection = true) }

    internal fun apply() {
        val current = state.value
        if (!current.canApply) return
        run(RobloxOperation.APPLY) {
            manager.apply(current.selected, requireNotNull(current.status).file)
            load(resetSelection = false)
            messages.send(R.string.rb_file_written)
        }
    }

    internal fun restore() {
        val status = state.value.status ?: return
        if (!status.canRestore) return
        run(RobloxOperation.RESTORE) {
            manager.restore(status.file)
            load(resetSelection = true)
            messages.send(R.string.rb_restored)
        }
    }

    internal fun closeGame() = run(RobloxOperation.CLOSE) {
        manager.closeGame()
        messages.send(R.string.rb_closed)
    }

    private fun run(operation: RobloxOperation, action: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(operation = operation, problem = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Invalidate stale controls; Refresh re-reads both the live file and recovery journal.
                state.update { it.copy(status = null, problem = (error as? RobloxFailure)?.problem ?: RobloxProblem.IO) }
            } finally {
                state.update { it.copy(operation = null) }
            }
        }
    }

    private suspend fun load(resetSelection: Boolean) {
        val installed = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getPackageInfo(RobloxConfig.PACKAGE, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: Exception) {
                null
            }
        }
        state.update { it.copy(installed = installed) }
        val status = manager.status()
        state.update { current -> current.copy(
            status = status,
            selected = if (resetSelection) RobloxConfig.targets.last() else current.selected
        ) }
    }
}
