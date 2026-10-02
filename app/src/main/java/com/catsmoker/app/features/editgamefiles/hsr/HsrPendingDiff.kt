package com.catsmoker.app.features.editgamefiles.hsr

/**
 * Which fields of an HSR working copy differ from its baseline: the pending-change list
 * behind the dirty dots and the pending counts.
 *
 * The reference tracks this per field (`GraphicsViewModel.modifiedFields`,
 * `pendingChangesCount`); the app's [com.catsmoker.app.features.editgamefiles.EditHistory]
 * compares whole values, so these pure diffs bridge the two — computed wherever the
 * working copy changes, never stored, so they cannot disagree with what the screen shows.
 * Names are internal field keys, not user-visible copy.
 */
fun pendingGraphicsFields(base: HsrGraphicsSettings, current: HsrGraphicsSettings): List<String> {
    val out = ArrayList<String>()
    if (base.fps != current.fps) out.add("fps")
    if (base.enableVSync != current.enableVSync) out.add("enableVSync")
    if (base.renderScale != current.renderScale) out.add("renderScale")
    if (base.resolutionQuality != current.resolutionQuality) out.add("resolutionQuality")
    if (base.shadowQuality != current.shadowQuality) out.add("shadowQuality")
    if (base.lightQuality != current.lightQuality) out.add("lightQuality")
    if (base.characterQuality != current.characterQuality) out.add("characterQuality")
    if (base.envDetailQuality != current.envDetailQuality) out.add("envDetailQuality")
    if (base.reflectionQuality != current.reflectionQuality) out.add("reflectionQuality")
    if (base.sfxQuality != current.sfxQuality) out.add("sfxQuality")
    if (base.bloomQuality != current.bloomQuality) out.add("bloomQuality")
    if (base.aaMode != current.aaMode) out.add("aaMode")
    if (base.enableMetalFXSU != current.enableMetalFXSU) out.add("enableMetalFXSU")
    if (base.enableHalfResTransparent != current.enableHalfResTransparent) out.add("enableHalfResTransparent")
    if (base.enableSelfShadow != current.enableSelfShadow) out.add("enableSelfShadow")
    if (base.dlssQuality != current.dlssQuality) out.add("dlssQuality")
    if (base.particleTrailSmoothness != current.particleTrailSmoothness) out.add("particleTrailSmoothness")
    if (base.screenWidth != current.screenWidth) out.add("screenWidth")
    if (base.screenHeight != current.screenHeight) out.add("screenHeight")
    if (base.fullscreenMode != current.fullscreenMode) out.add("fullscreenMode")
    if (base.graphicsQuality != current.graphicsQuality) out.add("graphicsQuality")
    if (base.enablePsoShaderWarmup != current.enablePsoShaderWarmup) out.add("enablePsoShaderWarmup")
    if (base.isUserSave != current.isUserSave) out.add("isUserSave")
    if (base.version != current.version) out.add("version")
    return out
}

/** Same pending-field diff for the QoL-preferences working copy. */
fun pendingPrefsFields(base: HsrGamePreferences, current: HsrGamePreferences): List<String> {
    val out = ArrayList<String>()
    if (base.textLanguage != current.textLanguage) out.add("textLanguage")
    if (base.audioLanguage != current.audioLanguage) out.add("audioLanguage")
    if (base.videoBlacklist != current.videoBlacklist) out.add("videoBlacklist")
    if (base.audioBlacklist != current.audioBlacklist) out.add("audioBlacklist")
    if (base.speedUpOpen != current.speedUpOpen) out.add("speedUpOpen")
    if (base.autoBattleOpen != current.autoBattleOpen) out.add("autoBattleOpen")
    return out
}
