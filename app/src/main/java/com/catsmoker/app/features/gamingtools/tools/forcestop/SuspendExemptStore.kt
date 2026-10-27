package com.catsmoker.app.features.gamingtools.tools.forcestop

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The apps Gaming Mode's suspend sweep must leave running — the user's exception list.
 *
 * The sweep freezes every user app (plus the curated Google and OEM lists) while the mode is
 * active. That is the point of it, but it cannot know which of those apps the user still wants
 * alive: a chat, a music player, a video app. This store is the user's answer. Packages listed
 * here are subtracted from the sweep — and from the AppOps background restriction that rides it —
 * in `SuspendSweep.resolveTargets`, and are never recorded in `affected_pkgs` because they were
 * never frozen. Deactivation therefore has nothing to wake for them.
 *
 * Its own prefs file, like every feature store. The set here means "keep running", which must
 * never be confused with [SuspendListStore]'s "freeze these" (the two are kept mutually exclusive
 * by the UI, and the engine resolves a hand-edited overlap as kept) or Auto Force Stop's
 * keep-alive set, which answers a different question: what not to force-stop when the user
 * switches away. A kept app is not protected from either of those logs' own actions.
 */
@Singleton
class SuspendExemptStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The packages to keep running. Empty is valid and means "the sweep decides". */
    fun getExemptPackages(): Set<String> =
        prefs.getStringSet(KEY_EXEMPT, emptySet()) ?: emptySet()

    /** Adds [packageName], or removes it when already picked. Returns the set afterwards. */
    fun toggleExemptPackage(packageName: String): Set<String> {
        val next = toggled(getExemptPackages(), packageName)
        prefs.edit { putStringSet(KEY_EXEMPT, next) }
        return next
    }

    fun removeExemptPackage(packageName: String): Set<String> {
        val next = removed(getExemptPackages(), packageName)
        prefs.edit { putStringSet(KEY_EXEMPT, next) }
        return next
    }

    companion object {
        /**
         * The set transition behind [toggleExemptPackage], kept pure so the UI's state change can be
         * tested without a device: one pick flips, every other pick is untouched, and the caller's
         * set is never mutated.
         */
        internal fun toggled(current: Set<String>, packageName: String): Set<String> {
            val next = current.toMutableSet()
            if (!next.add(packageName)) next.remove(packageName)
            return next
        }

        /** The transition behind [removeExemptPackage]; removing an absent pick is a no-op. */
        internal fun removed(current: Set<String>, packageName: String): Set<String> =
            current - packageName

        private const val PREFS_NAME = "SuspendExemptPrefs"
        private const val KEY_EXEMPT = "exempt_packages"
    }
}
