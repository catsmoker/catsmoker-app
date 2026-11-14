package com.catsmoker.app.screens.editgamefiles.games.empire

import com.catsmoker.app.screens.editgamefiles.model.*

val empireConfig = feralEditorSpec(
    GameType.TOTAL_WAR_EMPIRE, "com.feralinteractive.empire_android",
    feralConfigFile(
        "The Creative Assembly Limited/Total War EMPIRE/Setup" to integerFields("FixedScreenHeight"),
        "The Creative Assembly Limited/Total War EMPIRE/Setup/GraphicsSettings" to
            integerFields("ShadowsQuality", "SoftShadows", "HDR", "TextureQuality", "TextureFiltering", "WaterQuality"),
        "IndirectX/Direct3D/Config" to integerFields("FramerateManagerBattleResponsiveFPS", "FramerateManagerCampaignResponsiveFPS")
    ), "docs/catalogues/total-war-empire.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "SoftShadows" to ConfigControl.Toggle(), "HDR" to ConfigControl.Toggle()
    )
)
