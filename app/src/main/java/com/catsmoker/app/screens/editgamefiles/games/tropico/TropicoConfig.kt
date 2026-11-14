package com.catsmoker.app.screens.editgamefiles.games.tropico

import com.catsmoker.app.screens.editgamefiles.model.*

val tropicoConfig = feralEditorSpec(
    GameType.TROPICO, "com.feralinteractive.tropico3",
    feralConfigFile(
        "Haemimont Games/Tropico 3: Gold Edition/Setup" to integerFields("FixedScreenHeight"),
        "Haemimont Games/Tropico 3: Gold Edition/Setup/GFX" to tokenFields("SceneRez", "ShadowQuality", "TextureLevel", "WaterQuality")
    ), "docs/catalogues/tropico-3.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "SceneRez" to ConfigControl.Choice(listOf("Off", "Low", "Medium", "High")),
        "ShadowQuality" to ConfigControl.Choice(listOf("Off", "Low", "Medium", "High")),
        "TextureLevel" to ConfigControl.Choice(listOf("Off", "Low", "Medium", "High")),
        "WaterQuality" to ConfigControl.Choice(listOf("Off", "Low", "Medium", "High"))
    )
)
