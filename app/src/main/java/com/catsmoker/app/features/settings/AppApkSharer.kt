package com.catsmoker.app.features.settings

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.catsmoker.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cache-side naming and pruning for the APK the Settings "Share App" card hands to the
 * share sheet.
 *
 * Kept apart from [AppApkSharer] so the naming and pruning rules stay plain `java.io` and
 * can be unit-tested without an Android runtime.
 */
internal object SharedApkFiles {

    /**
     * Cache subdirectory behind the FileProvider `cache-path` named `shared_apk`
     * (`res/xml/file_paths.xml`). Nothing outside this folder is exposed by that entry.
     */
    const val DIR = "shared_apk"

    /**
     * `CatSmoker-<versionName>.apk` — the name the receiving app sees.
     *
     * The installed `versionName` comes from Gradle and is not a file name: path separators
     * or spaces in a build tag can be rejected (or silently rewritten) by share targets, so
     * every character outside `[A-Za-z0-9._-]` becomes `-`.
     */
    fun fileName(versionName: String): String {
        val safe = versionName.trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .ifEmpty { "unknown" }
        return "CatSmoker-$safe.apk"
    }

    /**
     * Empties the folder and returns it, so exactly one shared APK is ever cached.
     *
     * The previous copy is deleted before the new one is written: keeping one file per
     * version would grow the cache by a full APK on every update the user shared from.
     */
    fun prepare(cacheDir: File): File {
        cacheDir.listFiles()?.forEach { runCatching { it.delete() } }
        cacheDir.mkdirs()
        return cacheDir
    }
}

/** What an "Share App" attempt did, so the screen reports exactly one message. */
internal sealed interface ApkShareOutcome {

    /**
     * The chooser was opened. [splitApks] is true when the installed build is split (an App
     * Bundle or per-ABI install): the shared file is the base APK only, so it may not
     * install on its own — the caller says so instead of letting the receiver find out.
     */
    data class Shared(val splitApks: Boolean) : ApkShareOutcome

    /** Copy, FileProvider lookup or `startActivity` failed; nothing was shared. */
    data object Failed : ApkShareOutcome
}

/**
 * Shares the installed APK as a file through the share sheet.
 *
 * The card used to send a Play Store sentence; that is useless on the Full build (which is
 * not on Play) and cannot install anything. The APK is copied out of
 * `applicationInfo.sourceDir` into `cacheDir/shared_apk`, exposed as a `content://` URI by
 * the existing `FileProvider`, and offered with `ACTION_SEND` — so Bluetooth, Nearby Share,
 * messaging apps and file managers all receive a real installer without any storage
 * permission.
 *
 * Split installs share the base APK only, deliberately: `ACTION_SEND` carries one stream,
 * and `ACTION_SEND_MULTIPLE` with several APKs is badly supported by messaging targets
 * (most attach only the first file). A single base APK at least carries the signature and
 * the manifest of this exact build; the callers tells the user it may need the splits
 * beside it to install.
 */
internal object AppApkSharer {

    private const val TAG = "AppApkSharer"
    private const val MIME_APK = "application/vnd.android.package-archive"

    /** `<package>.fileprovider`, the authority declared in AndroidManifest.xml. */
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    /**
     * Copies the base APK and opens the chooser. The copy runs on [Dispatchers.IO]; the
     * activity start is posted back to the main thread, which is where `startActivity` and
     * the share sheet belong.
     *
     * Returns [ApkShareOutcome.Failed] instead of throwing for every failure the user can
     * hit (unreadable install path, full cache, no share targets), so the card can report it
     * and stay usable.
     */
    suspend fun share(context: Context, chooserTitle: String): ApkShareOutcome {
        val (file, splitApks) = withContext(Dispatchers.IO) {
            copyBaseApk(context)
        } ?: return ApkShareOutcome.Failed

        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", file)
        }.getOrNull() ?: return ApkShareOutcome.Failed

        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_APK
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return withContext(Dispatchers.Main) {
            runCatching {
                context.startActivity(
                    Intent.createChooser(send, chooserTitle).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }.fold(
                onSuccess = { ApkShareOutcome.Shared(splitApks) },
                onFailure = { e ->
                    Log.w(TAG, "No app to share the APK with", e)
                    ApkShareOutcome.Failed
                }
            )
        }
    }

    /**
     * Copies `sourceDir` into the FileProvider-served cache folder.
     *
     * Returns the copied file plus whether this install is split, or null on any failure so
     * the caller reports one message. A partial copy is deleted, so a failed attempt never
     * leaves a truncated APK behind for the next tap to hand out.
     */
    private fun copyBaseApk(context: Context): Pair<File, Boolean>? {
        val info = context.applicationInfo ?: return null
        val source = info.sourceDir?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        if (!source.isFile) return null

        var target: File? = null
        return try {
            val dir = SharedApkFiles.prepare(File(context.cacheDir, SharedApkFiles.DIR))
            val file = File(dir, SharedApkFiles.fileName(BuildConfig.VERSION_NAME))
            target = file
            source.inputStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file to !info.splitSourceDirs.isNullOrEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy the installed APK to cache", e)
            runCatching { target?.delete() }
            null
        }
    }
}
