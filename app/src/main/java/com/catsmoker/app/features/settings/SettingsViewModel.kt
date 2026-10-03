package com.catsmoker.app.features.settings

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsmoker.app.BuildConfig
import com.catsmoker.app.R
import com.catsmoker.app.system.ads.AdManager
import com.catsmoker.app.system.config.AppearanceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adManager: AdManager,
) : ViewModel() {

    data class UiState(
        val adsEnabled: Boolean = true,
        val autoCheck: Boolean = false,
        val isPreRelease: Boolean = false,
        val isUpdating: Boolean = false,
        val updateProgress: Float = 0f,
        val updateDialog: UpdateDialog? = null,
        val themeMode: AppearanceStore.ThemeMode = AppearanceStore.ThemeMode.SYSTEM,
        /** BCP-47 tag, or "" for system default. Endonyms stay native — never localized. */
        val languageTag: String = AppearanceStore.LANGUAGE_SYSTEM,
    )

    data class UpdateDialog(val tagName: String, val downloadUrl: String?)

    /** One-shot UI actions the screen itself must perform (process restart for locale). */
    sealed interface UiEvent {
        data object RestartApp : UiEvent
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val prefs by lazy { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    init {
        _uiState.update {
            it.copy(
                adsEnabled = adManager.isEnabled(),
                autoCheck = prefs.getBoolean("auto_check_update", false),
                isPreRelease = prefs.getBoolean("use_prerelease", false),
                themeMode = AppearanceStore.themeModeSync(context),
                languageTag = AppearanceStore.languageTag(context)
            )
        }
    }

    fun onThemeModeChanged(mode: AppearanceStore.ThemeMode) {
        // Live-recomposes via AppearanceStore.themeMode — no activity restart needed.
        AppearanceStore.setThemeMode(context, mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun onLanguageChanged(tag: String) {
        if (tag == _uiState.value.languageTag) return
        AppearanceStore.setLanguage(context, tag)
        _uiState.update { it.copy(languageTag = tag) }
        // The application context — and every cached getString in the surviving ViewModels,
        // engine state and running services — resolves the language once, at process start.
        // A bare recreate() would leave all of those on the previous language (English-selected
        // UI showing Arabic), so the process restarts and everything re-resolves fresh.
        _events.tryEmit(UiEvent.RestartApp)
    }

    fun onAdsToggled(enabled: Boolean) {
        adManager.setEnabled(enabled)
        _uiState.update { it.copy(adsEnabled = enabled) }
    }

    fun onAutoCheckToggled(enabled: Boolean) {
        prefs.edit { putBoolean("auto_check_update", enabled) }
        _uiState.update { it.copy(autoCheck = enabled) }
    }

    fun onCheckUpdates() {
        // Play Store owns updates on that variant — the section is hidden there,
        // but guard the entry point anyway so a stale call can never self-update.
        if (!SelfUpdater.SUPPORTED) return
        _toasts.tryEmit(context.getString(R.string.sys_checking_updates))
        performUpdateCheck(_uiState.value.isPreRelease)
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(updateDialog = null) }
    }

    fun startUpdateDownload() {
        if (!SelfUpdater.SUPPORTED) return
        val dialog = _uiState.value.updateDialog ?: return
        val url = dialog.downloadUrl
        if (url == null) {
            dismissUpdateDialog()
            return
        }
        dismissUpdateDialog()
        startUpdateDownload(url)
    }

    private fun performUpdateCheck(isPreRelease: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = SelfUpdater.checkForUpdate(isPreRelease, BuildConfig.VERSION_NAME)) {
                is SelfUpdater.CheckResult.NoReleases -> withContext(Dispatchers.Main) {
                    _toasts.tryEmit(context.getString(R.string.sys_no_releases))
                }
                is SelfUpdater.CheckResult.UpToDate -> withContext(Dispatchers.Main) {
                    _toasts.tryEmit(context.getString(R.string.sys_up_to_date, BuildConfig.VERSION_NAME))
                }
                is SelfUpdater.CheckResult.Available -> withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(updateDialog = UpdateDialog(result.info.tagName, result.info.downloadUrl))
                    }
                }
                is SelfUpdater.CheckResult.Failed -> withContext(Dispatchers.Main) {
                    _toasts.tryEmit(context.getString(R.string.sys_update_check_failed, result.message))
                }
            }
        }
    }

    private fun startUpdateDownload(url: String) {
        _uiState.update { it.copy(isUpdating = true, updateProgress = 0f) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = SelfUpdater.downloadUpdate(context, url) { progress ->
                    _uiState.update { it.copy(updateProgress = progress) }
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isUpdating = false) }
                    if (file != null) SelfUpdater.installUpdate(context, file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isUpdating = false) }
                    _toasts.tryEmit(context.getString(R.string.sys_download_failed, e.message ?: "?"))
                }
            }
        }
    }
}
