package com.catsmoker.app.screens.gaming.crosshair

import android.content.Context
import com.catsmoker.app.screens.gaming.crosshair.CrosshairAssets
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.CRC32
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CrosshairCatalogEntry(
    val filename: String,
    val number: Int,
    val downloadUrl: String
)

internal interface CrosshairRemote {
    suspend fun fetchCatalog(): String
    suspend fun fetchImage(filename: String): ByteArray
}

internal object CrosshairCatalog {
    private const val RAW_BASE =
        "https://raw.githubusercontent.com/catsmoker/catsmoker-app/main/crosshairs/"
    private val filenamePattern = Regex("""scope([1-9]\d*)\.png""")

    val catalogUrl: String = "${RAW_BASE}catalog.json"

    fun parse(json: String): List<CrosshairCatalogEntry> {
        val files = JSONObject(json).getJSONArray("files")
        val entries = ArrayList<CrosshairCatalogEntry>(files.length())
        val seen = HashSet<String>(files.length())
        for (index in 0 until files.length()) {
            val filename = files.getString(index)
            require(isValidFilename(filename)) { "Invalid crosshair filename in catalog: $filename" }
            require(seen.add(filename)) { "Duplicate crosshair filename in catalog: $filename" }
            val match = filenamePattern.matchEntire(filename)
                ?: throw IllegalArgumentException("Unsupported crosshair filename in catalog: $filename")
            val number = match.groupValues.getOrNull(1)?.toIntOrNull()
                ?: throw IllegalArgumentException("Crosshair filename missing numeric suffix: $filename")
            entries += CrosshairCatalogEntry(filename, number, downloadUrl(filename))
        }
        return entries.sortedBy { it.number }
    }

    fun isValidFilename(filename: String): Boolean = filenamePattern.matches(filename)

    private fun downloadUrl(filename: String): String {
        require(isValidFilename(filename)) { "Invalid crosshair filename" }
        return "$RAW_BASE$filename"
    }
}

class CrosshairLibraryRepository internal constructor(
    private val directory: File,
    private val remote: CrosshairRemote
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        File(context.filesDir, "crosshairs"),
        GitHubCrosshairRemote()
    )

    suspend fun catalog(): List<CrosshairCatalogEntry> = withContext(Dispatchers.IO) {
        CrosshairCatalog.parse(remote.fetchCatalog())
            .filterNot { it.filename == CrosshairAssets.DEFAULT_ASSET }
    }

    suspend fun download(filename: String): File = withContext(Dispatchers.IO) {
        require(CrosshairCatalog.isValidFilename(filename) && filename != CrosshairAssets.DEFAULT_ASSET) {
            "Unsupported crosshair filename"
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create crosshair storage")
        }
        val target = File(directory, CrosshairAssets.downloadedAssetName(filename))
        if (target.isFile && PngValidator.isValid(target.readBytes())) return@withContext target

        val bytes = remote.fetchImage(filename)
        if (!PngValidator.isValid(bytes)) throw IOException("Downloaded crosshair is not a valid PNG")
        if (target.exists() && !target.delete()) {
            throw IOException("Could not replace invalid crosshair file")
        }

        val temporary = File.createTempFile("crosshair-", ".png", directory)
        try {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) throw IOException("Could not save downloaded crosshair")
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                throw IOException("Could not remove temporary crosshair file")
            }
        }
        target
    }

    fun downloadedAssets(): Set<String> {
        if (!directory.isDirectory) return emptySet()
        return directory.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && CrosshairAssets.isDownloadedAsset(it.name) }
            .filter { PngValidator.isValid(it.readBytes()) }
            .map { it.name.lowercase() }
            .toSet()
    }
}

private class GitHubCrosshairRemote : CrosshairRemote {
    override suspend fun fetchCatalog(): String = withContext(Dispatchers.IO) {
        readUrl(CrosshairCatalog.catalogUrl, MAX_CATALOG_BYTES).toString(Charsets.UTF_8)
    }

    override suspend fun fetchImage(filename: String): ByteArray = withContext(Dispatchers.IO) {
        val entry = CrosshairCatalog.parse("""{"files":["$filename"]}""").single()
        readUrl(entry.downloadUrl, MAX_IMAGE_BYTES)
    }

    private fun readUrl(address: String, maxBytes: Int): ByteArray {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "CatSmoker")
        connection.setRequestProperty("Accept", "application/octet-stream")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Crosshair server returned HTTP ${connection.responseCode}")
            }
            if (connection.contentLengthLong > maxBytes) throw IOException("Crosshair response is too large")
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maxBytes) throw IOException("Crosshair response is too large")
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                if (bytes.size > maxBytes) throw IOException("Crosshair response is too large")
                bytes
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
        const val MAX_CATALOG_BYTES = 64 * 1024
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    }
}

private object PngValidator {
    private val signature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    )
    private const val MAX_DIMENSION = 1024

    fun isValid(bytes: ByteArray): Boolean {
        if (bytes.size < 45 || bytes.size > 2 * 1024 * 1024) return false
        if (!bytes.copyOfRange(0, signature.size).contentEquals(signature)) return false

        var offset = signature.size
        var hasHeader = false
        var hasData = false
        while (offset + 12 <= bytes.size) {
            val length = readInt(bytes, offset)
            if (length < 0 || length > bytes.size - offset - 12) return false
            val typeOffset = offset + 4
            val dataOffset = offset + 8
            val crcOffset = dataOffset + length
            val crc = CRC32().apply { update(bytes, typeOffset, length + 4) }.value
            if (crc != readUnsignedInt(bytes, crcOffset)) return false

            when {
                bytes.matchesType(typeOffset, "IHDR") -> {
                    if (hasHeader || length != 13) return false
                    val width = readInt(bytes, dataOffset)
                    val height = readInt(bytes, dataOffset + 4)
                    if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION) return false
                    hasHeader = true
                }
                bytes.matchesType(typeOffset, "IDAT") -> hasData = true
                bytes.matchesType(typeOffset, "IEND") ->
                    return length == 0 && hasHeader && hasData && crcOffset + 4 == bytes.size
            }
            offset = crcOffset + 4
        }
        return false
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun readUnsignedInt(bytes: ByteArray, offset: Int): Long =
        readInt(bytes, offset).toLong() and 0xffffffffL

    private fun ByteArray.matchesType(offset: Int, type: String): Boolean =
        this[offset] == type[0].code.toByte() &&
            this[offset + 1] == type[1].code.toByte() &&
            this[offset + 2] == type[2].code.toByte() &&
            this[offset + 3] == type[3].code.toByte()
}
