package com.catsmoker.app.screens.editgamefiles.games.romebarbarian

import com.catsmoker.app.screens.editgamefiles.model.*

val romeBarbarianConfig = feralEditorSpec(
    GameType.ROME_BARBARIAN, "com.feralinteractive.rometwbi",
    feralConfigFile("The Creative Assembly Limited/Rome Total War - Barbarian Invasion/Setup" to integerFields(
        "FixedScreenHeight", "EnableVSync", "feral_battle_map_shadows", "feral_campaign_map_shadows", "feral_detailed_unit_shadows"
    )), "docs/catalogues/rome-barbarian-invasion.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "feral_battle_map_shadows" to ConfigControl.Toggle(),
        "feral_campaign_map_shadows" to ConfigControl.Toggle(),
        "feral_detailed_unit_shadows" to ConfigControl.Toggle()
    )
)
