package com.catsmoker.app.features.settings

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsmoker.app.BuildConfig
import com.catsmoker.app.R
import com.catsmoker.app.system.ads.AdManager

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
class FullSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adManager: AdManager
) : ViewModel() {

    data class UiState(
        val adsEnabled: Boolean = true,
        /** True while the "don't turn ads off" guilt dialog is on screen (full variant only). */
        val showAdsDonationDialog: Boolean = false,
        val autoCheck: Boolean = false,
        val isPreRelease: Boolean = false,
        val isUpdating: Boolean = false,
        val updateProgress: Float = 0f,
        val updateDialog: UpdateDialog? = null,
        /**
         * Version name of a release the last check found, null when up to date. Drives the
         * "New" badge on the Updates card; the dialog may already have been dismissed.
         */
        val availableUpdateName: String? = null
    )

    data class UpdateDialog(
        val tagName: String,
        val downloadUrl: String?,
        val notes: String? = null,
        val sha256Hex: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private val prefs by lazy { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    private var downloadJob: kotlinx.coroutines.Job? = null

    companion object {
        /**
         * Automatic checks run at most once per process: reopening Settings
         * must not re-hit the API. Manual checks always run. This class is full-only.
         */
        private var autoCheckedThisProcess = false
        private var checkInFlight: Boolean = false

        private const val PREF_AUTO_CHECK = "auto_check_update"
        private const val PREF_PRERELEASE = "use_prerelease"
        private const val PREF_DISMISSED_TAG = "update_dismissed_tag"
        private const val PREF_PENDING_TAG = "update_pending_tag"
        /** Cached tag of the newest release found, so the badge survives process death. */
        private const val PREF_CACHED_UPDATE_TAG = "update_cached_tag"
        /** Timestamp (ms) of the last completed update check, for throttling repeats. */
        private const val PREF_LAST_CHECK_TS = "update_last_check_ts"
        /** Minimum gap between automatic checks, to avoid hammering the GitHub API. */
        private const val REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000

        /**
         * Process-wide source of truth for the found update's version name. Every
         * FullSettingsViewModel instance (home screen and Settings screen) observes it,
         * so the home indicator and the Updates card can never disagree.
         */
        private val _sharedAvailableUpdate = MutableStateFlow<String?>(null)
        val sharedAvailableUpdate: StateFlow<String?> = _sharedAvailableUpdate.asStateFlow()
    }

    init {
        val adsOn = adManager.isEnabled()
        val autoOn = prefs.getBoolean(PREF_AUTO_CHECK, true)
        _uiState.update {
            it.copy(
                adsEnabled = adsOn,
                autoCheck = autoOn,
                isPreRelease = prefs.getBoolean(PREF_PRERELEASE, false)
            )
        }
        // Seed the badge from the cached release so it shows before any network
        // round-trip, and drop a stale cache once the installed version catches up.
        val cachedTag = prefs.getString(PREF_CACHED_UPDATE_TAG, null)
        val cachedName = if (cachedTag != null) {
            if (SelfUpdater.isTagInstalled(cachedTag, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
                prefs.edit { remove(PREF_CACHED_UPDATE_TAG) }
                null
            } else {
                SelfUpdater.parseTagVersion(cachedTag).name
            }
        } else null
        if (cachedName != null && _sharedAvailableUpdate.value == null) {
            _sharedAvailableUpdate.value = cachedName
        }
        // Keep this instance's UiState in sync with the process-wide update state.
        viewModelScope.launch {
            sharedAvailableUpdate.collect { name ->
                _uiState.update { it.copy(availableUpdateName = name) }
            }
        }
        checkPendingUpdateResult()
        maybeAutoCheck(autoOn)
    }

    fun onAdsToggled(enabled: Boolean) {
        if (!enabled) {
            // Guilt-trip gate: the switch stays ON and the preference is untouched. Only the
            // dialog's explicit "turn off anyway" action applies the change, so a mis-tap can
            // never cost the developer the ads that keep the app free.
            _uiState.update { it.copy(showAdsDonationDialog = true) }
            return
        }
        adManager.setEnabled(true)
        _uiState.update { it.copy(adsEnabled = true) }
    }

    /** Dismissal — button, back press or outside tap — counts as "Keep ads". */
    fun onAdsDonationDismissed() {
        _uiState.update { it.copy(showAdsDonationDialog = false) }
    }

    /**
     * The only path that actually writes ads-off. The dialog hides this action when
     * [ALLOW_TURN_OFF_ANYWAY] is false, making a donation the sole way to stop ads.
     */
    fun onAdsTurnOffAnyway() {
        adManager.setEnabled(false)
        _uiState.update { it.copy(adsEnabled = false, showAdsDonationDialog = false) }
    }

    fun onAutoCheckToggled(enabled: Boolean) {
        prefs.edit { putBoolean(PREF_AUTO_CHECK, enabled) }
        _uiState.update { it.copy(autoCheck = enabled) }
    }

    /**
     * Opts the manual/automatic update check into GitHub pre-releases. Off by
     * default: a beta is never offered unless the user asked for one. The flag
     * is read by the next check, not retroactively applied to a download already
     * in flight.
     */
    fun onPreReleaseToggled(enabled: Boolean) {
        prefs.edit { putBoolean(PREF_PRERELEASE, enabled) }
        _uiState.update { it.copy(isPreRelease = enabled) }
    }

    /**
     * Silent automatic check, once per process: shows the dialog only for a
     * genuinely new release the user has not dismissed, and never toasts.
     * Manual checks always report. Turning the toggle off stops this path
     * entirely — nothing re-enables it.
     */
    private fun maybeAutoCheck(autoOn: Boolean) {
        if (!SelfUpdater.SUPPORTED || !autoOn) return
        if (checkInFlight) return
        val now = System.currentTimeMillis()
        // Throttle: never re-hit the API within the refresh interval, even across
        // processes/instances, so reopening the app or Settings stays cheap.
        if (now - prefs.getLong(PREF_LAST_CHECK_TS, 0L) < REFRESH_INTERVAL_MS) return
        checkInFlight = true
        autoCheckedThisProcess = true
        performUpdateCheck(_uiState.value.isPreRelease, silent = true)
    }

    /**
     * Re-evaluates the update status: syncs from the shared/cache state and
     * triggers a silent check when the last one is stale. Safe to call on app
     * start, when the Updates screen opens, and on return to the foreground.
     */
    fun refreshUpdateStatus() {
        maybeAutoCheck(_uiState.value.autoCheck)
    }

    /**
     * One-shot verdict on the installer handoff: if the version now installed
     * satisfies the pending tag, the update landed — say so once and clear the
     * marker. Otherwise clear it silently (cancelled/failed installs leave no
     * claim behind, and must not nag on every open).
     */
    private fun checkPendingUpdateResult() {
        val pending = prefs.getString(PREF_PENDING_TAG, null) ?: return
        prefs.edit { remove(PREF_PENDING_TAG) }
        if (!SelfUpdater.SUPPORTED) return
        if (SelfUpdater.isTagInstalled(pending, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
            // Say the version name, not the raw `11-2.0.5` tag.
            val installedName = SelfUpdater.parseTagVersion(pending).name
            _toasts.tryEmit(context.getString(R.string.sys_update_installed, installedName))
        }
    }

    fun onCheckUpdates() {
        if (!SelfUpdater.SUPPORTED) return
        if (checkInFlight) return
        checkInFlight = true
        _toasts.tryEmit(context.getString(R.string.sys_checking_updates))
        performUpdateCheck(_uiState.value.isPreRelease, silent = false)
    }

    /**
     * Records the dismissed tag so neither recomposition, activity recreation
     * nor the next silent auto-check re-prompts for the same release. Only a
     * strictly newer tag prompts again — and an explicit "Check for Updates"
     * always answers, because that tap is the user asking right now.
     */
    fun dismissUpdateDialog() {
        _uiState.value.updateDialog?.let { prefs.edit { putString(PREF_DISMISSED_TAG, it.tagName) } }
        _uiState.update { it.copy(updateDialog = null) }
    }

    fun startUpdateDownload() {
        if (!SelfUpdater.SUPPORTED) return
        val dialog = _uiState.value.updateDialog ?: return
        val url = dialog.downloadUrl
        _uiState.update { it.copy(updateDialog = null) }
        if (url == null) {
            _toasts.tryEmit(context.getString(R.string.sys_no_suitable_release))
            return
        }
        prefs.edit { putString(PREF_PENDING_TAG, dialog.tagName) }
        startUpdateDownload(url, dialog.sha256Hex)
    }

    /** Cancels an in-flight download; the pending marker stays until a verdict. */
    fun cancelUpdateDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.update { it.copy(isUpdating = false) }
        _toasts.tryEmit(context.getString(R.string.sys_update_cancelled))
    }

    private fun performUpdateCheck(isPreRelease: Boolean, silent: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                SelfUpdater.checkForUpdate(
                    isPreRelease, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME
                )
            } catch (e: Exception) {
                SelfUpdater.CheckResult.Failed(e.message ?: "?")
            }
            // Record that a check completed, regardless of outcome, so repeated
            // opens do not hammer the API. Cache the found release when present.
            prefs.edit {
                putLong(PREF_LAST_CHECK_TS, System.currentTimeMillis())
                when (result) {
                    is SelfUpdater.CheckResult.Available ->
                        putString(PREF_CACHED_UPDATE_TAG, result.info.tagName)
                    is SelfUpdater.CheckResult.UpToDate,
                    is SelfUpdater.CheckResult.NoReleases ->
                        remove(PREF_CACHED_UPDATE_TAG)
                    is SelfUpdater.CheckResult.Failed -> { /* keep previous cache */ }
                }
            }
            withContext(Dispatchers.Main) {
                when (result) {
                    is SelfUpdater.CheckResult.NoReleases -> {
                        _sharedAvailableUpdate.value = null
                        if (!silent) _toasts.tryEmit(context.getString(R.string.sys_no_releases))
                    }
                    is SelfUpdater.CheckResult.UpToDate -> {
                        // The badge is a claim about the latest check; an up-to-date answer
                        // retires it even when the check was silent.
                        _sharedAvailableUpdate.value = null
                        if (!silent) {
                            _toasts.tryEmit(
                                context.getString(R.string.sys_up_to_date, BuildConfig.VERSION_NAME)
                            )
                        }
                    }
                    is SelfUpdater.CheckResult.Available -> {
                        // "Later" mutes the silent auto-check only. A manual tap is an
                        // explicit question and must always be answered, or the button
                        // looks dead for the rest of that release's life.
                        val dismissed = prefs.getString(PREF_DISMISSED_TAG, null)
                        val name = SelfUpdater.parseTagVersion(result.info.tagName).name
                        if (!silent || dismissed != result.info.tagName) {
                            _sharedAvailableUpdate.value = name
                            _uiState.update {
                                it.copy(
                                    updateDialog = UpdateDialog(
                                        result.info.tagName,
                                        result.info.downloadUrl,
                                        result.info.notes,
                                        result.info.sha256Hex
                                    ),
                                    // Name the version, not the raw `11-2.0.5` tag.
                                    availableUpdateName = name
                                )
                            }
                        }
                    }
                    is SelfUpdater.CheckResult.Failed -> {
                        if (!silent) {
                            _toasts.tryEmit(
                                context.getString(R.string.sys_update_check_failed, result.message)
                            )
                        }
                    }
                }
            }
            checkInFlight = false
        }
    }

    private fun startUpdateDownload(url: String, expectedSha256: String?) {
        if (!SelfUpdater.SUPPORTED) return
        downloadJob?.cancel()
        _uiState.update { it.copy(isUpdating = true, updateProgress = 0f) }
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = SelfUpdater.downloadUpdate(context, url) { progress ->
                    _uiState.update { it.copy(updateProgress = progress) }
                }
                if (file == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isUpdating = false) }
                        _toasts.tryEmit(context.getString(R.string.sys_no_suitable_release))
                    }
                    return@launch
                }
                // Verify before the installer ever sees the file: checksum when
                // the release published one, then package id + signing cert.
                // Anything unverifiable refuses with a reason — never installs.
                val digestOk = expectedSha256 == null ||
                    SelfUpdater.sha256HexOf(file)?.equals(expectedSha256, ignoreCase = true) == true
                if (!digestOk) {
                    file.delete()
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isUpdating = false) }
                        _toasts.tryEmit(context.getString(R.string.sys_update_rejected, "checksum"))
                    }
                    return@launch
                }
                if (!SelfUpdater.isCompatibleApk(context, file)) {
                    file.delete()
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isUpdating = false) }
                        _toasts.tryEmit(context.getString(R.string.sys_update_rejected, "signature"))
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isUpdating = false) }
                    SelfUpdater.installUpdate(context, file)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                withContext(NonCancellable + Dispatchers.Main) {
                    _uiState.update { it.copy(isUpdating = false) }
                }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isUpdating = false) }
                    _toasts.tryEmit(context.getString(R.string.sys_download_failed, e.message ?: "?"))
                }
            }
        }
    }
}
