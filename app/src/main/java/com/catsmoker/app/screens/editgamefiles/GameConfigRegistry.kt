package com.catsmoker.app.screens.editgamefiles

import com.catsmoker.app.screens.editgamefiles.games.genshin.buildGenshinConfig
import com.catsmoker.app.screens.editgamefiles.games.newstate.buildNewStateConfig
import com.catsmoker.app.screens.editgamefiles.games.pubg.buildPubgConfig
import com.catsmoker.app.screens.editgamefiles.model.GameConfig
import com.catsmoker.app.screens.editgamefiles.model.GameType

/** Profile-based entries only; Star Rail, Wuthering Waves and GRID own live embedded editors. */
internal fun populateGameConfigs(gameConfigs: MutableMap<GameType, GameConfig>) {
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
