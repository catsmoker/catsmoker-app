package com.catsmoker.app.screens.editgamefiles.games.gridlegends

import com.catsmoker.app.screens.editgamefiles.model.*

val gridLegendsConfig = feralEditorSpec(
    GameType.GRID_LEGENDS, "com.feralinteractive.gridlegends_android",
    feralConfigFile(
        "Feral Interactive/GRIDLegends/GraphicsSettings" to integerFields("FramerateLimit", "Shadows", "TextureQuality"),
        "Feral Interactive/GRIDLegends/Setup" to integerFields("FixedScreenHeight", "EnableSSR")
    ), "testdata/preferences-sample.xml", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "EnableSSR" to ConfigControl.Toggle()
    )
)
