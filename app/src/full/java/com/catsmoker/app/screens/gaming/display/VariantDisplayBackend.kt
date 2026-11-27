package com.catsmoker.app.screens.gaming.display

import android.content.Context
import com.catsmoker.app.platform.shell.ShellRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The GitHub-distribution privileged display backend.
 *
 * Every hidden interface is reached through a `shell`-UID child process, because that identity
 * holds `ACCESS_SURFACE_FLINGER` while the app UID cannot (`SurfaceComposerAIDL` gates mode
 * changes on it, and it is `signature` level). The child is [DisplayShellMain], started with
 * `app_process` over the installed APK's classpath so its classes sit in the boot domain and
 * the hidden APIs are reachable without any exemption trick.
 *
 * Nothing here is attempted when the prerequisite is missing. A device without root or Shizuku
 * gets `shellProcess = false` and one reason; a Samsung without the pacesetter symbols gets
 * `pacesetter = false`; an ordinary phone gets both false and keeps the settings backend. The
 * probe runs the child once and caches the answer, because it is a property of the firmware.
 */
@Singleton
class VariantDisplayBackend @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRunner: ShellRunner
) {

    @Volatile
    private var cachedProbe: DisplayProbe? = null

    /** Whether this distribution compiles the hidden-interface backend at all. */
    val usesHiddenInterfaces: Boolean = true

    /**
     * Runs the privileged probe.
     *
     * @param force re-run instead of returning the cached firmware answer — used after the user
     *   grants root or Shizuku, when only the *prerequisite* changed, not the firmware.
     */
    suspend fun probe(force: Boolean = false): DisplayProbe = withContext(Dispatchers.IO) {
        if (!force) cachedProbe?.let { return@withContext it }
        if (!shellRunner.hasPrivilege()) {
            return@withContext DisplayProbe(reason = "Root or Shizuku is required for the privileged backends")
                .also { cachedProbe = it }
        }
        val reply = run("probe")
            ?: return@withContext DisplayProbe(
                shellProcess = false,
                reason = "The privileged display helper did not return a report"
            ).also { cachedProbe = it }

        val probe = DisplayProbe(
            shellProcess = reply.optString("result") == "ok",
            modeSpecs = reply.optBoolean("modeSpecs", false),
            pacesetter = reply.optBoolean("pacesetter", false),
            dexHighResolution = reply.optBoolean("dexHighRes", false),
            pacesetterReadback = reply.optBoolean("schedulerDump", false),
            qtiDisplayConfig = reply.optBoolean("qtiDisplayConfig", false),
            uid = reply.optInt("uid", -1).takeIf { it > 0 },
            reason = reply.optString("reason").takeIf { it.isNotBlank() }
        )
        cachedProbe = probe
        probe
    }

    /**
     * Pins one display to a mode and a rate range through the composer's policy.
     *
     * @param displayId a *physical* display id from the composer's own list, not a
     *   `DisplayManager` display id — they are different namespaces and mixing them addresses
     *   the wrong panel.
     */
    suspend fun applyModeSpecs(
        displayId: Long,
        modeId: Int,
        minHz: Float,
        maxHz: Float
    ): BackendOutcome = withContext(Dispatchers.IO) {
        val reply = run("mode-specs", displayId.toString(), modeId.toString(), minHz.toString(), maxHz.toString())
            ?: return@withContext BackendOutcome.refused("The privileged display helper is unavailable")
        reply.toOutcome()
    }

    /**
     * Makes [displayId] the display whose vsync applications render against, or clears the
     * override when [displayId] is null.
     *
     * This is what makes a monitor's rate the *application's* rate in DeX: apps pace off the
     * pacesetter display, so choosing the monitor is the difference between "the monitor runs
     * at 120 Hz" and "the app renders at 120 Hz".
     */
    suspend fun setPacesetter(displayId: Long?): BackendOutcome = withContext(Dispatchers.IO) {
        val reply = if (displayId == null) run("pacesetter", "reset")
        else run("pacesetter", "force", displayId.toString())
        reply?.toOutcome() ?: BackendOutcome.refused("The privileged display helper is unavailable")
    }

    /** Lifts or reinstates Samsung DeX's external-desktop timing filter. */
    suspend fun setDeXHighResolution(enabled: Boolean): BackendOutcome = withContext(Dispatchers.IO) {
        val reply = run("dex-highres", if (enabled) "1" else "0")
            ?: return@withContext BackendOutcome.refused("The privileged display helper is unavailable")
        reply.toOutcome()
    }

    /** Drops the cached firmware answer, e.g. after a Shizuku restart. */
    fun invalidate() {
        cachedProbe = null
    }

    // ------------------------------------------------------------------ plumbing

    /**
     * Runs one command in the `shell`-UID child and parses its single JSON line.
     *
     * The classpath is the installed APK: `app_process` then loads this app's classes with the
     * system loader, which is the boot domain, where the hidden SurfaceFlinger APIs are not
     * blocked. Quoting is minimal — the only interpolated value is the APK path, which comes
     * from `ApplicationInfo` rather than from anything the user types.
     */
    private suspend fun run(vararg command: String): JSONObject? {
        if (!shellRunner.hasPrivilege()) return null
        val apk = runCatching { context.applicationInfo.sourceDir }.getOrNull() ?: return null
        val line = buildString {
            append("CLASSPATH='").append(apk).append("' ")
            append("/system/bin/app_process /system/bin ")
            append(ENTRY_POINT)
            command.forEach { append(' ').append(it) }
        }
        val result = runCatching { shellRunner.execResult(line) }.getOrNull() ?: return null
        if (!result.isSuccess) return null
        val json = result.stdout.lineSequence().lastOrNull { it.trimStart().startsWith("{") } ?: return null
        return runCatching { JSONObject(json) }.getOrNull()
    }

    private fun JSONObject.toOutcome(): BackendOutcome {
        val ok = optString("result") == "ok"
        val readBack = when {
            has("readBack") -> optString("readBack")
            has("forcedPacesetter") -> optString("forcedPacesetter")
            has("enabled") -> optBoolean("enabled").toString()
            else -> null
        }
        return if (ok) {
            BackendOutcome(ok = true, detail = if (optBoolean("verified", true)) null else "not verified", readBack = readBack)
        } else {
            BackendOutcome.refused(optString("reason").ifBlank { "The device refused the request" })
        }
    }

    private companion object {
        /**
         * The `app_process` entry point. Kept as a literal rather than a class reference so
         * the release build's shrinking cannot rename it out from under the child process —
         * `proguard-rules.pro` keeps the same name.
         */
        const val ENTRY_POINT = "com.catsmoker.app.screens.gaming.display.DisplayShellMain"
    }
}
