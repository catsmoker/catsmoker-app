package com.catsmoker.app.screens.editgamefiles.games.companyofheroes

import com.catsmoker.app.screens.editgamefiles.model.*

val companyOfHeroesConfig = feralEditorSpec(
    GameType.COMPANY_OF_HEROES, "com.feralinteractive.companyofheroes_android",
    feralConfigFile(
        "Feral Interactive/Company of Heroes/Setup" to integerFields("FixedScreenHeight", "FramerateManagerNormalResponsiveFPS"),
        "Original Developer/Company of Heroes/Config" to integerFields("shadows", "texturedetail")
    ), "docs/catalogues/company-of-heroes.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS)
    )
)
