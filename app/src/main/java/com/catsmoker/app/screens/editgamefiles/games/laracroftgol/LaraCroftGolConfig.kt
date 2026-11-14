package com.catsmoker.app.screens.editgamefiles.games.laracroftgol

import com.catsmoker.app.screens.editgamefiles.model.*

val laraCroftGolConfig = feralEditorSpec(
    GameType.LARA_CROFT_GOL, "com.feralinteractive.laracroftgol_android",
    feralConfigFile("Crystal Dynamics/Lara Croft: GoL/Graphics" to integerFields(
        "TextureDetail", "BestTextureFilter", "EnableShadows", "EnableReflection", "EnableVSync"
    )), "docs/catalogues/lara-croft-guardian-of-light.md", controls = mapOf(
        "EnableShadows" to ConfigControl.Toggle(), "EnableReflection" to ConfigControl.Toggle()
    )
)
