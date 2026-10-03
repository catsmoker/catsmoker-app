package com.catsmoker.app.features.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full-variant GitHub-release self-updater.
 *
 * Same FQN as the playstore variant's unsupported stub: [SettingsViewModel]
 * delegates here so the APK-download implementation is not compiled into the
 * Play Store variant at all (Play policy forbids self-updating).
 *
 * Network work runs on the caller's thread — call from Dispatchers.IO.
 */
object SelfUpdater {
    const val SUPPORTED = true

    data class UpdateInfo(val tagName: String, val downloadUrl: String?)

    sealed interface CheckResult {
        data object NoReleases : CheckResult
        data object UpToDate : CheckResult
        data class Available(val info: UpdateInfo) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    suspend fun checkForUpdate(isPreRelease: Boolean, currentVersion: String): CheckResult {
        return try {
            val releases = fetchReleases()
            if (releases.length() == 0) return CheckResult.NoReleases
            val latest = findLatestRelease(releases, isPreRelease) ?: return CheckResult.NoReleases
            val info = processReleaseData(latest, currentVersion)
            if (info != null) CheckResult.Available(info) else CheckResult.UpToDate
        } catch (e: Exception) {
            CheckResult.Failed(e.message ?: "?")
        }
    }

    /**
     * Downloads the APK to external files dir, reporting 0..1 progress.
     * Returns the file, or null when no URL was available. Throws on I/O failure.
     */
    suspend fun downloadUpdate(
        context: Context,
        url: String?,
        onProgress: (Float) -> Unit
    ): File? {
        if (url == null) return null
        val destination = File(context.getExternalFilesDir(null), "update.apk")
        val u = URL(url)
        val conn = u.openConnection() as HttpURLConnection
        conn.connect()
        val fileLength = conn.contentLength
        u.openStream().use { input ->
            FileOutputStream(destination).use { output ->
                val data = ByteArray(4096)
                var total = 0L
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        onProgress(total.toFloat() / fileLength)
                    }
                    output.write(data, 0, count)
                }
            }
        }
        return destination
    }

    fun installUpdate(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun findLatestRelease(releases: JSONArray, isPreRelease: Boolean): JSONObject? {
        if (releases.length() <= 0) return null
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (isPreRelease || !release.getBoolean("prerelease")) return release
        }
        return releases.getJSONObject(0)
    }

    private fun fetchReleases(): JSONArray {
        val url = URL("https://api.github.com/repos/catsmoker/com.catsmoker.app/releases")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 5000
            conn.setRequestProperty("User-Agent", "Catsmoker-App")
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            JSONArray(response)
        } finally {
            conn.disconnect()
        }
    }

    private fun processReleaseData(latestRelease: JSONObject, currentVersion: String): UpdateInfo? {
        try {
            val tagName = latestRelease.getString("tag_name")
            val githubVersion = tagName.removePrefix("v")
            if (isUpdateAvailable(githubVersion, currentVersion)) {
                val assets = latestRelease.getJSONArray("assets")
                var downloadUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                return UpdateInfo(tagName, downloadUrl)
            }
        } catch (_: Exception) {}
        return null
    }

    private fun isUpdateAvailable(githubVersion: String, currentVersion: String): Boolean {
        try {
            val v1 = githubVersion.split(".").map { it.toInt() }
            val v2 = currentVersion.split(".").map { it.toInt() }
            for (i in 0 until max(v1.size, v2.size)) {
                val n1 = v1.getOrElse(i) { 0 }
                val n2 = v2.getOrElse(i) { 0 }
                if (n1 > n2) return true
                if (n1 < n2) return false
            }
        } catch (_: Exception) {}
        return false
    }
}
