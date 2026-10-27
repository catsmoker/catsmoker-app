package com.catsmoker.app.features.editgamefiles

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsmoker.app.R
import com.catsmoker.app.features.editgamefiles.genshin.GenshinConfigTemplate
import com.catsmoker.app.features.editgamefiles.grid.GridPreferences
import com.catsmoker.app.features.editgamefiles.hsr.HsrGameManager
import com.catsmoker.app.features.editgamefiles.pubg.NewStateIniTransform
import com.catsmoker.app.features.editgamefiles.pubg.PubgSafAccess
import com.catsmoker.app.features.editgamefiles.pubg.PubgSavePatcher
import com.catsmoker.app.features.editgamefiles.pubg.PubgSaveSelection
import com.catsmoker.app.features.editgamefiles.pubg.PubgSaveVerification
import com.catsmoker.app.features.editgamefiles.pubg.PubgUploadPlan
import com.catsmoker.app.features.editgamefiles.wuwa.WuwaConfigManager
import com.catsmoker.app.shared.data.model.GameConfig
import com.catsmoker.app.shared.data.model.GameProfile
import com.catsmoker.app.shared.data.model.GameType
import com.catsmoker.app.shared.data.model.GameUploadTarget
import com.catsmoker.app.system.shell.ShellRunner
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
import rikka.shizuku.Shizuku
import java.io.File
import java.io.IOException
import java.util.EnumMap
import javax.inject.Inject

@HiltViewModel
class EditGameFilesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRunner: ShellRunner,
    private val hsrGameManager: HsrGameManager,
) : ViewModel(), Shizuku.OnRequestPermissionResultListener {

    sealed class EditEvent {
        data class Toast(val message: String, val isLong: Boolean = false) : EditEvent()
        object LaunchFilePicker : EditEvent()
        data object LaunchSafPicker : EditEvent()
        /**
         * Launches the folder picker, optionally at a starting location — [initialUri] is the
         * game's `Android/data/<package>` tree from [PubgSafAccess], null on Android 10 and
         * below where it was reachable anyway. The hint only moves the picker's initial view;
         * the grant is still exactly what the user confirms.
         */
        data class LaunchFolderPicker(val initialUri: Uri? = null) : EditEvent()
        object LaunchAllFilesAccess : EditEvent()
        object ShowZArchiverDialog : EditEvent()
    }

    /** Which card started the running job. One job at a time: the active card shows the bar + spinner, the rest only disable. */
    enum class BusyArea {
        PROFILE_PUSH,
        CUSTOM_UPLOAD,
        SAVE_READ,
        SAVE_APPLY,
        RESET,
        RESTORE,
        BACKUP_DELETE
    }

    data class UiState(
        val isLoading: Boolean = false,
        val busyArea: BusyArea? = null,
        val selectedGame: GameType = GameType.NONE,
        /**
         * Per-game install state, probed once when the screen opens — `null` = not probed yet,
         * so a chip can show "unknown" instead of guessing. The profile games used to have no
         * surface for this at all (the fact came out only when LAUNCH GAME was pressed), which
         * is why selecting a not-installed game looked identical to selecting an installed one
         * until the very last step.
         */
        val installedGames: Map<GameType, Boolean> = emptyMap(),
        val selectedProfile: Int = 0,
        /**
         * True when the selected profile's push must read the device's file first — to keep its
         * own `[BackUp DeviceProfile]` tail (PUBG's `UserCustom.ini`) or to patch the save's
         * fields in place (`Active.sav` profiles). The method chooser hides ZARCHIVER while set:
         * a manual copy cannot read the current file, so it could neither preserve that section
         * nor patch the values the profile names.
         */
        val selectedProfileNeedsExistingFile: Boolean = false,
        /** String-resource IDs of the selected game's profile labels — resolved in Compose so a language switch re-renders them (a resolved string cached here would survive `recreate()` in the old language). */
        val profileLabelResIds: List<Int> = emptyList(),
        /**
         * The selected game's own config-file name for UI labels — the reset button and its
         * dialog once hardcoded "ACTIVE.SAV" and showed it under Genshin too. Null when no
         * game is selected; the reset button itself is hidden when the game has no reset.
         */
        val configFileLabel: String? = null,
        /**
         * The selected game's package name, so the GAME NOT FOUND card can name the exact
         * package the probe looked for instead of a one-size-fits-all claim. The card's
         * message once hardcoded "no PUBG-family package …" and said it under Genshin, whose
         * probe never looks at a PUBG package at all. Null for the embedded editors (their
         * packages resolve inside their own managers) — they render their own failure cards.
         */
        val gamePackageName: String? = null,
        val canReset: Boolean = false,
        /** Custom upload is a PUBG `Active.sav` affordance — hidden for games with templated configs. */
        val canUploadCustom: Boolean = false,
        /** Supported custom-file types for the selected game, in display order. */
        val uploadTargets: List<GameUploadTarget> = emptyList(),
        /** Index into [uploadTargets]; changing it clears the picked file. */
        val selectedUploadTarget: Int = 0,
        /** Resolved destination of the selected type, shown before the user commits. */
        val uploadDestination: String? = null,
        /** The inline save editor exists for this game — PUBG's verified GVAS layout only. */
        val canPatchSave: Boolean = false,
        /**
         * True when the selected game's file opens only under root
         * ([GameConfig.rootOnlyIniPath] — PUBG: New State, backlog item M168): the method chooser never
         * appears, the profile card carries the root note, and the restore chooser offers only
         * the root channel.
         */
        val rootOnlyIni: Boolean = false,
        /**
         * What the game's save actually holds, read through the editor's channel. Null until a
         * read succeeds; the editor's chips stay disabled until then — it edits the values the
         * file carries, never assumed defaults.
         */
        val saveRead: PubgSavePatcher.ReadResult? = null,
        /**
         * The last apply's read-back — what the file holds *now*, per the house rule that the
         * device's own answer is the report. Null when nothing has been applied yet.
         */
        val savePatchReport: String? = null,
        /** The save editor's pending edits, keyed by property name — chip selections before APPLY. */
        val saveEdits: Map<String, Int> = emptyMap(),
        /** Display name of the picked import file; null until one is chosen. */
        val selectedFileName: String? = null,
        val showMethodChooser: Boolean = false,
        val showResetChooser: Boolean = false,
        /** Saved backups of the selected game's config, newest first — populated when the dialog opens. */
        val backups: List<ConfigBackupStore.Entry> = emptyList(),
        val showBackupDialog: Boolean = false,
        val showRestoreChooser: Boolean = false,
        /**
         * "Show anyway" override for a confirmed-missing install. False until the user taps
         * the small secondary button on the GAME NOT FOUND card — then the profile editor
         * renders below the card even though the probe still says absent. Reset on every
         * game selection (each game needs its own explicit tap); re-probes never clear it,
         * so installing/uninstalling while the screen sits open keeps the user's choice.
         * The card itself stays visible while set — the screen never pretends the game is
         * installed.
         */
        val showEditorAnyway: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<EditEvent> = _events.asSharedFlow()

    private val gameConfigs: MutableMap<GameType, GameConfig> = EnumMap(GameType::class.java)
    private val customUploadPrefs by lazy { context.getSharedPreferences("custom_upload_prefs", Context.MODE_PRIVATE) }

    private var selectedFileUri: Uri? = null
    private var selectedAssetPath: String? = null
    private var pendingAction: (() -> Unit)? = null
    private var customPackagePending: String? = null
    private var pendingRestore: ConfigBackupStore.Entry? = null

    private val backupStore by lazy { ConfigBackupStore(context) }

    init {
        initializeGameConfigs()
        probeInstallStates()
        Shizuku.addRequestPermissionResultListener(this)
    }

    override fun onCleared() {
        Shizuku.removeRequestPermissionResultListener(this)
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            checkAndStartShizukuAction()
        } else {
            showSnackbar(context.getString(R.string.gf_shizuku_denied))
        }
    }

    private fun initializeGameConfigs() {
        // PUBG-family variants share the same `ShadowTrackerExtra` save layout, so one config
        // builder serves them all. The package set is the variants the reference
        // `reference/gamingtools/BattleGrounds_GFX-main` writes `Active.sav` to — a working
        // tool that verifies the folder the user picks, so its list is verified evidence of
        // which packages actually carry that path. Those same variants carry the eu GFX
        // tool's `UserCustom.ini` in the shared `ShadowTrackerExtra` tree — [buildPubgConfig]
        // offers it as a third profile. `com.pubg.newstate` is a different UE4 project with
        // its own layout and gets its own root-only config ([buildNewStateConfig]) instead.
        val pubgGames = listOf(
            GameType.PUBG_GLOBAL to "com.tencent.ig",
            GameType.PUBG_KRJP to "com.pubg.krmobile",
            GameType.PUBG_VN to "com.vng.pubgmobile",
            GameType.BGMI to "com.pubg.imobile",
            GameType.PUBG_REKOO to "com.rekoo.pubgm",
            GameType.PUBG_FLASH to "com.tencent.igfit"
        )
        pubgGames.forEach { (type, pkg) ->
            gameConfigs[type] = buildPubgConfig(pkg)
        }
        gameConfigs[GameType.GENSHIN_IMPACT] = buildGenshinConfig()
        gameConfigs[GameType.PUBG_NEW_STATE] = buildNewStateConfig()
    }

    private fun buildPubgConfig(packageName: String): GameConfig {
        return GameConfig(
            packageName = packageName,
            saveDir = PubgUploadPlan.saveDir(packageName),
            saveFile = "Active.sav",
            profiles = listOf(
                // The bundled saves are whole saves from the reference tools, but a player's
                // Active.sav already holds their own sensitivity and layout. What each profile
                // is actually about is patchable in place (ProfilePushPlan), so these edit maps
                // are its payload; the blob only seeds a save the game has not written yet.
                GameProfile(
                    R.string.gf_profile_unlock_120,
                    "PUBG/MaxFPS/Active.sav",
                    saveEdits = PubgSavePatcher.fpsEdits(120)
                ),
                GameProfile(
                    R.string.gf_profile_tablet,
                    "PUBG/TabletView/Active.sav",
                    saveEdits = PubgSavePatcher.viewEdits(PubgSavePatcher.TABLET_VIEW)
                ),
                // The eu tool's own file: `UserCustom.ini` sits in
                // `Saved/Config/Android/`, not `SaveGames/`, so the profile carries its own
                // target — and the game keeps its previous device profile in a
                // `[BackUp DeviceProfile]` tail the push must carry over.
                GameProfile(
                    R.string.gf_profile_user_custom,
                    "PUBG/UserCustom/DeviceProfile.ini",
                    targetDir = PubgUploadPlan.userCustomDir(packageName),
                    targetFile = "UserCustom.ini",
                    preserveDeviceProfileBackup = true
                )
            ),
            resetFilePath = "/storage/emulated/0" + PubgUploadPlan.saveDir(packageName) + "Active.sav",
            // Custom upload accepts the two files this editor really understands; the chosen
            // type resolves its own package-specific destination.
            allowCustomUpload = true,
            uploadTargets = PubgUploadPlan.targets(packageName),
            // The save is a verified GVAS layout, so the inline read-modify-write editor is
            // available too — the closed-source references' core mechanism (their savedit.sh
            // sed-rewrites these same ints inside the user's own save).
            supportsSavePatching = true
        )
    }

    /**
     * Genshin Impact, from the reference project `GenshinConfig-main`.
     *
     * The game reads `hardware_model_config.json` out of its own `files/` directory and looks the
     * entries up by the device's real model, so the tuned entry in the bundled template carries a
     * placeholder model that every delivery channel substitutes with `Build.MODEL` — the reference
     * README's manual instruction ("`Your Device Model` must match the model of the device you are
     * using"), automated. The asset is the reference's file byte-for-byte; it is deliberately not
     * valid JSON (unquoted `ASTC`, `01`, `00000001h`, trailing commas), and the game's lenient
     * parser accepts it exactly as shipped — so it is never parsed or re-serialised on the way
     * through, for the same reason the PUBG `Active.sav` blobs are shipped opaque.
     */
    private fun buildGenshinConfig(): GameConfig {
        return GameConfig(
            packageName = "com.miHoYo.GenshinImpact",
            saveDir = "/Android/data/com.miHoYo.GenshinImpact/files/",
            saveFile = "hardware_model_config.json",
            profiles = listOf(
                GameProfile(R.string.gf_profile_genshin, "Genshin/hardware_model_config.json")
            ),
            requiresDeviceModel = true
        )
    }

    /**
     * PUBG: New State, from the New State branch of the reference
     * `reference/Magisk-Modules/Unlocker-p4` (backlog item M168). The game keeps `GameUserSettings.ini`
     * inside its private data directory, which neither SAF nor a Shizuku shell can reach — so
     * this config ships no asset at all: APPLY runs [NewStateIniTransform] over the bytes the
     * device already holds, through root. `saveDir`/`saveFile` are labels for the UI; the
     * reset, custom-upload and save-editor channels stay off, and the restore channel targets
     * the same path. The module's non-INI lines (its concealment commands) are a rejected
     * pattern and stay out (closed item M170).
     */
    private fun buildNewStateConfig(): GameConfig {
        return GameConfig(
            packageName = NewStateIniTransform.PACKAGE,
            saveDir = NewStateIniTransform.CONFIG_DIR,
            saveFile = NewStateIniTransform.CONFIG_FILE,
            profiles = listOf(
                GameProfile(R.string.gf_profile_newstate, "")
            ),
            rootOnlyIniPath = NewStateIniTransform.CONFIG_DIR + NewStateIniTransform.CONFIG_FILE
        )
    }

    /**
     * One package-manager probe per game, off the main thread, so every picker row can show
     * install state up front. Public: the ON_RESUME hook and the GAME NOT FOUND card's
     * Re-check button re-run it — the probe ran once per view model for most of this screen's
     * life, which made the dots lie after an install/uninstall while the app stayed open: the
     * user installs the game, comes back, and the red dot claims it is still missing.
     * [PackageManager.getPackageInfo] — the same check WuWa's own
     * editor makes (`WuwaConfigViewModel.refresh`), not `getLaunchIntentForPackage`: a
     * launch intent can be null for a real install (games that ship no MAIN launcher
     * activity, or launcher-disabled installs), and the dot must not call that "not
     * installed". What a game without a launch intent *does* break is launching, which is
     * why the launch button's own probe stays `getLaunchIntentForPackage` — it reports the
     * device's honest "Game not installed" when the launcher is what's missing.
     *
     * Package ownership: every game's package set has one owner. Profile games and Genshin
     * carry theirs in [gameConfigs]; WuWa's and GRID's live as constants in their editors
     * ([WuwaConfigManager.PACKAGE], [GridPreferences.PACKAGE]); HSR's five variants live only
     * in [HsrGameManager] — the manager answers the probe itself. The root-only dynamic sweep
     * (HSR store variants beyond the known five) stays the editor's load-time probe; a variant
     * found only there shows "unknown" here rather than a false "not installed".
     */
    fun probeInstallStates() {
        viewModelScope.launch(Dispatchers.IO) {
            val states: Map<GameType, Boolean> = gameConfigs.keys.plus(EMBEDDED_EDITOR_GAMES).mapNotNull { type ->
                val installed: Boolean? = when (type) {
                    // HSR's variant list has one owner: the editor's manager, which answers
                    // the probe itself. The root-only dynamic sweep (unknown store variants)
                    // stays the editor's load-time probe; a variant found only there shows
                    // "unknown" here, never a false "no".
                    GameType.HSR -> hsrGameManager.isAnyKnownVariantInstalled()
                    // WuWa and GRID resolve their single packages at editor load; the package
                    // constants live in the same features, so the dots can answer here too.
                    GameType.WUWA -> probePackage(WuwaConfigManager.PACKAGE)
                    GameType.GRID -> probePackage(GridPreferences.PACKAGE)
                    // Everything else carries exactly one package in its config; NONE has no
                    // config and gets no dot.
                    else -> gameConfigs[type]?.packageName?.let { probePackage(it) }
                }
                if (installed == null) return@mapNotNull null
                type to installed
            }.toMap()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(installedGames = states) }
            }
        }
    }

    /**
     * One package-manager probe: true = installed, false = query answered and the package is
     * not there, null = the query threw (package-visibility filtering) — "unknown", never a
     * false "not installed". This is what lets a dot say "I don't know" instead of guessing.
     */
    private fun probePackage(pkg: String): Boolean? = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (_: Exception) {
        null
    }

    /**
     * Selecting a game. Editor-backed entries (HSR, WuWa, GRID — [GameType.embeddedEditor])
     * select like any other game: the screen swaps its body to that game's inline editor, and
     * every profile-push control below is hidden for them, because their editors own their
     * games' files and pushing a bundled template over them is exactly what those games must
     * not get.
     */
    fun onGameSelected(game: GameType) {
        val config = gameConfigs[game]
        val uploadTargets = config?.uploadTargets.orEmpty()
        selectedFileUri = null
        _uiState.update {
            it.copy(
                selectedGame = game,
                selectedProfile = 0,
                selectedProfileNeedsExistingFile =
                    ProfilePushPlan.needsExistingFile(config?.profiles?.firstOrNull()),
                profileLabelResIds = config?.profiles?.map(GameProfile::labelRes).orEmpty(),
                configFileLabel = config?.resetFileLabel,
                // The exact package the GAME NOT FOUND card should name — the one the probe
                // actually checked, not a family-level guess.
                gamePackageName = config?.packageName,
                canReset = config?.resetFilePath != null,
                canUploadCustom = config?.allowCustomUpload == true,
                uploadTargets = uploadTargets,
                selectedUploadTarget = 0,
                uploadDestination = uploadTargets.firstOrNull()
                    ?.destination(Environment.getExternalStorageDirectory().path),
                selectedFileName = null,
                canPatchSave = config?.supportsSavePatching == true,
                rootOnlyIni = config?.rootOnlyIniPath != null,
                // A fresh selection starts the editor from "read the file first" — a stale
                // read from the previously selected game would describe the wrong file.
                saveRead = null,
                savePatchReport = null,
                saveEdits = emptyMap(),
                // Each game needs its own explicit "Show anyway" tap — a carried-over
                // override would reveal a different game's editor unasked.
                showEditorAnyway = false
            )
        }
    }
    fun onProfileSelected(profile: Int) {
        val needsExisting = ProfilePushPlan.needsExistingFile(
            gameConfigs[_uiState.value.selectedGame]?.profiles?.getOrNull(profile)
        )
        _uiState.update { it.copy(selectedProfile = profile, selectedProfileNeedsExistingFile = needsExisting) }
    }

    /**
     * The GAME NOT FOUND card's small secondary "Show anyway" action: reveals the profile
     * editor below the card even though the probe says absent. One-way — once shown it
     * stays shown until another game is selected (which resets it) or the screen's
     * ViewModel is cleared. Installed/unknown games never need it; the screen only offers
     * the button when the probe confirmed "no".
     */
    fun onShowEditorAnyway() = _uiState.update { it.copy(showEditorAnyway = true) }

    fun onApplyProfile() {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        // A root-only config (New State) has no asset to push and no channel but root: run its
        // transform directly, never the method chooser — none of its entries could reach the
        // file, and offering them would be a promise they cannot keep.
        if (config.rootOnlyIniPath != null) {
            applyRootOnlyIni(config)
            return
        }
        if (setSelectedAssetPathFromProfile()) {
            _uiState.update { it.copy(showMethodChooser = true) }
        }
    }

    // ------------------------------------------------------------- save editor (PUBG)

    /**
     * Reads the game's save through root or Shizuku and reports what it holds. The editor edits
     * only values it has seen in the file, so this runs before the chips enable — the same
     * read-back-first discipline Gaming Mode uses before it writes anything.
     */
    fun onReadSave() {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        if (config.packageName !in PUBG_PACKAGES) return
        launchIoWithLoading(BusyArea.SAVE_READ) {
            val bytes = pullSaveBytes(config) ?: return@launchIoWithLoading true
            val read = PubgSavePatcher.read(bytes)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        saveRead = read,
                        saveEdits = emptyMap(),
                        savePatchReport = null
                    )
                }
                when {
                    !read.isGvas -> showSnackbar(context.getString(R.string.gf_not_pubg_save))
                    else -> showSnackbar(read.summary())
                }
            }
            true
        }
    }

    /** Selects or clears one tier's edit set from the editor's chips. */
    fun onSaveEditSelected(name: String, value: Int?) {
        _uiState.update { state ->
            val edits = when {
                value == null -> PubgSaveSelection.clear(state.saveEdits, name)
                name == PubgSavePatcher.FIELD_BATTLE_FPS ->
                    PubgSaveSelection.toggleFps(state.saveRead, state.saveEdits, value)
                name == PubgSavePatcher.FIELD_BATTLE_RENDER ->
                    PubgSaveSelection.toggleRender(state.saveRead, state.saveEdits, value)
                name == PubgSavePatcher.FIELD_TP_VIEW -> {
                    // Both camera fields move together — a lone TpViewValue beside the file's
                    // own FpViewValue would be a half-identity the references never ship.
                    val preset = PubgSavePatcher.VIEW_PRESETS.firstOrNull { it.tpView == value }
                    if (preset != null) {
                        PubgSaveSelection.toggleView(state.saveRead, state.saveEdits, preset)
                    } else {
                        state.saveEdits
                    }
                }
                else -> state.saveEdits + (name to value)
            }
            state.copy(saveEdits = edits)
        }
    }

    /**
     * Applies the selected edits to the save: pull, back up, patch, push, read back. All four
     * steps run through the same privileged channel as every other overwrite here — pull and
     * push via the direct binder channel (root cp fallback), the patch itself in-process
     * ([PubgSavePatcher]), and the pre-write backup through [ConfigBackupStore] like every
     * other write this screen makes.
     */
    fun onApplySaveEdits() {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        val edits = _uiState.value.saveEdits
        if (edits.isEmpty()) {
            showSnackbar(context.getString(R.string.gf_pick_tier_first))
            return
        }
        launchIoWithLoading(BusyArea.SAVE_APPLY) {
            val original = pullSaveBytes(config)
                ?: return@launchIoWithLoading true // the pull already reported its failure
            val patched = when (val result = PubgSavePatcher.patch(original, edits)) {
                is PubgSavePatcher.PatchResult.Ok -> result
                is PubgSavePatcher.PatchResult.Refused -> {
                    withContext(Dispatchers.Main) {
                        showSnackbar(context.getString(R.string.gf_save_changed,
                            result.reasons.entries.joinToString(" · ") { "${it.key} — ${it.value}" }))
                    }
                    return@launchIoWithLoading true
                }
            }
            // No undo point means no write: the patched save is about to replace the only copy
            // of the player's file, and ConfigBackupStore.save reports failure instead of
            // pretending a backup exists.
            if (backupStore.save(config.packageName, config.saveFile, original) == null) {
                showSnackbar(context.getString(R.string.gf_backup_failed_refused))
                return@launchIoWithLoading false
            }
            if (!pushSaveBytes(config, patched.data)) return@launchIoWithLoading false

            // The push is not the report: read the file back, then compare what it holds with
            // what was requested. The old code printed the read-back summary next to "applied"
            // even when the values were still the old ones, and cleared the chips as if the
            // request had landed.
            val outcome = pullSaveOutcome(config)
            if (outcome is PullOutcome.NoChannel && Shizuku.pingBinder()) {
                shellRunner.markShizukuServiceUnreachable()
            }
            val readBack = (outcome as? PullOutcome.Ok)?.bytes?.let { PubgSavePatcher.read(it) }
            val mismatches = readBack?.let { PubgSaveVerification.mismatches(it, edits) }.orEmpty()
            val verified = readBack != null && mismatches.isEmpty()
            // Cold-start choreography (the reference's push-then-start shape): PUBG reads
            // Active.sav at launch, so a running game would keep playing the old values —
            // and overwrite the file with them on exit. Stop it after a verified push; the
            // manual LAUNCH GAME button cold-starts it. Best-effort: a refused stop keeps
            // the applied values (reported below), it just needs a manual restart.
            val stopped = if (verified) {
                shellRunner.execSafeResult("am", "force-stop", config.packageName).isSuccess
            } else {
                false
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        saveRead = readBack ?: it.saveRead,
                        // Only a verified apply clears the chips: a mismatch keeps the request
                        // visible so it can be retried instead of silently lost.
                        saveEdits = if (verified) emptyMap() else it.saveEdits,
                        savePatchReport = readBack?.summary()
                    )
                }
                when {
                    readBack == null -> showSnackbar(
                        if (outcome is PullOutcome.Missing) {
                            context.getString(R.string.gf_written_no_readback)
                        } else {
                            context.getString(R.string.gf_no_privileged_channel)
                        }
                    )
                    mismatches.isNotEmpty() -> showSnackbar(
                        context.getString(
                            R.string.gf_save_verify_failed,
                            mismatches.joinToString(" · ") { "${it.field}: ${it.actual ?: "—"} ≠ ${it.expected}" }
                        )
                    )
                    else -> {
                        val applied = context.getString(R.string.gf_applied_holds, readBack.summary())
                        showSnackbar(
                            if (stopped) {
                                "$applied ${context.getString(R.string.gf_game_stopped_relaunch)}"
                            } else {
                                applied
                            }
                        )
                    }
                }
            }
            true
        }
    }

    /**
     * The save's bytes, or null with the failure already reported. Two distinct nulls:
     * [PullOutcome.Missing] means the privileged channel answered and the file is not there
     * (the game has not created it yet); [PullOutcome.NoChannel] means no privileged channel
     * could answer at all. The old single message collapsed both into "launch the game once",
     * which read as nonsense on a device where root and Shizuku were both gone.
     */
    private sealed interface PullOutcome {
        // Bytes are carried by identity; content equality is defined explicitly so
        // a data-class copy never compares array references.
        data class Ok(val bytes: ByteArray) : PullOutcome {
            override fun equals(other: Any?): Boolean = other is Ok && bytes.contentEquals(other.bytes)
            override fun hashCode(): Int = bytes.contentHashCode()
        }
        data object Missing : PullOutcome
        data object NoChannel : PullOutcome
    }

    private suspend fun pullSaveOutcome(config: GameConfig): PullOutcome {
        val destPath = (Environment.getExternalStorageDirectory().path + config.saveDir) + config.saveFile

        // Direct binder read first: one call inside the service's own process. This removed
        // the multi-second apply on Shizuku devices, where every `cp` used to cost a fork and
        // the first one after a cold start additionally the user-service spawn. forceBind
        // clears a stale block-cache first — an explicit user action deserves one real
        // attempt, not the cached memory of a previous failure. When the user service itself
        // cannot start (wedged daemon, version-gated restart), readFileDirect still answers
        // through the reference's remote-shell channel, so this line no longer collapses to
        // null just because a helper refused to spawn. Its empty answer is the file's absence
        // — the existence probe every write below is gated on.
        shellRunner.readFileDirect(destPath, forceBind = true)?.let { bytes ->
            return if (bytes.isNotEmpty()) PullOutcome.Ok(bytes) else PullOutcome.Missing
        }

        // Root path (readFileDirect returns null without any Shizuku channel): a straight
        // read through the root shell's own process.
        if (shellRunner.isRootAvailable()) {
            val pull = withContext(Dispatchers.IO) {
                File.createTempFile("pubgread_", "_" + config.saveFile, context.externalCacheDir ?: context.cacheDir)
            }
            try {
                pull.delete()
                val result = shellRunner.execSafeResult("cp", "-f", destPath, pull.absolutePath)
                if (result.isSuccess && pull.exists() && pull.length() > 0L) {
                    return PullOutcome.Ok(pull.readBytes())
                }
                return PullOutcome.Missing
            } finally {
                pull.delete()
            }
        }

        // Every Shizuku channel answered with null and root is absent. This is now genuinely
        // "no channel": the binder does not answer, the permission is missing, or the remote
        // process itself refused — not merely "the helper did not start".
        return PullOutcome.NoChannel
    }

    private suspend fun pullSaveBytes(config: GameConfig): ByteArray? {
        val outcome = pullSaveOutcome(config)
        val bytes: ByteArray? = when (outcome) {
            is PullOutcome.Ok -> outcome.bytes
            PullOutcome.Missing -> {
                withContext(Dispatchers.Main) {
                    showSnackbar(
                        context.getString(R.string.gf_save_not_created, config.saveFile, config.saveDir)
                    )
                }
                null
            }
            PullOutcome.NoChannel -> {
                withContext(Dispatchers.Main) {
                    showSnackbar(
                        if (Shizuku.pingBinder()) context.getString(R.string.gf_shizuku_channel_unavailable)
                        else context.getString(R.string.gf_no_privileged_channel)
                    )
                }
                null
            }
        }
        // The reference tool reports its Shizuku failures through the command's own exit code,
        // never through a helper-lifecycle state; mirror that by dropping the stale proxy when
        // a read came back channel-less while the binder still pings, so the next attempt
        // starts from a fresh bind rather than a cached dead one.
        if (outcome is PullOutcome.NoChannel && Shizuku.pingBinder()) shellRunner.markShizukuServiceUnreachable()
        return bytes
    }

    /**
     * Writes [bytes] over the game's save and reports what storage actually holds, not what
     * was sent: the direct binder channel's own flush-and-sync answer first, else the cp
     * exit code over root, else the shell fallback — each verified by a read-back at the
     * caller ([onApplySaveEdits]).
     */
    private suspend fun pushSaveBytes(config: GameConfig, bytes: ByteArray): Boolean {
        val destDir = Environment.getExternalStorageDirectory().path + config.saveDir
        val destPath = destDir + config.saveFile

        // Direct binder write: the service mkdirs, writes, flushes and fsyncs inside its own
        // process — one call, and the returned boolean is the report. When the helper will
        // not start, writeFileDirect falls through to the remote-shell channel (staged in
        // /data/local/tmp, `cp`'d over — the reference's own staging shape), so a wedged
        // daemon no longer ends the apply.
        val direct = shellRunner.writeFileDirect(destPath, bytes)
        if (direct == true) return true
        if (direct == false) {
            withContext(Dispatchers.Main) {
                showSnackbar(context.getString(R.string.gf_write_refused, destPath))
            }
            return false
        }
        // null = no Shizuku channel answered; fall through to the root channel.

        if (shellRunner.isRootAvailable()) {
            val pushFile = withContext(Dispatchers.IO) {
                File.createTempFile("pubgpush_", "_" + config.saveFile, context.externalCacheDir ?: context.cacheDir)
            }
            try {
                pushFile.writeBytes(bytes)
                val mkdir = shellRunner.execSafeResult("mkdir", "-p", destDir)
                if (!mkdir.isSuccess) {
                    withContext(Dispatchers.Main) {
                        showSnackbar(context.getString(R.string.gf_mkdir_failed, mkdir.exitCode, destDir))
                    }
                    return false
                }
                val result = shellRunner.execSafeResult("cp", "-f", pushFile.absolutePath, destPath)
                if (!result.isSuccess) {
                    withContext(Dispatchers.Main) {
                        showSnackbar(context.getString(R.string.gf_cp_failed, result.exitCode, result.stderr.ifBlank { context.getString(R.string.gf_no_output) }))
                    }
                    return false
                }
                return true
            } finally {
                pushFile.delete()
            }
        }

        val pushFile = withContext(Dispatchers.IO) {
            File.createTempFile("pubgpush_", "_" + config.saveFile, context.externalCacheDir ?: context.cacheDir)
        }
        try {
            pushFile.writeBytes(bytes) // written fresh by this app, so no privileged-uid leftover sits on it
            shellRunner.execSafe("mkdir", "-p", destDir)
            val result = shellRunner.execSafeResult("cp", "-f", pushFile.absolutePath, destPath)
            if (!result.isSuccess) {
                withContext(Dispatchers.Main) {
                    showSnackbar(context.getString(R.string.gf_cp_failed, result.exitCode, result.stderr.ifBlank { context.getString(R.string.gf_no_output) }))
                }
                return false
            }
            return true
        } finally {
            pushFile.delete()
        }
    }

    fun dismissMethodChooser() = _uiState.update { it.copy(showMethodChooser = false) }

    /**
     * Entry point for the reset channel: deletes the game's own `Active.sav` so
     * it regenerates one from defaults on the next launch — the clean revert for everything
     * this screen pushed, which the reference implements as `deleteActiveSavWithShizuku()` /
     * `deleteActiveSavWithSAF()` in `reference/gamingtools/BattleGrounds_GFX-main`.
     */
    fun onResetSave() {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        if (config.resetFilePath == null) {
            showSnackbar(context.getString(R.string.gf_not_reset_deletable))
            return
        }
        _uiState.update { it.copy(showResetChooser = true) }
    }

    fun dismissResetChooser() = _uiState.update { it.copy(showResetChooser = false) }

    fun onResetMethodSelected(which: Int) {
        _uiState.update { it.copy(showResetChooser = false) }
        when (which) {
            0 -> performRootAction { resetViaShizuku() }
            1 -> resetViaShizuku()
            2 -> resetViaSaf()
        }
    }

    /**
     * Runs [action] through the privileged shell when root is present — the method chooser's
     * "ROOT" entry — falling back to the Shizuku path when it is not. Kept as a gate rather
     * than a separate copy of every action so each channel keeps its own single implementation.
     */
    private fun performRootAction(action: () -> Unit) {
        if (!shellRunner.isRootAvailable(force = true)) {
            showSnackbar(context.getString(R.string.gf_root_fallback))
            action()
            return
        }
        action()
    }

    private fun resetViaShizuku() {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        val path = config.resetFilePath ?: return
        if (!Shizuku.pingBinder()) {
            showSnackbar(context.getString(R.string.gf_shizuku_not_running))
            return
        }
        launchIoWithLoading(BusyArea.RESET) {
            // Existence probe first: rm -f succeeds on a file that is not there, so the old
            // exit-code-only report announced "deleted" for a file that never existed. The
            // empty read answers "not there" and gets its own message — "nothing to reset",
            // matching what the SAF reset path already reported.
            val existing = shellRunner.readFileDirect(path, forceBind = true)
            val message = when {
                existing == null ->
                    if (Shizuku.pingBinder()) context.getString(R.string.gf_shizuku_channel_unavailable_short)
                    else context.getString(R.string.gf_no_privileged_channel)
                existing.isEmpty() -> context.getString(R.string.gf_not_there_nothing, config.resetFileLabel)
                else -> {
                    // Backup-before-reset: every overwrite stashes the pre-write bytes, and a
                    // reset that deletes without stashing would be the one write the restore
                    // path cannot undo. A failed backup aborts the delete — same contract as
                    // the overwrite channels — rather than deleting unrecoverably.
                    val backedUp = backupStore.save(config.packageName, File(path).name, existing) != null
                    if (!backedUp) {
                        context.getString(R.string.gf_reset_backup_failed, config.resetFileLabel)
                    } else {
                        val result = shellRunner.execSafeResult("rm", "-f", path)
                        when {
                            result.isSuccess ->
                                context.getString(R.string.gf_deleted_rebuilds, config.resetFileLabel)
                            else -> context.getString(R.string.gf_rm_failed, result.exitCode, result.stderr.ifBlank { context.getString(R.string.gf_no_output) })
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) { showSnackbar(message) }
            true
        }
    }

    private fun resetViaSaf() {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        if (config.resetFilePath == null) return
        val treeUri = getCustomGameTreeUri(config.packageName)
        if (treeUri != null) {
            performSafReset(treeUri, config)
        } else {
            // Deleting needs a persisted tree grant for the game's data folder — same shape as
            // the custom-upload channel: remember the package, park the real work in
            // pendingAction, and onCustomFolderPicked runs it once the grant exists.
            customPackagePending = config.packageName
            pendingAction = {
                getCustomGameTreeUri(config.packageName)?.let { performSafReset(it, config) }
            }
            _events.tryEmit(EditEvent.LaunchFolderPicker(PubgSafAccess.initialFolderUri(config.packageName, Build.VERSION.SDK_INT)))
        }
    }

    private fun performSafReset(treeUri: Uri, config: GameConfig) {
        val path = config.resetFilePath ?: return
        launchIoWithLoading(BusyArea.RESET) {
            when (deleteSafDocument(treeUri, config, path)) {
                SafDeleteResult.DELETED -> showSnackbar(context.getString(R.string.gf_deleted_rebuilds, config.resetFileLabel))
                SafDeleteResult.NOT_FOUND -> showSnackbar(context.getString(R.string.gf_not_found_nothing, config.resetFileLabel))
                SafDeleteResult.BACKUP_FAILED -> showSnackbar(context.getString(R.string.gf_reset_backup_failed, config.resetFileLabel))
                SafDeleteResult.FAILED -> showSnackbar(context.getString(R.string.gf_provider_refused_delete))
            }
            true
        }
    }

    /** Keeps the four SAF outcomes distinct rather than collapsing to a boolean. */
    private enum class SafDeleteResult { DELETED, NOT_FOUND, BACKUP_FAILED, FAILED }

    private fun deleteSafDocument(
        treeUri: Uri,
        config: GameConfig,
        absolutePath: String
    ): SafDeleteResult {
        // The tree grant is rooted at the game's Android/data/<pkg> folder, and resetFilePath is
        // absolute from /storage/emulated/0 — strip the game-data prefix to get the document-id
        // tail, the same construction the reference builds from its picked folder id.
        val gameDataRoot = "/storage/emulated/0/Android/data/${config.packageName}"
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri) +
                absolutePath.removePrefix(gameDataRoot)
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val exists = DocumentFile.fromSingleUri(context, uri)?.exists() == true
            if (!exists) return SafDeleteResult.NOT_FOUND
            // Backup-before-reset, same contract as the shell channel: read the current bytes
            // through the grant and stash them first; an unreadable file or a failed stash
            // aborts the delete rather than deleting unrecoverably.
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null || bytes.isEmpty()) return SafDeleteResult.BACKUP_FAILED
            if (backupStore.save(config.packageName, File(absolutePath).name, bytes) == null) {
                return SafDeleteResult.BACKUP_FAILED
            }
            if (DocumentsContract.deleteDocument(context.contentResolver, uri)) SafDeleteResult.DELETED
            else SafDeleteResult.FAILED
        } catch (_: Exception) {
            SafDeleteResult.FAILED
        }
    }

    /**
     * Backup & restore channel. Every overwrite this screen performs first saves
     * the game's current file through [ConfigBackupStore]; this is the way back. From the
     * references' safety-backup systems — `hsrgraphicdroid-main` ("Safety Backup System") and
     * `WuWa-Config-Android-main/config/BackupStore.kt` — which both restore by pushing the saved
     * bytes back through the *same* channel that overwrote them, never by a side door.
     */
    fun onRestoreBackup() {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        val backups = backupStore.list(config.packageName)
        if (backups.isEmpty()) {
            showSnackbar(context.getString(R.string.gf_no_backups))
            return
        }
        _uiState.update { it.copy(backups = backups, showBackupDialog = true) }
    }

    fun dismissBackupDialog() = _uiState.update { it.copy(showBackupDialog = false) }

    fun onBackupChosen(entry: ConfigBackupStore.Entry) {
        _uiState.update { it.copy(showBackupDialog = false, showRestoreChooser = true) }
        pendingRestore = entry
    }

    fun dismissRestoreChooser() {
        pendingRestore = null
        _uiState.update { it.copy(showRestoreChooser = false) }
    }

    fun onRestoreMethodSelected(which: Int) {
        _uiState.update { it.copy(showRestoreChooser = false) }
        val entry = pendingRestore ?: return
        pendingRestore = null
        // A root-only config has exactly one channel — its file is another app's private
        // data — so every choice lands on the root restore, which is also the only button the
        // screen offers for it.
        val config = gameConfigs[_uiState.value.selectedGame]
        if (config != null && config.rootOnlyIniPath != null) {
            restoreRootOnlyIni(config, entry)
            return
        }
        when (which) {
            0 -> performRootAction { restoreViaShizuku(entry) }
            1 -> restoreViaShizuku(entry)
            2 -> restoreViaSaf(entry)
        }
    }

    fun onDeleteBackup(entry: ConfigBackupStore.Entry) {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        launchIoWithLoading(BusyArea.BACKUP_DELETE) {
            if (!backupStore.delete(entry)) {
                withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_delete_backup_failed)) }
            } else {
                val remaining = backupStore.list(config.packageName)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(backups = remaining, showBackupDialog = remaining.isNotEmpty()) }
                }
            }
            true
        }
    }

    private fun restoreViaShizuku(entry: ConfigBackupStore.Entry) {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        if (!Shizuku.pingBinder()) {
            showSnackbar(context.getString(R.string.gf_shizuku_not_running))
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            showSnackbar(context.getString(R.string.gf_shizuku_permission_required))
            return
        }
        launchIoWithLoading(BusyArea.RESTORE) {
            val bytes = backupStore.readBytes(entry)
                ?: return@launchIoWithLoading run {
                    withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_backup_unreadable)) }
                    true
                }
            // The restore payload is written by this app under a fresh name, then copied over the
            // game's file by the privileged shell — the same ownership split as a profile push,
            // so no privileged-uid leftover can sit on a path this app later writes.
            // The entry's own file name picks the target: a profile whose file is not the
            // config's `saveFile` (`UserCustom.ini`) must get its bytes back into its own file
            // and directory, never into `Active.sav`.
            val profile = profileForBackup(config, entry)
            val targetName = profile?.targetFile ?: config.saveFile
            val tempFile = File.createTempFile("restore_", "_" + targetName, context.externalCacheDir ?: context.cacheDir)
            try {
                tempFile.writeBytes(bytes)
                val destDir = Environment.getExternalStorageDirectory().path +
                    (profile?.targetDir ?: config.saveDir)
                val destPath = destDir + targetName
                // The marker under the list promises the restore is reversible, so take the
                // bytes being replaced first — a restore of the wrong generation is otherwise
                // the end of the newer file.
                val current = readCurrentFileForBackup(destPath)
                if (current != null && backupStore.save(config.packageName, targetName, current) == null) {
                    withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_backup_failed_refused)) }
                    return@launchIoWithLoading false
                }
                val mkdir = shellRunner.execSafeResult("mkdir", "-p", destDir)
                if (!mkdir.isSuccess) {
                    withContext(Dispatchers.Main) {
                        showSnackbar(context.getString(R.string.gf_mkdir_failed, mkdir.exitCode, destDir))
                    }
                    return@launchIoWithLoading false
                }
                val result = shellRunner.execSafeResult("cp", "-f", tempFile.absolutePath, destPath)
                // A copy that had to re-create the file is owned by the privileged shell; take
                // the owner from the destination directory (the game's UID) so the game can
                // still read it, exactly like the profile push.
                if (result.isSuccess && current == null) {
                    val owner = shellRunner.execSafe("stat", "-c", "%u:%g", destDir).trim()
                    if (owner.matches(Regex("^\\d+:\\d+$"))) {
                        shellRunner.execSafe("chown", owner, destPath)
                    }
                }
                val readBack = if (result.isSuccess) readCurrentFileForBackup(destPath) else null
                val message = when {
                    !result.isSuccess -> context.getString(
                        R.string.gf_cp_failed,
                        result.exitCode,
                        result.stderr.ifBlank { context.getString(R.string.gf_no_output) }
                    )
                    readBack == null || !readBack.contentEquals(bytes) ->
                        context.getString(R.string.gf_written_no_readback)
                    else -> context.getString(R.string.gf_restored_backup, entry.formattedTimestamp())
                }
                withContext(Dispatchers.Main) { showSnackbar(message) }
            } finally {
                tempFile.delete()
            }
            true
        }
    }

    /**
     * The file a restore is about to replace, for the pre-restore backup — through the direct
     * binder channel when it answers, else the root shell. Null means the file is missing or
     * unreadable, never "no channel"; a missing file needs no backup.
     */
    private suspend fun readCurrentFileForBackup(path: String): ByteArray? {
        val direct = shellRunner.readFileDirect(path, forceBind = true)
        if (direct != null && direct.isNotEmpty()) return direct
        return readRootFile(path)?.takeIf { it.isNotEmpty() }
    }

    private fun restoreViaSaf(entry: ConfigBackupStore.Entry) {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        val treeUri = getCustomGameTreeUri(config.packageName)
        if (treeUri != null) {
            performSafRestore(treeUri, config, entry)
        } else {
            // Same shape as the reset channel: remember the game, park the restore in
            // pendingAction, and the folder grant runs it once it exists.
            customPackagePending = config.packageName
            pendingAction = {
                getCustomGameTreeUri(config.packageName)?.let { performSafRestore(it, config, entry) }
            }
            _events.tryEmit(EditEvent.LaunchFolderPicker(PubgSafAccess.initialFolderUri(config.packageName, Build.VERSION.SDK_INT)))
        }
    }

    private fun performSafRestore(treeUri: Uri, config: GameConfig, entry: ConfigBackupStore.Entry) {
        launchIoWithLoading(BusyArea.RESTORE) {
            val bytes = backupStore.readBytes(entry)
            if (bytes == null) {
                withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_backup_unreadable)) }
                return@launchIoWithLoading true
            }
            val profile = profileForBackup(config, entry)
            val targetName = profile?.targetFile ?: config.saveFile
            val dir = getOrCreateTargetDir(treeUri, config, profile?.targetDir)
            if (dir == null) {
                withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_provider_refused_folder)) }
                return@launchIoWithLoading true
            }
            // The marker under the backup list promises the restore is reversible, so the bytes
            // being replaced are stashed first. `findFile` distinguishes "not created yet" from
            // "present": a present file that cannot be read has no backup, and overwriting it
            // would destroy the only copy without a way back.
            val existing = dir.findFile(targetName)
            val current = if (existing == null) {
                null
            } else {
                runCatching {
                    context.contentResolver.openInputStream(existing.uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (existing != null && current == null) {
                withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_existing_unreadable)) }
                return@launchIoWithLoading false
            }
            if (current != null && current.isNotEmpty() && backupStore.save(config.packageName, targetName, current) == null) {
                withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_backup_failed_refused)) }
                return@launchIoWithLoading false
            }
            val target = existing ?: dir.createFile(MIME_BINARY, targetName)
            if (target == null) {
                withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_provider_refused_create)) }
                return@launchIoWithLoading true
            }
            context.contentResolver.openOutputStream(target.uri, "w")?.use { it.write(bytes) }
                ?: return@launchIoWithLoading run {
                    withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_provider_refused_write)) }
                    true
                }
            // The write call is not the verdict: read the provider's own bytes back and compare
            // them with the backup being restored. A provider that accepts the stream but writes
            // nothing or partially used to still produce "Restored ...".
            val readBack = runCatching {
                context.contentResolver.openInputStream(target.uri)?.use { it.readBytes() }
            }.getOrNull()
            val message = if (readBack == null || !readBack.contentEquals(bytes)) {
                context.getString(R.string.gf_written_no_readback)
            } else {
                context.getString(R.string.gf_restored_backup, entry.formattedTimestamp())
            }
            withContext(Dispatchers.Main) { showSnackbar(message) }
            true
        }
    }

    // ------------------------------------------------------ root-only INI (New State)

    /**
     * Applies the New State INI transform (backlog item M168; field verification pending) — the
     * reference module's choreography
     * minus its concealment commands: the game is force-stopped first (it rewrites its own
     * settings on exit), the current bytes are read, transformed and backed up, the transform
     * is written back, the file keeps the reference's `chmod 0440` lock, and the result is
     * read back so the report is what the device holds — never what was sent.
     *
     * Root-only by construction: the path is another app's private data directory, so there is
     * no fallback channel to offer.
     */
    private fun applyRootOnlyIni(config: GameConfig) {
        val path = config.rootOnlyIniPath ?: return
        if (!shellRunner.isRootAvailable(force = true)) {
            showSnackbar(context.getString(R.string.gf_newstate_need_root))
            return
        }
        launchIoWithLoading(BusyArea.PROFILE_PUSH) {
            shellRunner.execSafeResult("am", "force-stop", config.packageName)
            // Existence first, so "the game never wrote it" and "the channel died mid-read"
            // stay distinct messages.
            if (!shellRunner.execSafeResult("test", "-f", path).isSuccess) {
                withContext(Dispatchers.Main) {
                    showSnackbar(context.getString(R.string.gf_save_not_created, config.saveFile, config.saveDir))
                }
                return@launchIoWithLoading true
            }
            val original = readRootFile(path)
            if (original == null) {
                withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_newstate_read_failed)) }
                return@launchIoWithLoading true
            }
            val transform = NewStateIniTransform.apply(original)
            if (!transform.matched) {
                withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_newstate_unrecognized)) }
                return@launchIoWithLoading true
            }
            if (!transform.changed) {
                withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_newstate_already)) }
                return@launchIoWithLoading true
            }
            // No undo point means no write: the transform is about to replace the only copy of
            // the game's INI, and ConfigBackupStore.save reports failure instead of pretending
            // a backup exists. The old code discarded that result and overwrote anyway.
            if (backupStore.save(config.packageName, config.saveFile, original) == null) {
                withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_backup_failed_refused)) }
                return@launchIoWithLoading false
            }
            val pushFile = File.createTempFile("push_", "_" + config.saveFile, context.externalCacheDir ?: context.cacheDir)
            try {
                pushFile.writeBytes(transform.bytes)
                // `cp` over the existing file keeps its inode, owner and mode — which is why
                // the reference's pre-write `chmod 0777` has nothing to do here, and why the
                // write is a copy rather than a delete-and-recreate.
                val copy = shellRunner.execSafeResult("cp", "-f", pushFile.absolutePath, path)
                if (!copy.isSuccess) {
                    withContext(Dispatchers.Main) {
                        showSnackbar(context.getString(R.string.gf_cp_failed, copy.exitCode, copy.stderr.ifBlank { context.getString(R.string.gf_no_output) }))
                    }
                    return@launchIoWithLoading true
                }
                val chmod = shellRunner.execSafeResult("chmod", "0440", path)
                if (!chmod.isSuccess) {
                    withContext(Dispatchers.Main) {
                        showSnackbar(context.getString(
                            R.string.gf_permissions_failed,
                            "chmod ${chmod.exitCode}: ${chmod.stderr.ifBlank { context.getString(R.string.gf_no_output) }}"
                        ))
                    }
                    return@launchIoWithLoading false
                }
                val readBack = readRootFile(path)
                withContext(Dispatchers.Main) {
                    showSnackbar(
                        if (readBack != null && readBack.contentEquals(transform.bytes)) {
                            context.getString(R.string.gf_newstate_applied)
                        } else {
                            context.getString(R.string.gf_written_no_readback)
                        }
                    )
                }
                true
            } finally {
                pushFile.delete()
            }
        }
    }

    /** Restores one of a root-only config's backups to the same path [applyRootOnlyIni] writes. */
    private fun restoreRootOnlyIni(config: GameConfig, entry: ConfigBackupStore.Entry) {
        val path = config.rootOnlyIniPath ?: return
        if (!shellRunner.isRootAvailable(force = true)) {
            showSnackbar(context.getString(R.string.gf_newstate_need_root))
            return
        }
        launchIoWithLoading(BusyArea.RESTORE) {
            val bytes = backupStore.readBytes(entry)
            if (bytes == null) {
                withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_backup_unreadable)) }
                return@launchIoWithLoading true
            }
            val tempFile = File.createTempFile("restore_", "_" + config.saveFile, context.externalCacheDir ?: context.cacheDir)
            try {
                tempFile.writeBytes(bytes)
                shellRunner.execSafeResult("am", "force-stop", config.packageName)
                // The backup list promises a reversible restore, so stash the live file's bytes
                // first. Existence first, so "the game never wrote it" stays distinct from "it
                // exists but cannot be read" — that one has no backup and must not be replaced.
                val exists = shellRunner.execSafeResult("test", "-f", path).isSuccess
                if (exists) {
                    val current = readRootFile(path)
                    if (current == null) {
                        withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_existing_unreadable)) }
                        return@launchIoWithLoading false
                    }
                    if (backupStore.save(config.packageName, config.saveFile, current) == null) {
                        withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_backup_failed_refused)) }
                        return@launchIoWithLoading false
                    }
                }
                val copy = shellRunner.execSafeResult("cp", "-f", tempFile.absolutePath, path)
                if (!copy.isSuccess) {
                    withContext(Dispatchers.Main) {
                        showSnackbar(context.getString(R.string.gf_cp_failed, copy.exitCode, copy.stderr.ifBlank { context.getString(R.string.gf_no_output) }))
                    }
                    return@launchIoWithLoading true
                }
                // `cp` over an existing file keeps its owner; a file that had to be re-created
                // would otherwise stay root-owned and unreadable by the game, so the owner is
                // taken from the app's own directory (whose owner is the app's UID). The result
                // is checked: announcing "restored" over a file the game cannot read is exactly
                // the failure this path exists to avoid.
                if (!exists) {
                    val uid = shellRunner.execSafe("stat", "-c", "%u", NewStateIniTransform.CONFIG_DIR).trim()
                    if (uid.toLongOrNull() != null) {
                        val chown = shellRunner.execSafeResult("chown", "$uid:$uid", path)
                        if (!chown.isSuccess) {
                            withContext(Dispatchers.Main) {
                                showSnackbar(context.getString(
                                    R.string.gf_permissions_failed,
                                    "chown ${chown.exitCode}: ${chown.stderr.ifBlank { context.getString(R.string.gf_no_output) }}"
                                ))
                            }
                            return@launchIoWithLoading false
                        }
                    }
                }
                val chmod = shellRunner.execSafeResult("chmod", "0440", path)
                if (!chmod.isSuccess) {
                    withContext(Dispatchers.Main) {
                        showSnackbar(context.getString(
                            R.string.gf_permissions_failed,
                            "chmod ${chmod.exitCode}: ${chmod.stderr.ifBlank { context.getString(R.string.gf_no_output) }}"
                        ))
                    }
                    return@launchIoWithLoading false
                }
                // The copy is not the verdict either: compare the bytes the device now holds
                // with the backup being restored.
                val readBack = readRootFile(path)
                val message = if (readBack == null || !readBack.contentEquals(bytes)) {
                    context.getString(R.string.gf_written_no_readback)
                } else {
                    context.getString(R.string.gf_restored_backup, entry.formattedTimestamp())
                }
                withContext(Dispatchers.Main) { showSnackbar(message) }
                true
            } finally {
                tempFile.delete()
            }
        }
    }

    /**
     * Reads a root-only file by having the shell copy it into this app's cache — the same
     * copy-then-read shape the save editor uses. Null when the copy produced no file (the
     * file is gone, or the channel died between the check and the read).
     */
    private suspend fun readRootFile(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("rootread_", ".tmp", context.externalCacheDir ?: context.cacheDir)
        tempFile.delete()
        try {
            val copy = shellRunner.execSafeResult("cp", "-f", path, tempFile.absolutePath)
            if (copy.isSuccess && tempFile.exists()) tempFile.readBytes() else null
        } finally {
            tempFile.delete()
        }
    }

    fun onMethodSelected(which: Int) {
        _uiState.update { it.copy(showMethodChooser = false) }
        when (which) {
            0 -> performRootAction { checkAndStartShizukuAction() }
            1 -> checkAndStartShizukuAction()
            2 -> {
                _events.tryEmit(EditEvent.LaunchSafPicker)
            }
            3 -> {
                pendingAction = { handleZArchiverAction() }
                handleZArchiverAction()
            }
        }
    }

    fun onSelectFile() {
        _events.tryEmit(EditEvent.LaunchFilePicker)
    }

    fun onCustomFilePicked(uri: Uri) {
        setCustomSelection(uri)
    }

    /**
     * Switches the file type before a pick. The old selection is cleared because a file chosen
     * for `Active.sav` must never be written to `UserCustom.ini` (or vice versa) by accident.
     */
    fun onUploadTargetSelected(index: Int) {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        val target = config.uploadTargets.getOrNull(index) ?: return
        setCustomSelection(null)
        _uiState.update {
            it.copy(
                selectedUploadTarget = index,
                uploadDestination = target.destination(
                    Environment.getExternalStorageDirectory().path
                )
            )
        }
    }

    fun onUploadFile() {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        val target = config.uploadTargets
            .getOrNull(_uiState.value.selectedUploadTarget)
            ?: return showSnackbar(context.getString(R.string.gf_upload_type_unsupported))
        uploadCustomContent(
            selectedFileUri,
            context.getString(R.string.gf_select_file_first),
            context.getString(R.string.custom_upload_success_file),
            target = target,
            shAction = { uri, cfg, t -> uploadCustomFileWithShizuku(uri, cfg, t) },
            safAction = { uri, cfg, t -> uploadCustomFileWithSaf(uri, cfg, t) }
        )
    }

    fun onClearSelection() {
        setCustomSelection(null)
    }

    fun onSafPicked(treeUri: Uri) {
        performSafFileCopy(treeUri, _uiState.value.selectedGame)
    }

    /**
     * The bytes a delivery channel should push for [assetPath], after the game's own transform.
     *
     * Only the model-naming games have one: Genshin's template must carry the device's real
     * `Build.MODEL` or the game's lookup misses it and the pushed file changes nothing. Binary
     * blobs (PUBG) pass through untouched.
     */
    private fun assetBytes(config: GameConfig, assetPath: String): ByteArray {
        val raw = context.assets.open(assetPath).use { it.readBytes() }
        if (!config.requiresDeviceModel) return raw
        return GenshinConfigTemplate.withThisDevice(String(raw, Charsets.UTF_8))
            .toByteArray(Charsets.UTF_8)
    }

    private fun performSafFileCopy(treeUri: Uri, game: GameType) {
        val config = gameConfigs[game] ?: return
        val assetPath = requireSelectedAssetPath() ?: return
        launchIoWithLoading(BusyArea.PROFILE_PUSH, successMessage = context.getString(R.string.gf_success_saf)) {
            val pickedDir = DocumentFile.fromTreeUri(context, treeUri) ?: throw IOException("Cannot write")
            // A profile may target its own file name (`UserCustom.ini`) — the picked folder
            // must hold that file, and the name keys the backup the same way.
            val profile = selectedProfileOf(config)
            val targetName = profile?.targetFile ?: config.saveFile
            val existingFile = pickedDir.findFile(targetName)
            // `length() > 0` used to collapse "present but unreadable" into "absent", which
            // made the plan seed the bundled asset over a file it had never read and never
            // backed up. Only a genuinely absent file is a seed now.
            val existingBytes = if (existingFile == null) {
                null
            } else {
                runCatching {
                    context.contentResolver.openInputStream(existingFile.uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (existingFile != null && existingBytes == null) {
                showSnackbar(context.getString(R.string.gf_existing_unreadable))
                return@launchIoWithLoading false
            }
            // What the profile means on this device, decided once for every channel
            // (ProfilePushPlan): seed a missing save, patch specific fields of an existing one,
            // merge a preserve-tail INI, or refuse when the file's layout cannot be verified.
            // Rewriting the device's own bytes was the old behaviour here and reported success
            // while changing nothing.
            val plan = ProfilePushPlan.resolve(
                existing = existingBytes,
                asset = assetBytes(config, assetPath),
                profile = profile,
                requiresDeviceModel = config.requiresDeviceModel
            )
            val inputBytes = when (plan) {
                is ProfilePushPlan.Result.Bytes -> plan.bytes
                is ProfilePushPlan.Result.Refused -> {
                    showSnackbar(context.getString(
                        R.string.gf_profile_patch_refused,
                        plan.fieldReasons.entries.joinToString(" · ") { "${it.key} — ${it.value}" }
                    ))
                    return@launchIoWithLoading false
                }
                ProfilePushPlan.Result.NothingToApply -> {
                    showSnackbar(context.getString(R.string.gf_profile_nothing_to_apply))
                    return@launchIoWithLoading false
                }
            }
            // Safety backup before the overwrite — the bytes the device actually held, never
            // the payload about to replace them. A file the game never wrote backs up nothing;
            // a backup that cannot be written aborts the push, because there would be no way
            // back from the write (ConfigBackupStore.save reports failure for exactly this).
            if (existingBytes != null && existingBytes.isNotEmpty()) {
                if (backupStore.save(config.packageName, targetName, existingBytes) == null) {
                    showSnackbar(context.getString(R.string.gf_backup_failed_refused))
                    return@launchIoWithLoading false
                }
            }
            val file = existingFile ?: pickedDir.createFile(MIME_BINARY, targetName) ?: throw IOException("Cannot create file")
            context.contentResolver.openOutputStream(file.uri, "w")?.use { it.write(inputBytes) }
                ?: throw IOException("Cannot open $targetName for writing")
            // The write call is not the verdict: read the provider's own bytes back and compare
            // them with what was meant to be written. A null stream used to fall through to
            // `true` and report a success that never happened.
            val readBack = runCatching {
                context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
            }.getOrNull()
            if (readBack == null || !readBack.contentEquals(inputBytes)) {
                throw IOException("read-back at $targetName does not match the written bytes")
            }
            true
        }
    }

    fun onStoragePermissionResult() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            pendingAction?.invoke()
            pendingAction = null
        }
    }

    fun onCustomFolderPicked(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            customPackagePending?.let { pkg ->
                customUploadPrefs.edit { putString(getCustomPrefsKey(pkg), uri.toString()) }
            }
            pendingAction?.invoke()
            pendingAction = null
        } catch (_: Exception) {}
    }

    fun onLaunchGame() {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        val intent = context.packageManager.getLaunchIntentForPackage(config.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            showSnackbar(context.getString(R.string.gf_game_not_installed_toast))
        }
    }

    private fun setSelectedAssetPathFromProfile(): Boolean {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return false
        val profiles = config.profiles
        // A profile index the game does not have (the leftover PUBG selection after switching to a
        // one-profile game) is clamped, not treated as missing — the user still gets a file to push.
        selectedAssetPath = profiles.getOrNull(
            _uiState.value.selectedProfile.coerceAtMost(profiles.lastIndex)
        )?.assetPath
        if (selectedAssetPath == null) {
            showSnackbar(context.getString(R.string.apply_profile_first))
            return false
        }
        return true
    }

    /** The selected profile of [config] — the same clamping rule [setSelectedAssetPathFromProfile] uses. */
    private fun selectedProfileOf(config: GameConfig): GameProfile? =
        config.profiles.getOrNull(_uiState.value.selectedProfile.coerceAtMost(config.profiles.lastIndex))

    /**
     * The profile a backup entry belongs to, by the original file name [ConfigBackupStore]
     * keeps in its `<timestamp>_<name>` layout — how a restore finds the right file and
     * directory for a profile that does not target the config's own `saveFile`.
     */
    private fun profileForBackup(config: GameConfig, entry: ConfigBackupStore.Entry): GameProfile? {
        val original = entry.file.name.substringAfter('_')
        return config.profiles.firstOrNull { it.targetFile == original }
    }

    private fun setCustomSelection(fileUri: Uri?) {
        selectedFileUri = fileUri
        _uiState.update {
            it.copy(
                // The raw name, not a formatted sentence: the screen resolves the localized
                // "Selected: …" at composition so a language switch cannot leave stale copy.
                selectedFileName = fileUri?.let { uri ->
                    getDisplayName(uri) ?: uri.toString()
                }
            )
        }
    }

    private fun uploadCustomContent(
        uri: Uri?,
        msg: String,
        success: String,
        target: GameUploadTarget,
        shAction: suspend (ByteArray, GameConfig, GameUploadTarget) -> Boolean,
        safAction: (ByteArray, GameConfig, GameUploadTarget) -> Boolean
    ) {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        if (uri == null) {
            showSnackbar(context.getString(R.string.custom_upload_failed, msg))
            return
        }
        launchIoWithLoading(BusyArea.CUSTOM_UPLOAD, successMessage = success) {
            val imported = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (imported == null || imported.isEmpty()) {
                withContext(Dispatchers.Main) {
                    showSnackbar(context.getString(R.string.gf_upload_empty))
                }
                return@launchIoWithLoading false
            }
            val invalid = PubgUploadPlan.validate(imported, target.format)
            if (invalid != null) {
                withContext(Dispatchers.Main) {
                    showSnackbar(
                        context.getString(
                            when (invalid) {
                                PubgUploadPlan.Invalid.EMPTY -> R.string.gf_upload_empty
                                PubgUploadPlan.Invalid.NOT_GVAS -> R.string.gf_upload_not_gvas
                                PubgUploadPlan.Invalid.NOT_USER_CUSTOM_INI -> R.string.gf_upload_not_usercustom
                            }
                        )
                    )
                }
                return@launchIoWithLoading false
            }
            if (canUseShizukuForCustom()) shAction(imported, config, target)
            else safAction(imported, config, target)
        }
    }

    private fun canUseShizukuForCustom(): Boolean {
        if (!Shizuku.pingBinder()) return false
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0)
            return false
        }
        return true
    }

    private suspend fun uploadCustomFileWithShizuku(
        imported: ByteArray,
        config: GameConfig,
        target: GameUploadTarget
    ): Boolean {
        val root = Environment.getExternalStorageDirectory().path
        val destDir = root + target.directory.trimEnd('/')
        val targetPath = "$destDir/${target.fileName}"
        val tempFile = File(
            context.externalCacheDir ?: context.cacheDir,
            "Custom_${target.fileName}"
        )
        return try {
            // A null answer is "no channel"; an empty answer is "the game has not written this
            // file yet". Both used to collapse into one refusal, which left a user unable to
            // seed a file even though the directory existed.
            val existingBytes = shellRunner.readFileDirect(targetPath, forceBind = true)
            if (existingBytes == null) {
                withContext(Dispatchers.Main) {
                    showSnackbar(
                        if (Shizuku.pingBinder()) context.getString(R.string.gf_shizuku_channel_unavailable_short)
                        else context.getString(R.string.gf_no_privileged_channel)
                    )
                }
                return false
            }
            val existing = existingBytes.takeIf { it.isNotEmpty() }
            val payload = PubgUploadPlan.payload(imported, existing, target.format)
            existing?.let { bytes ->
                backupStore.save(config.packageName, target.fileName, bytes)
            }
            shellRunner.execSafe("mkdir", "-p", destDir)
            tempFile.writeBytes(payload)
            val copy = shellRunner.execSafeResult("cp", "-f", tempFile.absolutePath, targetPath)
            if (!copy.isSuccess) {
                withContext(Dispatchers.Main) {
                    showSnackbar(
                        context.getString(
                            R.string.gf_cp_failed,
                            copy.exitCode,
                            copy.stderr.ifBlank { context.getString(R.string.gf_no_output) }
                        )
                    )
                }
                return false
            }
            // The copy's exit code is not the report: read the destination back and compare
            // the actual bytes before claiming success.
            val readBack = shellRunner.readFileDirect(targetPath, forceBind = true)
            if (readBack == null || !readBack.contentEquals(payload)) {
                withContext(Dispatchers.Main) {
                    showSnackbar(context.getString(R.string.gf_upload_verify_failed, targetPath))
                }
                return false
            }
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                showSnackbar(
                    context.getString(
                        R.string.custom_upload_failed,
                        e.message ?: e.javaClass.simpleName
                    )
                )
            }
            false
        } finally {
            tempFile.delete()
        }
    }

    private fun uploadCustomFileWithSaf(
        imported: ByteArray,
        config: GameConfig,
        target: GameUploadTarget
    ): Boolean {
        val treeUri = getCustomGameTreeUri(config.packageName)
            ?: run {
                requestCustomGameFolderAccess(config.packageName)
                return false
            }
        return try {
            val targetDir = getOrCreateTargetDir(treeUri, config, target.directory) ?: return false
            val existing = targetDir.findFile(target.fileName)?.takeIf { it.length() > 0L }?.let { file ->
                runCatching {
                    context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            val payload = PubgUploadPlan.payload(imported, existing, target.format)
            // Safety backup before the delete-and-recreate below, which would otherwise leave
            // the old generation unreachable through the provider.
            existing?.let { bytes ->
                backupStore.save(config.packageName, target.fileName, bytes)
            }
            targetDir.findFile(target.fileName)?.delete()
            val file = targetDir.createFile(MIME_BINARY, target.fileName) ?: return false
            context.contentResolver.openOutputStream(file.uri, "w")?.use { output ->
                output.write(payload)
            } ?: return false
            // Verify by reading the provider's own bytes back, not by the write call returning.
            val readBack = runCatching {
                context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
            }.getOrNull()
            if (readBack == null || !readBack.contentEquals(payload)) {
                showSnackbar(
                    context.getString(R.string.gf_upload_verify_failed, target.fileName)
                )
                false
            } else {
                true
            }
        } catch (e: Exception) {
            showSnackbar(
                context.getString(
                    R.string.custom_upload_failed,
                    e.message ?: e.javaClass.simpleName
                )
            )
            false
        }
    }

    private fun requestCustomGameFolderAccess(packageName: String) {
        pendingAction = null
        customPackagePending = packageName
        _events.tryEmit(EditEvent.LaunchFolderPicker(PubgSafAccess.initialFolderUri(packageName, Build.VERSION.SDK_INT)))
        showSnackbar(context.getString(R.string.custom_upload_need_folder_access, packageName))
    }

    private fun getCustomGameTreeUri(packageName: String): Uri? =
        customUploadPrefs.getString(getCustomPrefsKey(packageName), null)?.toUri()

    private fun getCustomPrefsKey(packageName: String): String = "saf_tree_uri_$packageName"

    private fun getOrCreateTargetDir(
        treeUri: Uri,
        config: GameConfig,
        saveDirOverride: String? = null
    ): DocumentFile? {
        val base = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        // The tree the user picked is the game's Android/data root, and the config's saveDir says
        // where the game actually reads from — PUBG nests seven levels, Genshin sits directly in
        // files/. Derived rather than hardcoded so a game added without its own branch works.
        // [saveDirOverride] is a profile's own target directory (UserCustom.ini sits in
        // Saved/Config/Android, not SaveGames) — same descent, different tail.
        val pathSegments = (saveDirOverride ?: config.saveDir).trim('/').split('/').filter { it.isNotBlank() }
        var current = base
        for (segment in pathSegments) {
            val next = current.findFile(segment) ?: current.createDirectory(segment)
            if (next == null) return null
            current = next
        }
        return current
    }

    private fun getDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1 && it.moveToFirst()) it.getString(index) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun checkAndStartShizukuAction() {
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        if (selectedAssetPath == null) {
            showSnackbar(context.getString(R.string.apply_profile_first))
            return
        }
        if (!Shizuku.pingBinder()) {
            showSnackbar(context.getString(R.string.gf_shizuku_not_running))
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0)
        } else {
            performShizukuCopy(config)
        }
    }

    private fun performShizukuCopy(config: GameConfig) {
        val assetPath = requireSelectedAssetPath() ?: return
        launchIoWithLoading(BusyArea.PROFILE_PUSH, successMessage = context.getString(R.string.gf_success_shizuku)) {
            var pushFile: File? = null
            try {
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                // A profile may carry its own target — `UserCustom.ini` lands in
                // `Saved/Config/Android/`, not `SaveGames/` — and its file name also keys its
                // backups, so a restore returns the right bytes to the right file.
                val profile = selectedProfileOf(config)
                val targetName = profile?.targetFile ?: config.saveFile
                val destDir = Environment.getExternalStorageDirectory().path +
                    (profile?.targetDir ?: config.saveDir)
                val destPath = destDir + targetName

                // The probe keeps "no channel" distinct from "missing": null aborts (nothing can
                // be reported honestly without a channel), empty means the game has not written
                // its save yet — and that is a seed, not a refusal. The reference tools push the
                // same way (BattleGrounds_GFX stages in /data/local/tmp and moves, never checking
                // first): applying a profile re-adds the file with the bundled profile's bytes
                // and the game takes over from its next launch. The save editor keeps its own
                // refusal (pullSaveBytes → Missing) because patching needs the file's real bytes.
                val existingBytes = shellRunner.readFileDirect(destPath, forceBind = true)
                if (existingBytes == null) {
                    withContext(Dispatchers.Main) {
                        showSnackbar(
                            if (Shizuku.pingBinder()) context.getString(R.string.gf_shizuku_channel_unavailable_short)
                            else context.getString(R.string.gf_no_privileged_channel)
                        )
                    }
                    return@launchIoWithLoading false
                }
                val existing = existingBytes.takeIf { it.isNotEmpty() }

                // Safety backup before the overwrite — the bytes the device actually held, not
                // the payload about to replace them (see ConfigBackupStore). A file the game
                // never wrote backs up nothing. A backup that cannot be written aborts the
                // push: without it the user has no way back from the write.
                if (existing != null) {
                    if (backupStore.save(config.packageName, targetName, existing) == null) {
                        showSnackbar(context.getString(R.string.gf_backup_failed_refused))
                        return@launchIoWithLoading false
                    }
                }

                // What the profile means on this device — the same decision every channel makes
                // (ProfilePushPlan). Patching the fields the profile names replaced the old
                // "prefer the file already there" branch, which rewrote the device's bytes with
                // themselves and reported success without changing anything.
                val inputBytes: ByteArray = when (val plan = ProfilePushPlan.resolve(
                    existing = existing,
                    asset = assetBytes(config, assetPath),
                    profile = profile,
                    requiresDeviceModel = config.requiresDeviceModel
                )) {
                    is ProfilePushPlan.Result.Bytes -> plan.bytes
                    is ProfilePushPlan.Result.Refused -> {
                        showSnackbar(context.getString(
                            R.string.gf_profile_patch_refused,
                            plan.fieldReasons.entries.joinToString(" · ") { "${it.key} — ${it.value}" }
                        ))
                        return@launchIoWithLoading false
                    }
                    ProfilePushPlan.Result.NothingToApply -> {
                        showSnackbar(context.getString(R.string.gf_profile_nothing_to_apply))
                        return@launchIoWithLoading false
                    }
                }

                // The push file is created and written by this app alone, under a fresh name on
                // every run, so no privileged-uid leftover can ever be sitting on it.
                pushFile = File.createTempFile("push_", "_" + config.saveFile, cacheDir)
                pushFile.writeBytes(inputBytes)

                // Every step reports its own verdict. The old code discarded both results and
                // announced success unconditionally, so a failed mkdir or a refused copy still
                // produced "Success via Shizuku!".
                val mkdir = shellRunner.execSafeResult("mkdir", "-p", destDir)
                if (!mkdir.isSuccess) {
                    showSnackbar(context.getString(
                        R.string.gf_cp_failed,
                        mkdir.exitCode,
                        mkdir.stderr.ifBlank { context.getString(R.string.gf_no_output) }
                    ))
                    return@launchIoWithLoading false
                }
                val copy = shellRunner.execSafeResult("cp", "-f", pushFile.absolutePath, destPath)
                if (!copy.isSuccess) {
                    showSnackbar(context.getString(
                        R.string.gf_cp_failed,
                        copy.exitCode,
                        copy.stderr.ifBlank { context.getString(R.string.gf_no_output) }
                    ))
                    return@launchIoWithLoading false
                }
                // A seed has no game-written file to inherit ownership from: `cp` creates it as
                // the shell's UID, and the game must still be able to read and rewrite it. Take
                // the owner from the destination directory, which belongs to the game (the same
                // move the New State restore makes). Best-effort: over an existing file `cp`
                // keeps the original owner, which is already correct.
                if (existing == null) {
                    val owner = shellRunner.execSafe("stat", "-c", "%u:%g", destDir).trim()
                    if (owner.matches(Regex("^\\d+:\\d+$"))) {
                        shellRunner.execSafe("chown", owner, destPath)
                    }
                }

                // The copy is not the verdict either: read the device's file back and compare it
                // with the bytes that were meant to land.
                val readBack = shellRunner.readFileDirect(destPath, forceBind = true)
                if (readBack == null || !readBack.contentEquals(inputBytes)) {
                    throw IOException("read-back at $destPath does not match the written bytes")
                }

                true
            } catch (e: Exception) {
                throw e
            } finally {
                pushFile?.delete()
            }
        }
    }

    private fun handleZArchiverAction() {
        if (!checkStoragePermission()) return
        val config = gameConfigs[_uiState.value.selectedGame] ?: return
        val assetPath = requireSelectedAssetPath() ?: return
        // Defense in depth for the chooser's hidden row: this profile's payload depends on the
        // file already on the device, which a manual copy cannot read.
        if (ProfilePushPlan.needsExistingFile(selectedProfileOf(config))) {
            showSnackbar(context.getString(R.string.gf_zarchiver_unavailable))
            return
        }
        launchIoWithLoading(BusyArea.PROFILE_PUSH) {
            pasteFileToDownloads(config, assetPath)
            withContext(Dispatchers.Main) { _events.tryEmit(EditEvent.ShowZArchiverDialog) }
            true
        }
    }

    @Throws(IOException::class)
    private fun pasteFileToDownloads(config: GameConfig, assetPath: String) {
        // Exported through the same transform as a direct push, so the manual file the user moves
        // with ZArchiver carries this device's model rather than the placeholder.
        val inputBytes = assetBytes(config, assetPath)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, config.saveFile)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            ) ?: throw IOException("MediaStore failed")
            context.contentResolver.openOutputStream(uri)?.use { it.write(inputBytes) }
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), config.saveFile).writeBytes(inputBytes)
        }
    }

    fun launchZArchiver(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(ZARCHIVER_PACKAGE)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } else {
            openPlayStore(ZARCHIVER_PACKAGE)
        }
    }

    /**
     * Opens the game's Play Store listing — the GAME NOT FOUND card's one-tap install path,
     * same fallback chain [launchZArchiver] has always used: market app first, browser URL
     * when no market app answers. Never throws; false only means every launcher refused.
     */
    fun openPlayStore(packageName: String): Boolean {
        return try {
            val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
            true
        } catch (_: Exception) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$packageName&hl=en".toUri()
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                _events.tryEmit(EditEvent.LaunchAllFilesAccess)
                return false
            }
            return true
        }
        val granted = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            _events.tryEmit(EditEvent.LaunchAllFilesAccess)
        }
        return granted
    }

    fun launchAllFilesAccess(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:${context.packageName}".toUri()
                }
            } catch (_: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        }
        return null
    }

    fun showSnackbar(message: String) {
        _events.tryEmit(EditEvent.Toast(message, isLong = true))
    }

    private fun requireSelectedAssetPath(): String? {
        val path = selectedAssetPath
        if (path == null) showSnackbar(context.getString(R.string.apply_profile_first))
        return path
    }

    private fun launchIoWithLoading(
        area: BusyArea,
        successMessage: String? = null,
        task: suspend () -> Boolean
    ) {
        _uiState.update { it.copy(isLoading = true, busyArea = area) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                task()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showSnackbar(context.getString(R.string.gf_failed, e.message)) }
                false
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoading = false, busyArea = null) }
                if (ok && successMessage != null) showSnackbar(successMessage)
            }
        }
    }

    private companion object {
        private const val MIME_BINARY = "application/octet-stream"
        private const val ZARCHIVER_PACKAGE = "ru.zdevs.zarchiver"

        /**
         * The packages whose saves the inline editor touches — exactly the variants [initializeGameConfigs]
         * builds a PUBG config for, kept in one place so the editor's gate can never widen past it.
         */
        private val PUBG_PACKAGES = setOf(
            "com.tencent.ig", "com.pubg.krmobile", "com.vng.pubgmobile", "com.pubg.imobile", "com.rekoo.pubgm",
            "com.tencent.igfit"
        )

        /**
         * The embedded editors (HSR/WuWa/GRID) are not in [gameConfigs] — their install probes
         * used to run only because the old `packages` list happened to name them. Listed here
         * so the probe loop covers them explicitly: each branch resolves its own packages.
         */
        private val EMBEDDED_EDITOR_GAMES = listOf(GameType.HSR, GameType.WUWA, GameType.GRID)
    }
}
