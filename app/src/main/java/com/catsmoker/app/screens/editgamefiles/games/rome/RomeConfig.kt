package com.catsmoker.app.screens.editgamefiles.games.rome

import com.catsmoker.app.screens.editgamefiles.model.*

val romeConfig = feralEditorSpec(
    GameType.ROME_TOTAL_WAR, "com.feralinteractive.rometw",
    feralConfigFile("The Creative Assembly Limited/Rome Total War/Setup" to integerFields(
        "FixedScreenHeight", "EnableVSync", "feral_battle_map_shadows", "feral_campaign_map_shadows", "feral_detailed_unit_shadows"
    )), "docs/catalogues/rome-total-war.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "feral_battle_map_shadows" to ConfigControl.Toggle(),
        "feral_campaign_map_shadows" to ConfigControl.Toggle(),
        "feral_detailed_unit_shadows" to ConfigControl.Toggle()
    )
)
