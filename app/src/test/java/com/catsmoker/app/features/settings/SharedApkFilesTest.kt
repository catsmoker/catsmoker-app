package com.catsmoker.app.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pins the cache rules behind the Settings "Share App" card: the file the receiver sees is
 * `CatSmoker-<versionName>.apk`, and a new share replaces the previous copy instead of
 * stacking one full APK per version in the cache.
 */
class SharedApkFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun namesTheSharedFileAfterTheVersion() {
        assertEquals("CatSmoker-2.0.5.apk", SharedApkFiles.fileName("2.0.5"))
    }

    @Test
    fun stripsCharactersShareTargetsRejectFromTheVersion() {
        // Build tags can carry spaces or slashes; neither belongs in an attachment name.
        assertEquals("CatSmoker-2.0.5-beta-1.apk", SharedApkFiles.fileName("2.0.5 beta/1"))
    }

    @Test
    fun fallsBackToAStableNameForAnEmptyVersion() {
        assertEquals("CatSmoker-unknown.apk", SharedApkFiles.fileName("  "))
    }

    @Test
    fun preparesAFolderThatDoesNotExistYet() {
        val dir = File(folder.root, SharedApkFiles.DIR)

        val prepared = SharedApkFiles.prepare(dir)

        assertEquals(dir, prepared)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun deletesThePreviousApkBeforeANewCopy() {
        val dir = File(folder.root, SharedApkFiles.DIR).apply { mkdirs() }
        val stale = File(dir, "CatSmoker-1.9.apk")
        assertTrue(stale.createNewFile())

        SharedApkFiles.prepare(dir)

        assertTrue(!stale.exists())
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }
}
