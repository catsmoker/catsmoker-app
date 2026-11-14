package com.catsmoker.app.screens.editgamefiles.games.genshin

import com.catsmoker.app.R

import com.catsmoker.app.screens.editgamefiles.model.GameConfig
import com.catsmoker.app.screens.editgamefiles.model.GameProfile

internal const val GENSHIN_PACKAGE = "com.miHoYo.GenshinImpact"

/**
 * Genshin Impact, from the reference project `GenshinConfig-main`.
 *
 * The game reads `hardware_model_config.json` out of its own `files/` directory and looks the
 * entries up by the device's real model, so the tuned entry in the bundled template carries a
 * placeholder model that every delivery channel substitutes with `Build.MODEL` — the reference
 * README's manual instruction ("`Your Device Model` must match the model of the device you are
 * using"), automated. The asset is the reference's file byte-for-byte; it is deliberately not
 * valid JSON (unquoted `ASTC`, `01`, `00000001h`, trailing commas), and the game's lenient
 * parser is claimed by the reference to accept it. Current Android consumption is unverified;
 * this legacy template is never parsed or re-serialised as if it were standard JSON.
 */
internal fun buildGenshinConfig(): GameConfig {
    return GameConfig(
        packageName = GENSHIN_PACKAGE,
        saveDir = "/Android/data/$GENSHIN_PACKAGE/files/",
        saveFile = "hardware_model_config.json",
        profiles = listOf(
            GameProfile(R.string.gf_profile_genshin, "Genshin/hardware_model_config.json")
        ),
        requiresDeviceModel = true
    )
}
