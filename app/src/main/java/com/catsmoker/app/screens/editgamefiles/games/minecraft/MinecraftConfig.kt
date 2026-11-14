package com.catsmoker.app.screens.editgamefiles.games.minecraft

import com.catsmoker.app.screens.editgamefiles.model.*

/** Storage moved between Bedrock versions; use the user's document, not an invented universal path. */
val minecraftConfig = ConfigEditorSpec(
    GameType.MINECRAFT, "com.mojang.minecraftpe",
    listOf(EditableConfigFile("options.txt", ConfigFormat.BEDROCK_OPTIONS,
        listOf(
            ConfigField("", "gfx_max_framerate", ConfigScalar.INTEGER, ConfigControl.Unlock("0")),
            ConfigField("", "gfx_renderdistance_new", ConfigScalar.INTEGER, ConfigControl.Number(1, ConfigUnit.BLOCKS)),
            ConfigField("", "gfx_fancygraphics", ConfigScalar.INTEGER, ConfigControl.Toggle())
        ))),
    "https://minecraft.wiki/w/Options.txt_(Bedrock_Edition)"
)
