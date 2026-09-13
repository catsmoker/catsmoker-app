package com.catsmoker.app.features.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.catsmoker.app.system.ads.AdManager
import com.catsmoker.app.system.config.AppearanceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Play-build settings: appearance + ads only.
 *
 * The GitHub-release self-updater (release check, APK download to `update.apk`,
 * FileProvider install intent) is removed on the `playstore` branch — Play
 * updates come from Play (see PLAYSTORE.md). The auto-check / pre-release
 * prefs went with it.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adManager: AdManager,
) : ViewModel() {

    data class UiState(
        val adsEnabled: Boolean = true,
        val themeMode: AppearanceStore.ThemeMode = AppearanceStore.ThemeMode.SYSTEM,
        /** BCP-47 tag, or "" for system default. Endonyms stay native — never localized. */
        val languageTag: String = AppearanceStore.LANGUAGE_SYSTEM,
    )

    /** One-shot UI actions the screen itself must perform (e.g. recreating for a new locale). */
    sealed interface UiEvent {
        data object RecreateActivity : UiEvent
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        _uiState.update {
            it.copy(
                adsEnabled = adManager.isEnabled(),
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
        // Resources are bound to the activity's configuration — only a recreate re-resolves
        // every stringResource/getString in the composition, so the new language is instant
        // everywhere instead of only on screens opened afterwards.
        _events.tryEmit(UiEvent.RecreateActivity)
    }

    fun onAdsToggled(enabled: Boolean) {
        adManager.setEnabled(enabled)
        _uiState.update { it.copy(adsEnabled = enabled) }
    }
}
