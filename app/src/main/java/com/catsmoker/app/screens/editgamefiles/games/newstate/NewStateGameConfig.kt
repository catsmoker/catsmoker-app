package com.catsmoker.app.screens.editgamefiles.games.newstate

import com.catsmoker.app.R

import com.catsmoker.app.screens.editgamefiles.games.newstate.NewStateIniTransform
import com.catsmoker.app.screens.editgamefiles.model.GameConfig
import com.catsmoker.app.screens.editgamefiles.model.GameProfile

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
internal fun buildNewStateConfig(): GameConfig {
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
