package com.catsmoker.app.screens.editgamefiles.games.hitmanabsolution

import com.catsmoker.app.screens.editgamefiles.model.*

val hitmanAbsolutionConfig = feralEditorSpec(
    GameType.HITMAN_ABSOLUTION, "com.feralinteractive.hitmanabsolution_android",
    feralConfigFile(
        "IO Interactive/Hitman Absolution" to integerFields("ShadowQuality", "TextureFilter", "TextureResolution", "VSync", "Bloom", "FXAA"),
        "IO Interactive/Hitman Absolution/Setup" to integerFields("FixedScreenHeight", "ResponsiveFPS")
    ), "docs/catalogues/hitman-absolution.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "Bloom" to ConfigControl.Toggle(), "FXAA" to ConfigControl.Toggle()
    )
)
