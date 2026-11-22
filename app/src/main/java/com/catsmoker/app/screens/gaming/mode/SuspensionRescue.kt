package com.catsmoker.app.screens.gaming.mode

import androidx.annotation.StringRes
import com.catsmoker.app.platform.shell.ShellRunner.PrivilegedIdentity
import com.catsmoker.app.screens.gaming.apps.SuspendVerdict.PackageState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal sealed interface SuspensionRescueResult<out T> {
    data class Success<T>(val value: T) : SuspensionRescueResult<T>
    data class Blocked(@StringRes val reason: Int) : SuspensionRescueResult<Nothing>
}

/** Manual, explicitly selected recovery when CatSmoker's own suspension record is missing. */
internal object SuspensionRescue {
    private val packageName = Regex("""[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*""")
    private val supportedOwners = mapOf("root" to PrivilegedIdentity.ROOT, "com.android.shell" to PrivilegedIdentity.SHELL)

    fun candidates(states: Map<String, PackageState>, excluded: Set<String>): Map<String, List<PrivilegedIdentity>> =
        states.filter { (pkg, state) ->
            pkg !in excluded && packageName.matches(pkg) && state.installed && state.suspended &&
                !state.quarantined && state.owners.isNotEmpty() && state.owners.all { it in supportedOwners }
        }.mapValues { (_, state) -> state.owners.sorted().map(supportedOwners::getValue) }

    /**
     * Use fresh owners on confirmation, not the picker snapshot. Don't invent an original app-op
     * or touch Android policy owners. A successful exit alone isn't proof an app can open again.
     */
    suspend fun restore(
        selected: Set<String>,
        fresh: Map<String, PackageState>,
        excluded: Set<String>,
        canContinue: () -> Boolean,
        release: suspend (String, PrivilegedIdentity) -> Boolean?,
        readBack: suspend () -> Map<String, PackageState>
    ): Set<String> {
        val candidates = candidates(fresh, excluded)
        fun isAvailable(state: PackageState?) =
            state != null && state.installed && !state.suspended && !state.quarantined
        val available = mutableSetOf<String>()
        val attempted = mutableSetOf<String>()
        for (pkg in selected.sorted()) {
            if (pkg in excluded) continue
            if (isAvailable(fresh[pkg])) {
                available += pkg
                continue
            }
            for (identity in candidates[pkg].orEmpty()) {
                currentCoroutineContext().ensureActive()
                if (!canContinue()) return available
                attempted += pkg
                if (release(pkg, identity) == false) {
                    available += pkg
                    break
                }
            }
        }
        val unconfirmed = attempted - available
        if (unconfirmed.isNotEmpty() && canContinue()) {
            val after = readBack()
            available += unconfirmed.filter { isAvailable(after[it]) }
        }
        return available
    }
}
