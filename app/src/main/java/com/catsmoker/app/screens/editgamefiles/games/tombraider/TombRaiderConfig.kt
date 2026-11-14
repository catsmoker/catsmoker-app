package com.catsmoker.app.screens.editgamefiles.games.tombraider

import com.catsmoker.app.screens.editgamefiles.model.*

val tombRaiderConfig = feralEditorSpec(
    GameType.TOMB_RAIDER, "com.feralinteractive.tombraider_android",
    feralConfigFile(
        "Crystal Dynamics/Tomb Raider/Setup" to integerFields("FixedScreenHeight"),
        "Crystal Dynamics/Tomb Raider/Setup/GraphicsSettings" to integerFields(
            "BestTextureFilter", "ShadowMode", "ShadowResolution", "TextureQuality", "VSyncMode",
            "EnableMotionBlur", "EnablePostProcess", "EnableScreenEffects", "EnableTessellation"
        )
    ), "docs/catalogues/tomb-raider.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "EnableMotionBlur" to ConfigControl.Toggle(), "EnablePostProcess" to ConfigControl.Toggle(),
        "EnableScreenEffects" to ConfigControl.Toggle(), "EnableTessellation" to ConfigControl.Toggle()
    )
)
