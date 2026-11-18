package com.catsmoker.app.screens.editgamefiles.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsmoker.app.screens.editgamefiles.additionalGameEditors
import com.catsmoker.app.screens.editgamefiles.model.ConfigEditorSpec
import com.catsmoker.app.screens.editgamefiles.model.GameType
import com.catsmoker.app.screens.editgamefiles.storage.AdditionalConfigStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConfigEditorOperation { READ, APPLY, RESTORE }

data class AdditionalConfigState(
    val spec: ConfigEditorSpec? = null,
    val fileIndex: Int = 0,
    val loaded: AdditionalConfigStorage.Loaded? = null,
    val edits: Map<String, String> = emptyMap(),
    val closed: Boolean = false,
    val busy: Boolean = false,
    val operation: ConfigEditorOperation? = null,
    val status: String? = null,
    val verified: Boolean = false
)

@HiltViewModel
class AdditionalConfigViewModel @Inject constructor(private val storage: AdditionalConfigStorage) : ViewModel() {
    private val mutableState = MutableStateFlow(AdditionalConfigState())
    val state = mutableState.asStateFlow()

    fun bind(game: GameType) {
        if (mutableState.value.spec == null) mutableState.value = AdditionalConfigState(spec = additionalGameEditors.getValue(game))
    }

    fun selectFile(index: Int) {
        val old = mutableState.value
        if (!old.busy && index in old.spec!!.files.indices) {
            mutableState.value = AdditionalConfigState(spec = old.spec, fileIndex = index)
        }
    }

    fun edit(id: String, value: String) {
        if (!mutableState.value.busy) mutableState.update { it.copy(edits = it.edits + (id to value), verified = false) }
    }

    fun confirmClosed(closed: Boolean) {
        if (!mutableState.value.busy) mutableState.update { it.copy(closed = closed) }
    }

    fun load(uri: Uri? = null, requestedFile: Int = mutableState.value.fileIndex) {
        if (requestedFile != mutableState.value.fileIndex) return
        operate(clearOnFailure = true) { old ->
            val file = old.spec!!.files[old.fileIndex]
            storage.load(file, uri)
        }
    }

    fun loadPickedDocument(fileIndex: Int, uri: Uri) {
        val old = mutableState.value
        if (old.busy || old.spec?.files?.getOrNull(fileIndex) == null) return
        if (old.fileIndex != fileIndex) selectFile(fileIndex)
        load(uri, fileIndex)
    }

    fun save(restore: Boolean = false) = operate(verified = true,
        operation = if (restore) ConfigEditorOperation.RESTORE else ConfigEditorOperation.APPLY) { old ->
        val spec = old.spec!!
        val loaded = old.loaded ?: error("Load a file first.")
        val edits = old.edits.filter { (id, value) -> loaded.document.values[id] != value }
        storage.save(spec, spec.files[old.fileIndex], loaded, if (restore) null else edits, old.closed)
    }

    private fun operate(
        verified: Boolean = false,
        clearOnFailure: Boolean = false,
        operation: ConfigEditorOperation = ConfigEditorOperation.READ,
        action: suspend (AdditionalConfigState) -> AdditionalConfigStorage.Loaded
    ) {
        val old = mutableState.value
        if (old.busy || old.spec == null) return
        mutableState.value = old.copy(busy = true, operation = operation, status = null, verified = false)
        viewModelScope.launch {
            try {
                val loaded = action(old)
                mutableState.value = old.copy(loaded = loaded, edits = loaded.document.values, busy = false, status = null, verified = verified)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = old.copy(busy = false, status = error.message ?: error.javaClass.simpleName, verified = false,
                    loaded = if (clearOnFailure) null else old.loaded,
                    edits = if (clearOnFailure) emptyMap() else old.edits)
            } finally {
                mutableState.update { it.copy(busy = false, operation = null) }
            }
        }
    }
}
