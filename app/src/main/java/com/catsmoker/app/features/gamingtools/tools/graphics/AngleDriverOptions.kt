package com.catsmoker.app.features.gamingtools.tools.graphics

import android.content.Context
import android.provider.Settings
import com.catsmoker.app.system.shell.ShellRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-game graphics driver choice (ANGLE vs the native GLES driver).
 *
 * Android 10+ lets each package pick its GLES driver through two global keys,
 * `angle_gl_driver_selection_pkgs` and `angle_gl_driver_selection_values` — comma-separated
 * lists zipped by position, the same table Developer Options' ANGLE Preferences screen edits,
 * and readable in the ANGLE project's own `src/android_system_settings/README.md`. The choices
 * persist across reboots, so this is an immediate control (like the Developer Options screen),
 * not a Gaming Mode session setting: applied now, kept until changed or reset.
 *
 * Reads go through the settings provider and need no privilege; writes go through the shell
 * and need root or Shizuku. Every write is confirmed by reading both lists back — merging into
 * device truth shared with Developer Options and other tools, never overwriting it.
 */
@Singleton
class AngleDriverOptions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRunner: ShellRunner
) {
    /** The three driver values the platform accepts. */
    enum class Driver(val value: String) {
        ANGLE("angle"),
        NATIVE("native"),
        DEFAULT("default");

        companion object {
            fun fromValue(value: String): Driver? = entries.firstOrNull { it.value == value }
        }
    }

    /**
     * Whether a driver choice landed.
     *
     * @param applied the read-back's verdict, never the command's exit code alone.
     * @param refusal the device's own words when it refused, or null when nothing was refused.
     */
    data class Outcome(val applied: Boolean, val refusal: String?)

    /** The whole table as currently held, in device order. Unknown tokens pass through as-is. */
    suspend fun readPairs(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        parsePairs(readGlobal(KEY_PKGS), readGlobal(KEY_VALUES))
    }

    /** The driver currently chosen for [pkg], or null when the table holds none for it. */
    suspend fun driverFor(pkg: String): Driver? =
        readPairs().firstOrNull { it.first == pkg }?.second?.let(Driver::fromValue)

    /**
     * Chooses [driver] for [pkg] and confirms the table holds it afterwards.
     *
     * `DEFAULT` removes the entry (absent means platform-default) so the lists do not grow a
     * token per reset. Entries this app did not write — including unknown tokens — are carried
     * over untouched.
     */
    suspend fun setDriver(pkg: String, driver: Driver): Outcome = withContext(Dispatchers.IO) {
        if (!shellRunner.hasPrivilege()) {
            return@withContext Outcome(false, NOT_PRIVILEGED)
        }
        val updated = withDriver(readPairs(), pkg, driver.value)
        writePairs(updated)
    }

    private fun readGlobal(key: String): String? = try {
        Settings.Global.getString(context.contentResolver, key)
    } catch (_: Exception) {
        null
    }

    private suspend fun writePairs(pairs: List<Pair<String, String>>): Outcome {
        val (pkgs, values) = toCsv(pairs)
        val putPkgs = putOrDelete(KEY_PKGS, pkgs)
        if (!putPkgs.isSuccess) return Outcome(false, shellSpeech(putPkgs))
        val putValues = putOrDelete(KEY_VALUES, values)
        if (!putValues.isSuccess) return Outcome(false, shellSpeech(putValues))
        // The exit code only says `settings` ran. The table now held is the fact.
        return if (readPairs() == pairs) {
            Outcome(true, null)
        } else {
            Outcome(false, DID_NOT_STICK)
        }
    }

    private suspend fun putOrDelete(key: String, value: String) =
        if (value.isEmpty()) {
            // Empty is "no entries": removing the key is the unset, not a blank entry.
            shellRunner.execSafeResult("settings", "delete", "global", key)
        } else {
            shellRunner.execSafeResult("settings", "put", "global", key, value)
        }

    private fun shellSpeech(result: ShellRunner.ExecResult): String =
        listOf(result.stdout, result.stderr).filter { it.isNotBlank() }.joinToString("\n")
            .ifBlank { "settings exited ${result.exitCode} and said nothing" }

    companion object {
        /** Package list of the ANGLE driver table. */
        const val KEY_PKGS = "angle_gl_driver_selection_pkgs"

        /** Driver list of the ANGLE driver table, zipped with [KEY_PKGS] by position. */
        const val KEY_VALUES = "angle_gl_driver_selection_values"

        /**
         * Parses the two parallel lists into pairs, zipped by position.
         *
         * Blank, missing and literal-`"null"` (what `settings get` prints for unset) all mean
         * "no entries". Ragged lists pair up to the shorter one — pairing past the end would
         * invent entries — and unknown tokens are kept verbatim so a later write-back preserves
         * device truth instead of "cleaning" it.
         */
        fun parsePairs(pkgsCsv: String?, valuesCsv: String?): List<Pair<String, String>> {
            fun split(csv: String?): List<String> {
                val raw = csv?.trim().orEmpty()
                if (raw.isEmpty() || raw == "null") return emptyList()
                return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
            val pkgs = split(pkgsCsv)
            val values = split(valuesCsv)
            return pkgs.zip(values)
        }

        /**
         * Returns the table with [pkg] set to [value], replacing in place or appending.
         * `"default"` removes the entry — absent is the platform default — and every other
         * entry (including unknown tokens) is carried over untouched.
         */
        fun withDriver(
            pairs: List<Pair<String, String>>,
            pkg: String,
            value: String
        ): List<Pair<String, String>> {
            if (value == Driver.DEFAULT.value) return pairs.filter { it.first != pkg }
            if (pairs.none { it.first == pkg }) return pairs + (pkg to value)
            return pairs.map { if (it.first == pkg) pkg to value else it }
        }

        /** Serializes pairs back to the two parallel lists. */
        fun toCsv(pairs: List<Pair<String, String>>): Pair<String, String> =
            pairs.joinToString(",") { it.first } to pairs.joinToString(",") { it.second }

        private const val NOT_PRIVILEGED = "needs root/Shizuku"
        private const val DID_NOT_STICK = "settings accepted the write but the table did not hold it"
    }
}
