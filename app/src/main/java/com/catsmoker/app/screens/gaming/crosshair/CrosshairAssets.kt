package com.catsmoker.app.screens.gaming.crosshair

import com.catsmoker.app.screens.gaming.crosshair.CrosshairCatalog

internal object CrosshairAssets {
    const val DEFAULT_ASSET = "scope7.png"

    private val bundledAsset = Regex("""scope7\.png""", RegexOption.IGNORE_CASE)
    private val downloadedAsset = Regex("""downloaded_scope[1-9]\d*\.png""", RegexOption.IGNORE_CASE)

    fun normalize(raw: String?): String {
        val name = raw?.trim() ?: return DEFAULT_ASSET
        return when {
            bundledAsset.matches(name) -> DEFAULT_ASSET
            downloadedAsset.matches(name) -> name.lowercase()
            else -> DEFAULT_ASSET
        }
    }

    fun downloadedAssetName(filename: String): String {
        require(CrosshairCatalog.isValidFilename(filename) && filename != DEFAULT_ASSET) {
            "Unsupported crosshair filename"
        }
        return "downloaded_$filename"
    }

    fun isDownloadedAsset(name: String): Boolean = downloadedAsset.matches(normalize(name))
}
