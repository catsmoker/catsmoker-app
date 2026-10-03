package com.catsmoker.app.features.settings

import android.content.Context
import java.io.File

/**
 * Play Store variant: no self-updater (Play policy forbids downloading APKs
 * outside Play; updates come from the Play Store itself).
 *
 * Same FQN as the full variant's [SelfUpdater] so [SettingsViewModel] links
 * without compiling the GitHub-release implementation into this variant.
 */
object SelfUpdater {
    const val SUPPORTED = false

    data class UpdateInfo(val tagName: String, val downloadUrl: String?)

    sealed interface CheckResult {
        data object NoReleases : CheckResult
        data object UpToDate : CheckResult
        data class Available(val info: UpdateInfo) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    suspend fun checkForUpdate(isPreRelease: Boolean, currentVersion: String): CheckResult =
        CheckResult.UpToDate

    suspend fun downloadUpdate(
        context: Context,
        url: String?,
        onProgress: (Float) -> Unit
    ): File? = null

    fun installUpdate(context: Context, file: File) {
        // No-op: the Play Store owns updates on this variant.
    }
}
