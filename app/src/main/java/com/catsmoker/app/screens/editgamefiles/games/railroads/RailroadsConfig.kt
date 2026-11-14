package com.catsmoker.app.screens.editgamefiles.games.railroads

import com.catsmoker.app.screens.editgamefiles.model.*

val railroadsConfig = feralEditorSpec(
    GameType.RAILROADS, "com.feralinteractive.railroads_android",
    feralConfigFile("Original Developer/Sid Meier's Railroads!/Setup" to integerFields(
        "FixedScreenHeight", "ShaderQuality", "ShadowQuality", "TextureQuality", "RenderUnderNotch"
    )), "docs/catalogues/sid-meiers-railroads.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "RenderUnderNotch" to ConfigControl.Toggle()
    )
)
