package com.catsmoker.app.screens.editgamefiles.games.romealexander

import com.catsmoker.app.screens.editgamefiles.model.*

val romeAlexanderConfig = feralEditorSpec(
    GameType.ROME_ALEXANDER, "com.feralinteractive.rometwalex",
    feralConfigFile("The Creative Assembly Limited/Rome Total War - Alexander/Setup" to integerFields(
        "FixedScreenHeight", "EnableVSync", "feral_battle_map_shadows", "feral_campaign_map_shadows", "feral_detailed_unit_shadows"
    )), "docs/catalogues/rome-alexander.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "feral_battle_map_shadows" to ConfigControl.Toggle(),
        "feral_campaign_map_shadows" to ConfigControl.Toggle(),
        "feral_detailed_unit_shadows" to ConfigControl.Toggle()
    )
)
