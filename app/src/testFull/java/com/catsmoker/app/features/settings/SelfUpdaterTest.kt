package com.catsmoker.app.features.settings

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GitHub-release selection for the full-variant self-updater, driven by the
 * real API shape (fetched 2026-10-09): tags are `{versionCode}-{versionName}`
 * (`9-2.0.3`), draft/prerelease flags are present, and APK assets carry a
 * `digest: "sha256:..."` plus size. Fixtures below are trimmed to those
 * fields so the tests pin the contract without snapshotting 100 KB of JSON.
 */
class SelfUpdaterTest {

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        assets: String = ""
    ): String =
        """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"body":"notes","assets":[$assets]}"""

    private fun apkAsset(
        name: String = "app-full-release.apk",
        url: String = "https://github.com/catsmoker/catsmoker-app/releases/download/9-2.0.3/app-full-release.apk",
        digest: String = "sha256:6dacd0be509334a0ffa7457f6b08a40e30c9f1df7b1dee78b1424eaf4f70ace1",
        size: Long = 5469625L
    ): String =
        """{"name":"$name","browser_download_url":"$url","digest":"$digest","size":$size}"""

    /** GitHub lists assets in upload order: the per-ABI splits come before the universal APK. */
    private fun releaseWithSplits(): String = release(
        "11-2.0.5",
        assets = listOf(
            apkAsset(
                name = "app-full-arm64-v8a-release.apk",
                url = "https://github.com/catsmoker/catsmoker-app/releases/download/11-2.0.5/app-full-arm64-v8a-release.apk",
                digest = "sha256:" + "a".repeat(64),
                size = 5821516L
            ),
            apkAsset(
                name = "app-full-armeabi-v7a-release.apk",
                url = "https://github.com/catsmoker/catsmoker-app/releases/download/11-2.0.5/app-full-armeabi-v7a-release.apk",
                digest = "sha256:" + "b".repeat(64),
                size = 5802442L
            ),
            apkAsset(
                name = "app-full-release.apk",
                url = "https://github.com/catsmoker/catsmoker-app/releases/download/11-2.0.5/app-full-release.apk",
                digest = "sha256:" + "c".repeat(64),
                size = 5968767L
            ),
            apkAsset(
                name = "app-full-x86_64-release.apk",
                url = "https://github.com/catsmoker/catsmoker-app/releases/download/11-2.0.5/app-full-x86_64-release.apk",
                digest = "sha256:" + "d".repeat(64),
                size = 5820619L
            )
        ).joinToString(",")
    )

    private fun arrayOf(vararg releases: String): JSONArray =
        JSONArray("[${releases.joinToString(",")}]")

    @Test
    fun tagWithCodePrefixParses() {
        assertEquals(
            SelfUpdater.TagVersion(code = 9, name = "2.0.3"),
            SelfUpdater.parseTagVersion("9-2.0.3")
        )
    }

    @Test
    fun legacyTagWithoutCodeFallsBackToNameOnly() {
        assertEquals(
            SelfUpdater.TagVersion(code = null, name = "v2.0.3"),
            SelfUpdater.parseTagVersion("v2.0.3")
        )
    }

    @Test
    fun selectsNewerStableWithDigest() {
        val info = SelfUpdater.selectUpdate(
            arrayOf(release("9-2.0.3", assets = apkAsset())),
            currentCode = 8, currentName = "2.0.2", wantPreRelease = false
        )
        assertTrue(info != null)
        assertEquals("9-2.0.3", info!!.tagName)
        assertTrue(info.downloadUrl!!.endsWith("app-full-release.apk"))
        assertEquals(
            "6dacd0be509334a0ffa7457f6b08a40e30c9f1df7b1dee78b1424eaf4f70ace1",
            info.sha256Hex
        )
        assertEquals(5469625L, info.sizeBytes)
        assertEquals("notes", info.notes)
    }

    @Test
    fun installedLatestIsUpToDate() {
        assertNull(
            SelfUpdater.selectUpdate(
                arrayOf(release("9-2.0.3", assets = apkAsset())),
                currentCode = 9, currentName = "2.0.3", wantPreRelease = false
            )
        )
    }

    @Test
    fun neverDowngrades() {
        assertNull(
            SelfUpdater.selectUpdate(
                arrayOf(release("7-2.0.1", assets = apkAsset())),
                currentCode = 9, currentName = "2.0.3", wantPreRelease = false
            )
        )
    }

    @Test
    fun skipsPrereleasesUnlessWanted() {
        val releases = arrayOf(
            release("10-2.1.0-beta", prerelease = true, assets = apkAsset()),
            release("9-2.0.3", assets = apkAsset())
        )
        assertEquals(
            "9-2.0.3",
            SelfUpdater.selectUpdate(releases, 8, "2.0.2", wantPreRelease = false)!!.tagName
        )
        assertEquals(
            "10-2.1.0-beta",
            SelfUpdater.selectUpdate(releases, 8, "2.0.2", wantPreRelease = true)!!.tagName
        )
    }

    @Test
    fun allPrereleasesMeansNoStable() {
        // Regression: the old code fell through to releases[0], offering a
        // prerelease as "stable". No stable candidate means no update.
        assertNull(
            SelfUpdater.selectUpdate(
                arrayOf(release("10-2.1.0-beta", prerelease = true, assets = apkAsset())),
                currentCode = 8, currentName = "2.0.2", wantPreRelease = false
            )
        )
    }

    @Test
    fun skipsDrafts() {
        assertNull(
            SelfUpdater.selectUpdate(
                arrayOf(release("10-2.1.0", draft = true, assets = apkAsset())),
                currentCode = 8, currentName = "2.0.2", wantPreRelease = false
            )
        )
    }

    @Test
    fun releaseWithoutApkHasNullUrl() {
        val info = SelfUpdater.selectUpdate(
            arrayOf(release("9-2.0.3")),
            currentCode = 8, currentName = "2.0.2", wantPreRelease = false
        )
        assertTrue(info != null)
        assertNull(info!!.downloadUrl)
    }

    @Test
    fun installedPendingTagCountsAsUpdated() {
        assertTrue(SelfUpdater.isTagInstalled("9-2.0.3", currentCode = 9, currentName = "2.0.3"))
        assertTrue(SelfUpdater.isTagInstalled("9-2.0.3", currentCode = 10, currentName = "2.0.4"))
        assertFalse(SelfUpdater.isTagInstalled("10-2.1.0", currentCode = 9, currentName = "2.0.3"))
    }

    @Test
    fun picksUniversalApkOverAbiSplits() {
        // Regression: "the first .apk" was the arm64-v8a split, so a 32-bit or x86
        // device downloaded a package it could not install.
        val info = SelfUpdater.selectUpdate(
            arrayOf(releaseWithSplits()),
            currentCode = 10, currentName = "2.0.4", wantPreRelease = true
        )!!
        assertEquals("11-2.0.5", info.tagName)
        assertTrue(info.downloadUrl!!.endsWith("/app-full-release.apk"))
        assertEquals("c".repeat(64), info.sha256Hex)
        assertEquals(5968767L, info.sizeBytes)
    }

    @Test
    fun splitOnlyListingStillOffersAnApk() {
        val onlySplit = apkAsset(
            name = "app-full-arm64-v8a-release.apk",
            url = "https://github.com/catsmoker/catsmoker-app/releases/download/9-2.0.3/app-full-arm64-v8a-release.apk"
        )
        val info = SelfUpdater.selectUpdate(
            arrayOf(release("9-2.0.3", assets = onlySplit)),
            currentCode = 8, currentName = "2.0.2", wantPreRelease = false
        )!!
        assertTrue(info.downloadUrl!!.endsWith("app-full-arm64-v8a-release.apk"))
    }

    @Test
    fun legacyNameCompareWithoutCodePrefix() {
        // Tags without a numeric prefix compare by semver name only.
        assertTrue(
            SelfUpdater.selectUpdate(
                arrayOf(release("v2.0.4", assets = apkAsset())),
                currentCode = null, currentName = "v2.0.3", wantPreRelease = false
            ) != null
        )
        assertNull(
            SelfUpdater.selectUpdate(
                arrayOf(release("v2.0.3", assets = apkAsset())),
                currentCode = null, currentName = "v2.0.3", wantPreRelease = false
            )
        )
    }
}
