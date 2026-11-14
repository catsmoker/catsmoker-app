package com.catsmoker.app.screens.editgamefiles.games.terraria

import com.catsmoker.app.screens.editgamefiles.model.*

/** Mobile/desktop schemas differ: only documented mobile-capable keys already present are edited. */
val terrariaConfig = ConfigEditorSpec(
    GameType.TERRARIA, "com.and.games505.TerrariaPaid",
    listOf(EditableConfigFile("config.json", ConfigFormat.TERRARIA_JSON,
        integerFields("WaterfallDrawLimit", "FilterLimit", "FilterPriorityThreshold", "MultiplayerNPCSmoothingRange", "TeamNameplateDistance")
            .map { it.copy(control = ConfigControl.Number()) } +
            ConfigField("", "DisableIntenseVisualEffects", ConfigScalar.BOOLEAN, ConfigControl.Toggle("false", "true")))),
    "https://terraria.wiki.gg/wiki/Config.json_settings"
)
