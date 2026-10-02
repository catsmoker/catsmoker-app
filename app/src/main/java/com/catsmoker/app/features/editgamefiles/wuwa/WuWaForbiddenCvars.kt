package com.catsmoker.app.features.editgamefiles.wuwa

/**
 * The "forbidden" cvars: keys the game's anti-cheat / integrity checks are known to watch,
 * stripped from generated configs when the user turns restricted cvars off. Key list and
 * matching rules (case-insensitive, `+`/`-` variants, `r.` re-prefixed for bare names) are
 * `reference/gamingtools/WuWa-Config-Android-main/.../config/ForbiddenCvars.kt` verbatim —
 * that file was read before this one was written — synced 2026-09-25 with the newer
 * community list `reference/gamingtools/Mobile-WuWa-Config-main/.github/forbidden_cvars.txt`
 * (v3.6, 51 entries), which was read line-by-line for the sync: every v3.6 key is covered,
 * and [WuWaForbiddenCvarsTest] pins the parity mechanically. Matching stays key-exact and
 * `+CVars=`-aware; the repo's substring CI match is deliberately not adopted. The exact
 * spelling of every key is the whole payload here: a renamed key strips nothing.
 *
 * Both spellings of the CppEffect(s)System key are kept even though v3.6 ships only the
 * singular one and one spelling is surely a typo: stripping a key the game does not watch
 * is harmless, leaving a watched key in place is not — and which one the watch list uses
 * is the reference's finding, not ours to correct.
 */
object WuWaForbiddenCvars {

    val ALL: Set<String> = setOf(
        "r.Kuro.SkeletalMesh.LODDistanceScale",
        "r.Streaming.Boost",
        "r.Streaming.PoolSize",
        "r.Streaming.LimitPoolSizeTOVRAM",
        "r.Shadow.MaxCSMResolution",
        "r.Streaming.MinBoost",
        "r.MipMapLODBias",
        "r.TextureGroup.Landscape.TextureLODBias",
        "r.Kuro.TexturePool.ExtraBudgetMB",
        "r.Streaming.CPUReadback",
        "r.Streaming.UseAsyncCPUReadback",
        "r.Streaming.MaxNumTexturesToStreamPerFrame",
        "r.Streaming.MinMipForSplitRequest",
        "r.Streaming.UseFixedPoolsize",
        "r.Streaming.UseAllMips",
        "r.Streaming.MaxTempMemoryAllowed",
        "r.RayTracing.LimitDevice",
        "r.DetailMode",
        "r.MaterialQualityLevel",
        "r.KuroMaterialQualityLevel",
        "r.ViewDistanceScale",
        "Kuro.CppEffectsSystem.UseLowMemoryPlayerEffectLruCapacity",
        "Kuro.CppEffectSystem.UseLowMemoryPlayerEffectLruCapacity",
        "r.AsyncComputePSO",
        "r.Streamline.DLSSG.RetainResourcesWhenOff",
        "r.MobileContentScaleFactor",
        "r.SecondaryScreenPercentage.GameViewport",
        "r.ScreenPercentage",
        "r.AFME.Enable",
        "r.MFRC.Enable",
        "r.FEstimation.Option",
        // ── v3.6 additions (Mobile-WuWa-Config-main/.github/forbidden_cvars.txt) ──
        "r.KuroFI.Enable",
        "r.LightMaxDrawDistanceScale",
        "r.Mobile.DeviceEvaluation",
        "r.ParallelInitViews",
        "r.ScreenSizeCullRatioFactor",
        "r.Shadow.DistanceScale",
        "r.Shadow.MaxResolution",
        "r.Streaming.AllowExtendedPoolSize",
        "r.Streaming.DistancePriority.Texture2DArrayPriority",
        "r.Streaming.ExtendedPoolSizeForceAllMipsThresholdPercentage",
        "r.Streaming.ExtendedPoolSizeThresholdPercentage",
        "r.Streaming.KuroExtraPoolSize",
        "r.Streaming.MaxExtendedPoolSizePercentage",
        "r.Streaming.MaxExtendedPoolsizeVRAMPercentage",
        "r.Streaming.MaxTempMemoryAllowedForTexture2DArray",
        "r.Streaming.PoolSizeExtraForTexture2DArray",
        "r.Streaming.Texture2DArrayStreamOutHysteresis",
        "r.VolumetricFog",
        "r.VRS.EnableMaterial",
        "r.VRS.EnableMesh",
        "s.PriorityAsyncLoadingExtraTime",
    )

    private val commonVariants: Set<String> = run {
        val variants = mutableSetOf<String>()
        for (key in ALL) {
            val lower = key.lowercase()
            variants.add(lower)
            variants.add("+$lower")
            variants.add("-$lower")
            if (!lower.startsWith("r.") && !lower.startsWith("kuro.")) {
                variants.add("r.$lower")
            }
        }
        variants
    }

    fun isForbidden(cvarKey: String): Boolean =
        commonVariants.contains(cvarKey.trim().lowercase())

    /**
     * Removes forbidden cvar lines from ini text, keeping everything else — comments,
     * section headers, blank lines — byte-for-byte where it stays. The leading `+CVars=` /
     * `-CVars=` directive is stripped *before* splitting on `=`; otherwise the delimiter
     * inside the directive would be parsed as part of the key (the reference's own comment
     * and fix).
     */
    fun stripForbiddenCvars(iniContent: String): String {
        val sb = StringBuilder()
        for (line in iniContent.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith(";") || trimmed.startsWith("#") ||
                trimmed.startsWith("//") || trimmed.isEmpty()
            ) {
                sb.appendLine(line)
                continue
            }
            val body = trimmed.removePrefix("+CVars=").removePrefix("-CVars=").trim()
            val eqIdx = body.indexOf('=')
            val keyPart = if (eqIdx >= 0) body.substring(0, eqIdx).trim() else body.trim()
            if (!isForbidden(keyPart)) {
                sb.appendLine(line)
            }
        }
        return sb.toString()
    }
}
