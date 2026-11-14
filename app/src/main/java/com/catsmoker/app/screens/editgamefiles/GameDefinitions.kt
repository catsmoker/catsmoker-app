package com.catsmoker.app.screens.editgamefiles

import com.catsmoker.app.screens.editgamefiles.games.genshin.GENSHIN_PACKAGE
import com.catsmoker.app.screens.editgamefiles.games.grid.GridPreferences
import com.catsmoker.app.screens.editgamefiles.games.newstate.NewStateIniTransform
import com.catsmoker.app.screens.editgamefiles.games.roblox.RobloxConfig
import com.catsmoker.app.screens.editgamefiles.games.starrail.HsrGameManager
import com.catsmoker.app.screens.editgamefiles.games.wutheringwaves.WuwaConfigManager
import com.catsmoker.app.screens.editgamefiles.model.GameType

enum class FileEngineeringImplementation { PROFILE, STAR_RAIL, WUTHERING_WAVES, GRID_AUTOSPORT, ROBLOX_TEST, SCOPED_CONFIG }
enum class FileEngineeringAccess { ROOT, SHIZUKU, DOCUMENT, MANUAL_EXPORT }
enum class FileEngineeringReadiness { IMPLEMENTED_UNVERIFIED, PRELIMINARY, BLOCKED }
enum class FileEngineeringCapability { PROFILES, EDIT_EXISTING, BACKUP_RESTORE, IMPORT, GENERATE, TEST_FLAG }

/**
 * A picker entry is not a support claim. Identity, routing and limitations must be declared
 * together; no fallback assigns a generic editor to an otherwise unimplemented game.
 */
data class FileEngineeringGame(
    val game: GameType,
    val packages: List<String>,
    val implementation: FileEngineeringImplementation,
    val capabilities: Set<FileEngineeringCapability>,
    val access: Set<FileEngineeringAccess>,
    val readiness: FileEngineeringReadiness,
    val limitation: String,
    val storePackage: String = packages.first()
)

internal val fileEngineeringGames: Map<GameType, FileEngineeringGame> = buildList {
    PUBG_PACKAGES.forEach { (game, pkg) ->
        add(FileEngineeringGame(game, listOf(pkg), FileEngineeringImplementation.PROFILE,
            setOf(FileEngineeringCapability.PROFILES, FileEngineeringCapability.EDIT_EXISTING,
                FileEngineeringCapability.IMPORT, FileEngineeringCapability.BACKUP_RESTORE),
            setOf(FileEngineeringAccess.ROOT, FileEngineeringAccess.SHIZUKU, FileEngineeringAccess.DOCUMENT),
            FileEngineeringReadiness.IMPLEMENTED_UNVERIFIED,
            "GVAS IntProperty patches require a recognized existing save; regional/version layouts can differ."))
    }
    add(FileEngineeringGame(GameType.GENSHIN_IMPACT, listOf(GENSHIN_PACKAGE), FileEngineeringImplementation.PROFILE,
        setOf(FileEngineeringCapability.PROFILES, FileEngineeringCapability.BACKUP_RESTORE),
        setOf(FileEngineeringAccess.ROOT, FileEngineeringAccess.SHIZUKU, FileEngineeringAccess.DOCUMENT, FileEngineeringAccess.MANUAL_EXPORT),
        FileEngineeringReadiness.PRELIMINARY,
        "Legacy hardware-model template; current game consumption is unverified. CN/cloud packages are not supported by this target."))
    add(FileEngineeringGame(GameType.PUBG_NEW_STATE, listOf(NewStateIniTransform.PACKAGE), FileEngineeringImplementation.PROFILE,
        setOf(FileEngineeringCapability.EDIT_EXISTING, FileEngineeringCapability.BACKUP_RESTORE),
        setOf(FileEngineeringAccess.ROOT), FileEngineeringReadiness.IMPLEMENTED_UNVERIFIED,
        "Private GameUserSettings.ini requires root and the recognized INI layout."))
    add(FileEngineeringGame(GameType.HSR, HsrGameManager.KNOWN_PACKAGES.map { it.first }, FileEngineeringImplementation.STAR_RAIL,
        setOf(FileEngineeringCapability.EDIT_EXISTING, FileEngineeringCapability.BACKUP_RESTORE),
        setOf(FileEngineeringAccess.ROOT), FileEngineeringReadiness.IMPLEMENTED_UNVERIFIED,
        "Private PlayerPrefs requires root. Flat-format clients have no proven FPS field; only their existing supported fields are editable."))
    add(FileEngineeringGame(GameType.WUWA, listOf(WuwaConfigManager.PACKAGE), FileEngineeringImplementation.WUTHERING_WAVES,
        setOf(FileEngineeringCapability.GENERATE, FileEngineeringCapability.IMPORT, FileEngineeringCapability.BACKUP_RESTORE),
        setOf(FileEngineeringAccess.ROOT, FileEngineeringAccess.SHIZUKU, FileEngineeringAccess.DOCUMENT),
        FileEngineeringReadiness.IMPLEMENTED_UNVERIFIED,
        "Global package only; engine/version-specific CVars may be ignored. SAF cannot stop the game or refresh its hash monitor."))
    add(FileEngineeringGame(GameType.GRID, listOf(GridPreferences.PACKAGE), FileEngineeringImplementation.GRID_AUTOSPORT,
        setOf(FileEngineeringCapability.EDIT_EXISTING, FileEngineeringCapability.BACKUP_RESTORE),
        setOf(FileEngineeringAccess.ROOT, FileEngineeringAccess.SHIZUKU, FileEngineeringAccess.DOCUMENT),
        FileEngineeringReadiness.IMPLEMENTED_UNVERIFIED,
        "Existing Feral registry required; missing keys are not created, binary FPS ceilings remain game-managed."))
    add(FileEngineeringGame(GameType.ROBLOX, listOf(RobloxConfig.PACKAGE), FileEngineeringImplementation.ROBLOX_TEST,
        setOf(FileEngineeringCapability.TEST_FLAG, FileEngineeringCapability.BACKUP_RESTORE),
        setOf(FileEngineeringAccess.ROOT, FileEngineeringAccess.SHIZUKU), FileEngineeringReadiness.BLOCKED,
        "File-write experiment only. Roblox consumption of the target flag is not established and the published allowlist omits it."))
    additionalGameEditors.values.forEach { editor ->
        val access = buildSet {
            add(FileEngineeringAccess.DOCUMENT)
            if (editor.files.any { it.paths.isNotEmpty() }) add(FileEngineeringAccess.ROOT)
            if (editor.files.any { it.paths.isNotEmpty() && !it.rootOnly }) add(FileEngineeringAccess.SHIZUKU)
        }
        add(FileEngineeringGame(editor.game, listOf(editor.packageName), FileEngineeringImplementation.SCOPED_CONFIG,
            setOf(FileEngineeringCapability.EDIT_EXISTING, FileEngineeringCapability.BACKUP_RESTORE),
            access, FileEngineeringReadiness.PRELIMINARY,
            when (editor.game) {
                GameType.MINECRAFT -> "Existing options.txt selected by the user; Android storage varies by version. Render distance is in blocks."
                GameType.TERRARIA -> "Existing mobile config.json selected by the user; desktop-only and absent fields are unsupported."
                GameType.CIVILIZATION_VI -> "Private files require root; Version header and duplicate sections must survive. Impact presets may override individual settings."
                GameType.ALIEN_ISOLATION -> "Specification files may clamp/reset settings; only proven scalar controls in the existing preferences file are edited."
                else -> "Android preference samples establish paths/types, not every quality mapping or game consumption. Unresolved settings stay unavailable."
            }))
    }
}.associateBy { it.game }.also { definitions ->
    check(definitions.keys == GameType.entries.filter { it != GameType.NONE }.toSet()) {
        "Every listed game must declare its File Engineering implementation and limitations."
    }
}

data class GameInstallation(val installedPackage: String?, val installed: Boolean?)

/** A failed query is unknown, not absent; prefer the first actually installed supported variant. */
internal fun FileEngineeringGame.probeInstallation(probe: (String) -> Boolean?): GameInstallation {
    var unknown = false
    for (pkg in packages) when (probe(pkg)) {
        true -> return GameInstallation(pkg, true)
        null -> unknown = true
        false -> Unit
    }
    return GameInstallation(null, if (unknown) null else false)
}

internal fun showFileEngineeringTools(game: GameType, installed: Boolean?, showAnyway: Boolean): Boolean =
    game != GameType.NONE && (installed != false || showAnyway)
