package com.catsmoker.app.screens.editgamefiles.games.laracrofttoo

import com.catsmoker.app.screens.editgamefiles.model.*

val laraCroftTooConfig = feralEditorSpec(
    GameType.LARA_CROFT_TOO, "com.feralinteractive.laracrofttoo_android",
    feralConfigFile(
        "Feral Interactive/Lara Croft and the Temple of Osiris/Setup" to integerFields("FixedScreenHeight"),
        *listOf("BatterySaverPreset", "GraphicsPreset", "PerformancePreset").map { preset ->
        "Crystal Dynamics/Lara Croft and the Temple of Osiris/Graphics/$preset" to
            integerFields("ShadowQuality", "TextureQuality", "TextureFiltering", "VSync", "Bloom", "FXAA")
    }.toTypedArray()), "docs/catalogues/lara-croft-temple-of-osiris.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "Bloom" to ConfigControl.Toggle(), "FXAA" to ConfigControl.Toggle()
    )
)
