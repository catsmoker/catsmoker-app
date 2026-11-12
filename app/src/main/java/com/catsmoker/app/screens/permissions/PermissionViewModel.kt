package com.catsmoker.app.screens.permissions

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsmoker.app.application.appearance.AppearanceStore
import com.catsmoker.app.application.appearance.LocaleHelper
import com.catsmoker.app.application.privacy.AnalyticsConsent
import com.catsmoker.app.application.privacy.GamingAccessConsent
import com.catsmoker.app.features.gamingtools.engine.GamingNotificationListener
import com.catsmoker.app.platform.shell.ShellRunner
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
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
import rikka.shizuku.Shizuku

@HiltViewModel
class PermissionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRunner: ShellRunner,
) : ViewModel() {

    data class UiState(
        /** True while the first-run flow is running; false when reopened from Settings. */
        val isOnboarding: Boolean = true,
        val choices: OnboardingState = OnboardingState(),
        val themeMode: AppearanceStore.ThemeMode = AppearanceStore.ThemeMode.SYSTEM,
        /** Applied immediately; the draft preserves the step across a process restart. */
        val languageTag: String = AppearanceStore.LANGUAGE_SYSTEM,
        val rootGranted: Boolean = false,
        val rootAvailable: Boolean = false,
        val notifGranted: Boolean = false,
        val storageGranted: Boolean = false,
        val batteryGranted: Boolean = false,
        val overlayGranted: Boolean = false,
        val usageGranted: Boolean = false,
        val notificationAccessGranted: Boolean = false,
        val shizukuGranted: Boolean = false,
        /** Shizuku's binder answers, whether or not this app holds the grant. */
        val shizukuRunning: Boolean = false,
        val micGranted: Boolean = false,
        val bluetoothGranted: Boolean = false,
    ) {
        val isAppearanceStep: Boolean get() = isOnboarding && choices.step == OnboardingState.Step.WELCOME
        val isAgreementStep: Boolean get() = isOnboarding && choices.step == OnboardingState.Step.TERMS
    }

    /** One-shot UI actions the screen itself must perform (process restart for locale). */
    sealed interface UiEvent {
        data object RestartApp : UiEvent
        /** The root request did not produce a grant; the row must not silently stay off. */
        data object RootRequestFailed : UiEvent
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val draftStore = OnboardingDraftStore(context)

    init {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val firstRun = prefs.getBoolean("is_first_run", true)
        val accepted = prefs.getBoolean("terms_accepted", false)
        val accessAnswered = GamingAccessConsent.answered(context)
        val initial = OnboardingState(
            step = when {
                !firstRun || accepted -> OnboardingState.Step.PERMISSIONS
                AppearanceStore.isChosen(context) -> OnboardingState.Step.TERMS
                else -> OnboardingState.Step.WELCOME
            },
            agreed = accepted,
            analytics = AnalyticsConsent.isEnabled(context),
            inventory = if (accessAnswered) GamingAccessConsent.allows(context, GamingAccessConsent.INVENTORY) else firstRun,
            usage = if (accessAnswered) GamingAccessConsent.allows(context, GamingAccessConsent.USAGE) else firstRun,
            notifications = if (accessAnswered) GamingAccessConsent.allows(context, GamingAccessConsent.NOTIFICATIONS) else firstRun,
        )
        _uiState.update {
            it.copy(
                isOnboarding = firstRun,
                choices = if (firstRun) draftStore.read(initial) else initial,
                themeMode = AppearanceStore.themeModeSync(context),
                languageTag = AppearanceStore.languageTag(context)
            )
        }
        viewModelScope.launch {
            shellRunner.shizukuHasPermission.collect { granted ->
                _uiState.update { it.copy(shizukuGranted = granted) }
            }
        }
    }

    fun onAgreedChange(agreed: Boolean) {
        updateChoices { copy(agreed = agreed) }
    }

    fun onAnalyticsChange(enabled: Boolean) = updateChoices { copy(analytics = enabled) }

    fun onAccessChange(key: String, selected: Boolean) {
        updateChoices {
            when (key) {
                GamingAccessConsent.INVENTORY -> copy(inventory = selected)
                GamingAccessConsent.USAGE -> copy(usage = selected)
                GamingAccessConsent.NOTIFICATIONS -> copy(notifications = selected)
                else -> this
            }
        }
        // Revocation is immediate, including while a previously enabled tool is running.
        if (!selected) GamingAccessConsent.setAllowed(context, key, false)
    }

    /** A dedicated request button consents only to this capability, not the other defaults. */
    fun confirmAccessRequest(key: String) {
        val choices = _uiState.value.choices
        val selected = when (key) {
            GamingAccessConsent.USAGE -> choices.usage
            GamingAccessConsent.NOTIFICATIONS -> choices.notifications
            else -> false
        }
        if (selected) GamingAccessConsent.setAllowed(context, key, true)
    }

    fun onBack() = updateChoices { back() }

    private fun updateChoices(change: OnboardingState.() -> OnboardingState) {
        _uiState.update { it.copy(choices = it.choices.change()) }
        saveDraft()
    }

    private fun saveDraft(commit: Boolean = false) {
        val state = _uiState.value
        if (state.isOnboarding) draftStore.save(state.choices, state.languageTag, commit)
    }

    /** Theme previews live — the flow recomposes the whole app around the onboarding. */
    fun onThemeModeChanged(mode: AppearanceStore.ThemeMode) {
        AppearanceStore.setThemeMode(context, mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    /** Commit the current step before exit: a locale restart clears Android saved state. */
    fun onLanguageChanged(tag: String) {
        val normalized = LocaleHelper.normalizeTag(tag)
        val previous = LocaleHelper.normalizeTag(AppearanceStore.languageTag(context))
        // Following system is a different preference, even when its language matches.
        if (normalized == previous) return
        val previousLanguage = LocaleHelper.effectiveLanguageTag(context, previous)
        val selectedLanguage = LocaleHelper.effectiveLanguageTag(context, normalized)
        _uiState.update { it.copy(languageTag = normalized) }
        saveDraft(commit = true)
        AppearanceStore.setLanguage(context, normalized)
        if (previousLanguage != selectedLanguage) {
            _events.tryEmit(UiEvent.RestartApp)
        }
    }

    /** Appearance already previews live; Continue only marks this step answered and advances. */
    fun onAppearanceContinue() {
        val state = _uiState.value
        AppearanceStore.setThemeMode(context, state.themeMode)
        AppearanceStore.setChosen(context)
        updateChoices { continueWelcome() }
        saveDraft(commit = true)
    }

    /** Step two's Continue: advances only when the terms checkbox is checked. */
    fun onAgreementContinue(): Boolean {
        val current = _uiState.value
        if (!current.isAgreementStep) return false
        val next = current.choices.continueTerms()
        if (next == current.choices) return false
        AnalyticsConsent.setEnabled(context, current.choices.analytics)
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("terms_accepted", true)
        }
        updateChoices { next }
        return true
    }

    /**
     * Step three's DONE. Commits the onboarding flag before navigating so a first run
     * must not re-enter onboarding, and a Settings revisit lands on the permission list.
     */
    fun onDone(onDone: () -> Unit) {
        val state = _uiState.value
        if (state.isOnboarding && !state.choices.canFinish()) return
        GamingAccessConsent.save(context, state.choices.inventory, state.choices.usage, state.choices.notifications)
        if (state.isOnboarding) {
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit(commit = true) {
                putBoolean("is_first_run", false)
            }
            draftStore.clear()
        }
        onDone()
    }

    fun refreshStates() {
        viewModelScope.launch(Dispatchers.IO) {
            val implemented = shellRunner.isRootImplemented()
            val granted = shellRunner.isRootAvailable(force = true)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(rootAvailable = implemented, rootGranted = granted) }
            }
        }
        shellRunner.refreshShizukuPermission()
        _uiState.update {
            it.copy(
                notifGranted = checkNotificationPermission(),
                storageGranted = checkStoragePermission(),
                batteryGranted = checkBatteryPermission(),
                overlayGranted = checkOverlayPermission(),
                usageGranted = checkUsagePermission(),
                notificationAccessGranted = checkNotificationAccess(),
                shizukuGranted = shellRunner.shizukuHasPermission.value,
                shizukuRunning = shellRunner.isShizukuRunning(),
                micGranted = checkMicPermission(),
                bluetoothGranted = checkBluetoothPermission()
            )
        }
    }

    private fun checkMicPermission(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun checkNotificationAccess(): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(
            ComponentName(context, GamingNotificationListener::class.java)
        )
    }.getOrDefault(false)

    private fun checkBluetoothPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true

    fun requestRootPermission() {
        if (!_uiState.value.rootAvailable) {
            _events.tryEmit(UiEvent.RootRequestFailed)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            shellRunner.allowRootRequest()
            // Build a *fresh* root shell instead of reusing libsu's cached one. A cached
            // non-root shell (e.g. from an earlier denied probe) would make `Shell.cmd`
            // reuse it silently and never re-trigger the superuser manager's prompt, so
            // the "Grant" button appeared to do nothing. A new builder always runs `su`,
            // which is what makes Magisk/KernelSU/APatch show their grant dialog.
            val granted = runCatching {
                Shell.Builder.create().setTimeout(60).build().isRoot
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(rootGranted = granted) }
                if (!granted) _events.tryEmit(UiEvent.RootRequestFailed)
            }
        }
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(0)
            }
        } catch (_: Exception) {}
    }

    private fun checkRootPermission(callback: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val isRooted = try { shellRunner.isRootAvailable(force = true) } catch (_: Exception) { false }
            withContext(Dispatchers.Main) { callback(isRooted) }
        }
    }

    private fun checkBatteryPermission(): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    private fun checkNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

    private fun checkStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            com.catsmoker.app.platform.storage.StorageAccess.hasModernAccess()
        } else {
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    private fun checkOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    private fun checkUsagePermission(): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}
