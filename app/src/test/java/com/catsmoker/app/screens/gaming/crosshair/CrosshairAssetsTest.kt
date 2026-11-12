package com.catsmoker.app.screens.gaming.crosshair

import org.junit.Assert.assertEquals
import org.junit.Test

class CrosshairAssetsTest {

    @Test
    fun keepsOnlyTheBundledStyleAndRecognizedDownloads() {
        assertEquals("scope7.png", CrosshairAssets.normalize("scope7.png"))
        assertEquals("downloaded_scope100.png", CrosshairAssets.normalize("downloaded_scope100.png"))
        assertEquals("downloaded_scope100.png", CrosshairAssets.downloadedAssetName("scope100.png"))
    }

    @Test
    fun invalidOrMissingNamesUseTheDefaultAsset() {
        assertEquals("scope7.png", CrosshairAssets.normalize(null))
        assertEquals("scope7.png", CrosshairAssets.normalize(""))
        assertEquals("scope7.png", CrosshairAssets.normalize("../scope1.png"))
        assertEquals("scope7.png", CrosshairAssets.normalize("scope8.png"))
        assertEquals("scope7.png", CrosshairAssets.normalize("scope2.png"))
    }
}
