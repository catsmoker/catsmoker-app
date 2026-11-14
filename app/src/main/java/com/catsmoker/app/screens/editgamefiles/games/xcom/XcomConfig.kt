package com.catsmoker.app.screens.editgamefiles.games.xcom

import com.catsmoker.app.screens.editgamefiles.model.*

val xcomConfig = feralEditorSpec(
    GameType.XCOM_2, "com.feralinteractive.xcom2_android",
    feralConfigFile("Feral Interactive/XCOM 2/Setup" to integerFields(
        "FixedScreenHeight", "GFXBloom", "GFXShadowQuality", "GFXTextureDetail", "GFXTextureFiltering", "NonIdleFPS"
    )), "docs/catalogues/xcom-2.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "GFXBloom" to ConfigControl.Toggle()
    )
)
