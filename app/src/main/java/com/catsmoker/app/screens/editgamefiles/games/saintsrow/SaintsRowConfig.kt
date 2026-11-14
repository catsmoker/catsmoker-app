package com.catsmoker.app.screens.editgamefiles.games.saintsrow

import com.catsmoker.app.screens.editgamefiles.model.*

val saintsRowConfig = feralEditorSpec(
    GameType.SAINTS_ROW, "com.feralinteractive.saintsrow3_android",
    feralConfigFile(
        "Feral Interactive/Saints Row The Third/Setup" to integerFields("FixedScreenHeight"),
        *listOf("BatterySaverPreset", "GraphicsPreset", "PerformancePreset").map { preset ->
            "Feral Interactive/Saints Row The Third/Setup/$preset" to
                (integerFields("GfxResponsiveFps") + tokenFields("GfxShadow", "GfxSSLR"))
        }.toTypedArray()
    ), "docs/catalogues/saints-row-the-third.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "GfxSSLR" to ConfigControl.Toggle("off", "on")
    )
)
