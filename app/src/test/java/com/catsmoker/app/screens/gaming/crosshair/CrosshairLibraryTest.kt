package com.catsmoker.app.screens.gaming.crosshair

import java.io.File
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrosshairLibraryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun catalogAcceptsOnlyUniqueSafePngNamesAndBuildsRepositoryUrls() {
        val entries = CrosshairCatalog.parse("""{"files":["scope1.png","scope100.png"]}""")

        assertEquals(listOf("scope1.png", "scope100.png"), entries.map { it.filename })
        assertEquals(
            "https://raw.githubusercontent.com/catsmoker/catsmoker-app/main/crosshairs/scope100.png",
            entries.last().downloadUrl
        )
    }

    @Test
    fun catalogRejectsPathTraversalAndDuplicateNames() {
        val unsafe = runCatching { CrosshairCatalog.parse("""{"files":["../secret.png"]}""") }
        val duplicate = runCatching { CrosshairCatalog.parse("""{"files":["scope1.png","scope1.png"]}""") }
        val malformed = runCatching { CrosshairCatalog.parse("""{"files":["scope-not-a-number.png"]}""") }

        assertTrue(unsafe.isFailure)
        assertTrue(duplicate.isFailure)
        assertTrue(malformed.isFailure)
    }

    @Test
    fun downloadStoresValidImageAndDoesNotFetchItTwice() = runBlocking {
        var fetchCount = 0
        val remote = fakeRemote { fetchCount++; validPng() }
        val repository = CrosshairLibraryRepository(File(temporaryFolder.root, "crosshairs"), remote)

        val first = repository.download("scope100.png")
        val second = repository.download("scope100.png")

        assertTrue(first.isFile)
        assertEquals(first, second)
        assertEquals(1, fetchCount)
        assertEquals(setOf("downloaded_scope100.png"), repository.downloadedAssets())
    }

    @Test
    fun invalidImageIsNotSavedOrReportedAsDownloaded() = runBlocking {
        val repository = CrosshairLibraryRepository(
            File(temporaryFolder.root, "crosshairs"),
            fakeRemote { byteArrayOf(1, 2, 3, 4) }
        )

        val result = runCatching { repository.download("scope100.png") }

        assertTrue(result.isFailure)
        assertTrue(repository.downloadedAssets().isEmpty())
        assertFalse(File(temporaryFolder.root, "crosshairs/downloaded_scope100.png").exists())
    }

    @Test
    fun failedDownloadDoesNotCreateDownloadedEntry() = runBlocking {
        val repository = CrosshairLibraryRepository(
            File(temporaryFolder.root, "crosshairs"),
            fakeRemote { throw IOException("network unavailable") }
        )

        val result = runCatching { repository.download("scope100.png") }

        assertTrue(result.isFailure)
        assertTrue(repository.downloadedAssets().isEmpty())
    }

    private fun fakeRemote(download: () -> ByteArray) = object : CrosshairRemote {
        override suspend fun fetchCatalog(): String = """{"files":["scope100.png"]}"""
        override suspend fun fetchImage(filename: String): ByteArray = download()
    }

    private fun validPng(): ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4////fwAJ+wP9KobjigAAAABJRU5ErkJggg=="
    )
}
