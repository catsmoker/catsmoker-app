package com.catsmoker.app.screens.editgamefiles.games.medieval

import com.catsmoker.app.screens.editgamefiles.model.*

val medievalConfig = feralEditorSpec(
    GameType.MEDIEVAL_II, "com.feralinteractive.medieval2_android",
    feralConfigFile(
        "Original Developer/Medieval II Total War/Setup" to integerFields("FixedScreenHeight", "ResponsiveFPS"),
        "Original Developer/Medieval II Total War/Setup/VideoSettings" to
            (integerFields("CampaignShadows", "TextureFiltering") + tokenFields("ShadowQuality", "TextureQuality"))
    ), "docs/catalogues/medieval-2-total-war.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "CampaignShadows" to ConfigControl.Toggle()
    )
)
