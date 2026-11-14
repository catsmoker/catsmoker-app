package com.catsmoker.app.screens.editgamefiles.games.hitmanbloodmoney

import com.catsmoker.app.screens.editgamefiles.model.*

val hitmanBloodMoneyConfig = feralEditorSpec(
    GameType.HITMAN_BLOOD_MONEY, "com.feralinteractive.hitmanbloodmoney_android",
    feralConfigFile("Feral Interactive/Hitman Blood Money/Setup" to integerFields(
        "FixedScreenHeight", "DirectionalShadowResolution", "ShadowResolution", "EnableReflections"
    )), "docs/catalogues/hitman-blood-money.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "DirectionalShadowResolution" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "ShadowResolution" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "EnableReflections" to ConfigControl.Toggle()
    )
)
