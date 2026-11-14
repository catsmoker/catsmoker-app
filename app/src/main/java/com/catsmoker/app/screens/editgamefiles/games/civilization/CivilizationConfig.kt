package com.catsmoker.app.screens.editgamefiles.games.civilization

import com.catsmoker.app.screens.editgamefiles.model.*

private const val CIV_DIR = "/data/user/0/com.aspyr.civvi/files/Sid Meier's Civilization VI"

val civilizationConfig = ConfigEditorSpec(
    GameType.CIVILIZATION_VI, "com.aspyr.civvi",
    listOf(
        EditableConfigFile("GraphicsOptions.txt", ConfigFormat.CIV6_INI,
            integerFields("PerformanceImpact", "MemoryImpact", "MSAA", "VSync", "RefreshRateInHz").map { it.copy(scope = "Video") } +
                ConfigField("Video", "ShadowMapResolution", ConfigScalar.INTEGER, ConfigControl.Number(1, ConfigUnit.PIXELS)) +
                listOf("AO" to "EnableAO", "Bloom" to "EnableBloom", "Shadows" to "EnableShadows")
                    .map { (scope, name) -> ConfigField(scope, name, ConfigScalar.INTEGER, ConfigControl.Toggle()) },
            listOf("$CIV_DIR/GraphicsOptions.txt"), rootOnly = true),
        EditableConfigFile("AppOptions.txt", ConfigFormat.CIV6_INI,
            integerFields("RenderWidth", "RenderHeight").map { it.copy(scope = "Video", control = ConfigControl.Number(1, ConfigUnit.PIXELS)) },
            listOf("$CIV_DIR/AppOptions.txt"), rootOnly = true)
    ),
    "https://github.com/andrewr8712/legend-tuner/blob/2fba90d6e227649b7391159ea4038050e8612cc9/docs/candidates/civilization-vi.md"
)
