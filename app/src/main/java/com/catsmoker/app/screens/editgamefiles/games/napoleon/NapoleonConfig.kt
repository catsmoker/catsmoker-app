package com.catsmoker.app.screens.editgamefiles.games.napoleon

import com.catsmoker.app.screens.editgamefiles.model.*

val napoleonConfig = feralEditorSpec(
    GameType.TOTAL_WAR_NAPOLEON, "com.feralinteractive.napoleon_android",
    feralConfigFile(
        "The Creative Assembly Limited/Napoleon Total War - Mobile/Setup" to integerFields("FixedScreenHeight"),
        "The Creative Assembly Limited/Napoleon Total War - Mobile/Setup/GraphicsSettings" to
            integerFields("ShadowsQuality", "SoftShadows", "HDR", "TextureQuality", "TextureFiltering", "WaterQuality"),
        "IndirectX/Direct3D/Config" to integerFields("FramerateManagerBattleResponsiveFPS", "FramerateManagerCampaignResponsiveFPS")
    ), "docs/catalogues/napoleon-total-war.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "SoftShadows" to ConfigControl.Toggle(), "HDR" to ConfigControl.Toggle()
    )
)
