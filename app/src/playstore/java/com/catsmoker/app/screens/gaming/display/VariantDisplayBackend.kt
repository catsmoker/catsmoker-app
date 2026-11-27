package com.catsmoker.app.screens.gaming.display

import android.content.Context
import com.catsmoker.app.platform.shell.ShellRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Google Play distribution's privileged display backend — deliberately empty.
 *
 * The Full variant drives displays through hidden SurfaceFlinger interfaces from a
 * `shell`-UID child process. That implementation, its `app_process` entry point and the
 * pacesetter/discovery code are not compiled into this variant at all: they are not in Play's
 * source inputs, not just disabled at runtime.
 *
 * What Play keeps is the part that needs no hidden interface and was already shipped: the AOSP
 * `min_refresh_rate`/`peak_refresh_rate` rows and the vendor settings keys, which is
 * [com.catsmoker.app.screens.gaming.mode.RefreshRateController]. This class therefore answers
 * every request with the same refusal, and the engine falls back to those settings instead of
 * offering a control that cannot move.
 */
@Singleton
class VariantDisplayBackend @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRunner: ShellRunner
) {

    /** This distribution does not compile the hidden-interface backend. */
    val usesHiddenInterfaces: Boolean = false

    private val refusal = "This build does not include the hidden display interfaces"

    /** Always "no privileged backend here"; the reason is the same for every device. */
    suspend fun probe(force: Boolean = false): DisplayProbe =
        DisplayProbe(shellProcess = false, reason = refusal)

    suspend fun applyModeSpecs(
        displayId: Long,
        modeId: Int,
        minHz: Float,
        maxHz: Float
    ): BackendOutcome = BackendOutcome.refused(refusal)

    suspend fun setPacesetter(displayId: Long?): BackendOutcome = BackendOutcome.refused(refusal)

    suspend fun setDeXHighResolution(enabled: Boolean): BackendOutcome = BackendOutcome.refused(refusal)

    /** Nothing is cached on this variant; the answer never changes. */
    fun invalidate() = Unit
}
