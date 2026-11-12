package com.catsmoker.app.screens.editgamefiles.games.pubg

import com.catsmoker.app.R

import com.catsmoker.app.screens.editgamefiles.games.pubg.PubgSavePatcher
import com.catsmoker.app.screens.editgamefiles.games.pubg.PubgUploadPlan
import com.catsmoker.app.screens.editgamefiles.model.GameConfig
import com.catsmoker.app.screens.editgamefiles.model.GameProfile

internal fun buildPubgConfig(packageName: String): GameConfig {
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
