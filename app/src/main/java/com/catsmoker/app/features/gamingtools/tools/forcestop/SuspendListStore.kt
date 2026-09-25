package com.catsmoker.app.features.gamingtools.tools.forcestop

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's own extra suspend list: packages frozen at Gaming Mode activation on top of the
 * automatic sweep, and woken with everything else on deactivation.
 *
 * The sweep already covers ordinary background apps; this list is for the ones it deliberately
 * protects or cannot see that the user wants frozen anyway (a chatty vendor service, a
 * downloader). Entries join the same `affected_pkgs` record as swept packages, so the revert
 * path unsuspends them with no second bookkeeping — only actually-frozen packages are
 * recorded, by the same verified rule.
 *
 * Its own prefs file, like every feature store: the set here means "suspend these", which must
 * never be confused with Auto Force Stop's keep-alive set in `AutoForceStopPrefs`.
 */
@Singleton
class SuspendListStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The packages the user picked. Empty is valid and means "just the automatic sweep". */
    fun getSuspendPackages(): Set<String> =
        prefs.getStringSet(KEY_SUSPEND, emptySet()) ?: emptySet()

    /** Adds [packageName], or removes it when already picked. Returns the list afterwards. */
    fun toggleSuspendPackage(packageName: String): Set<String> {
        val current = getSuspendPackages().toMutableSet()
        if (!current.add(packageName)) current.remove(packageName)
        prefs.edit { putStringSet(KEY_SUSPEND, current) }
        return current
    }

    fun removeSuspendPackage(packageName: String): Set<String> {
        val current = getSuspendPackages().toMutableSet()
        current.remove(packageName)
        prefs.edit { putStringSet(KEY_SUSPEND, current) }
        return current
    }

    companion object {
        /**
         * Usable targets from the stored list: blanks and duplicates dropped, sorted for a
         * stable activation order — and never Catsmoker itself or the active game, however
         * picked. Freezing this process would strand every frozen package with nothing left
         * to wake them; freezing the game would end the session the mode was started for.
         */
        fun filterTargets(
            candidates: Set<String>,
            activeGamePkg: String?,
            selfPkg: String
        ): List<String> = candidates
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != selfPkg && it != activeGamePkg }
            .distinct()
            .sorted()

        private const val PREFS_NAME = "SuspendListPrefs"
        private const val KEY_SUSPEND = "suspend_packages"
    }
}
