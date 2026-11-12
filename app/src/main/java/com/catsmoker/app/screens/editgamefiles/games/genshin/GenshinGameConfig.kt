package com.catsmoker.app.screens.editgamefiles.games.genshin

import com.catsmoker.app.R

import com.catsmoker.app.screens.editgamefiles.model.GameConfig
import com.catsmoker.app.screens.editgamefiles.model.GameProfile

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
internal fun buildGenshinConfig(): GameConfig {
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
