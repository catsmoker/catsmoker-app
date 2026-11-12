package com.catsmoker.app.screens.gaming.crosshair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsmoker.app.screens.gaming.crosshair.CrosshairAssets
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException

@HiltViewModel
class CrosshairLibraryViewModel @Inject constructor(
    private val repository: CrosshairLibraryRepository
) : ViewModel() {

    data class UiState(
        val entries: List<CrosshairCatalogEntry> = emptyList(),
        val downloadedAssets: Set<String> = emptySet(),
        val downloading: Set<String> = emptySet(),
        val failedDownloads: Set<String> = emptySet(),
        val isLoading: Boolean = true,
        val catalogLoadFailed: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, catalogLoadFailed = false) }
            try {
                val entries = withContext(Dispatchers.IO) { repository.catalog() }
                val downloaded = withContext(Dispatchers.IO) { repository.downloadedAssets() }
                _uiState.update {
                    it.copy(
                        entries = entries,
                        downloadedAssets = downloaded,
                        isLoading = false,
                        catalogLoadFailed = false
                    )
                }
            } catch (_: java.io.IOException) {
                _uiState.update { it.copy(isLoading = false, catalogLoadFailed = true) }
            } catch (_: JSONException) {
                _uiState.update { it.copy(isLoading = false, catalogLoadFailed = true) }
            } catch (_: IllegalArgumentException) {
                _uiState.update { it.copy(isLoading = false, catalogLoadFailed = true) }
            }
        }
    }

    fun download(filename: String) {
        if (filename in _uiState.value.downloading) return
        _uiState.update {
            it.copy(
                downloading = it.downloading + filename,
                failedDownloads = it.failedDownloads - filename
            )
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.download(filename) }
                _uiState.update {
                    it.copy(
                        downloading = it.downloading - filename,
                        downloadedAssets = it.downloadedAssets + CrosshairAssets.downloadedAssetName(filename)
                    )
                }
            } catch (_: java.io.IOException) {
                markDownloadFailed(filename)
            } catch (_: IllegalArgumentException) {
                markDownloadFailed(filename)
            }
        }
    }

    private fun markDownloadFailed(filename: String) {
        _uiState.update {
            it.copy(
                downloading = it.downloading - filename,
                failedDownloads = it.failedDownloads + filename
            )
        }
    }
}
