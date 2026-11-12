package com.catsmoker.app.screens.settings

import com.catsmoker.app.R

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsmoker.app.application.appearance.AppearanceStore
import com.catsmoker.app.application.privacy.AnalyticsConsent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Settings genuinely shared by both distributions; distribution controls own their own state. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    data class UiState(
        val themeMode: AppearanceStore.ThemeMode = AppearanceStore.ThemeMode.SYSTEM,
        val languageTag: String = AppearanceStore.LANGUAGE_SYSTEM,
        val analyticsEnabled: Boolean = AnalyticsConsent.DEFAULT_ENABLED,
        val notificationsEnabled: Boolean = true,
        val isSharingApk: Boolean = false
    )

    sealed interface UiEvent {
        data object RestartApp : UiEvent
    }

    private val _uiState = MutableStateFlow(UiState(
        themeMode = AppearanceStore.themeModeSync(context),
        languageTag = AppearanceStore.languageTag(context),
        analyticsEnabled = AnalyticsConsent.isEnabled(context),
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    ))
    val uiState = _uiState.asStateFlow()
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts = _toasts.asSharedFlow()
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun refreshNotifications() {
        _uiState.update { it.copy(
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) }
    }

    fun onThemeModeChanged(mode: AppearanceStore.ThemeMode) {
        AppearanceStore.setThemeMode(context, mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun onLanguageChanged(tag: String) {
        if (tag == _uiState.value.languageTag) return
        AppearanceStore.setLanguage(context, tag)
        _uiState.update { it.copy(languageTag = tag) }
        // Engine/application-context strings also need to resolve the new locale.
        _events.tryEmit(UiEvent.RestartApp)
    }

    fun onAnalyticsToggled(enabled: Boolean) {
        AnalyticsConsent.setEnabled(context, enabled)
        _uiState.update { it.copy(analyticsEnabled = enabled) }
    }

    /** Shares the installed APK, not an update downloaded from another distribution. */
    fun onShareApp() {
        if (_uiState.value.isSharingApk) return
        _uiState.update { it.copy(isSharingApk = true) }
        viewModelScope.launch {
            val outcome = AppApkSharer.share(context, context.getString(R.string.sys_share_app_title))
            _uiState.update { it.copy(isSharingApk = false) }
            when (outcome) {
                is ApkShareOutcome.Shared -> if (outcome.splitApks) {
                    _toasts.tryEmit(context.getString(R.string.sys_share_app_split_note))
                }
                ApkShareOutcome.Failed ->
                    _toasts.tryEmit(context.getString(R.string.sys_share_app_failed))
            }
        }
    }
}
