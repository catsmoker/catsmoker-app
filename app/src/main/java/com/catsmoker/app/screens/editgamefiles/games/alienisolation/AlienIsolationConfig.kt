package com.catsmoker.app.screens.editgamefiles.games.alienisolation

import com.catsmoker.app.screens.editgamefiles.model.*

val alienIsolationConfig = feralEditorSpec(
    GameType.ALIEN_ISOLATION, "com.feralinteractive.alienisolation_android",
    feralConfigFile("Feral Interactive/Alien Isolation/Setup" to integerFields(
        "FixedScreenHeight", "VideoSetting_ShadowMapping", "VideoSetting_ShadowMapResolution", "VideoSetting_Texture Filtering",
        "VideoSetting_ChromaticAberrations", "VideoSetting_Depth of Field", "VideoSetting_MotionBlur",
        "VideoSetting_Planar Reflections", "VideoSetting_Volumetric Lighting"
    )), "docs/catalogues/alien-isolation.md", controls = mapOf(
        "FixedScreenHeight" to ConfigControl.Number(1, ConfigUnit.PIXELS),
        "VideoSetting_ChromaticAberrations" to ConfigControl.Toggle(),
        "VideoSetting_Depth of Field" to ConfigControl.Toggle(),
        "VideoSetting_MotionBlur" to ConfigControl.Toggle(),
        "VideoSetting_Planar Reflections" to ConfigControl.Toggle(),
        "VideoSetting_Volumetric Lighting" to ConfigControl.Toggle()
    )
)
