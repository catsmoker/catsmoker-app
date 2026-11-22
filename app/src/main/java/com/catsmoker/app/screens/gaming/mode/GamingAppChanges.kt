package com.catsmoker.app.screens.gaming.mode

import android.content.SharedPreferences
import androidx.core.content.edit
import com.catsmoker.app.platform.shell.ShellRunner
import com.catsmoker.app.platform.shell.ShellRunner.PrivilegedIdentity
import com.catsmoker.app.screens.gaming.apps.AppOpsRecord
import com.catsmoker.app.screens.gaming.apps.AppOpsVerdict
import com.catsmoker.app.screens.gaming.apps.SuspendVerdict

/** The app half of the Gaming Mode journal. Its caller serializes writes with the session lock. */
internal class GamingAppChanges(
    private val prefs: SharedPreferences,
    private val shellRunner: ShellRunner,
    private val hasInventoryConsent: () -> Boolean
) {
    data class Applied(
        val suspended: Set<String>,
        val suspendFailures: Int,
        val appOpsFailures: Int
    )

    data class RestoredSuspensions(val pending: Set<String>, val restoredCount: Int, val verified: Boolean)

    fun recordedSuspensions(): Set<String> = prefs.getStringSet("affected_pkgs", emptySet()).orEmpty()

    private fun recordedAppOps() = AppOpsRecord.decode(prefs.getStringSet("appops_prior", emptySet()).orEmpty())

    fun recordedAppOpsPackages(): List<String> = recordedAppOps().keys.sorted()

    suspend fun apply(targets: List<String>, identity: PrivilegedIdentity): Applied {
        if (targets.isEmpty()) return Applied(emptySet(), 0, 0)
        val prior = querySuspensionStates()
        // Shell suspension is shared with tools such as app freezers. No readable pre-state
        // means we cannot tell whether a later unsuspend would undo someone else's suspension.
        val freshTargets = targets.filter { prior[it] == false }
        var suspendFailures = targets.count { it !in prior }
        val confirmed = mutableSetOf<String>()
        val pending = mutableSetOf<String>()
        for ((index, pkg) in freshTargets.withIndex()) {
            pending += pkg
            // Persist before asking Android: Binder may apply the change before cancellation,
            // loss of the reply, or process death. Only a positive read-back retires the entry.
            check(prefs.edit().putStringSet("affected_pkgs", pending.toSet()).commit()) {
                "Could not persist the Gaming Mode suspension record"
            }
            val result = shellRunner.execPrivilegedSafeResult(*PrivilegeCommands.suspendArgs(pkg), identity = identity)
            if (result == null) {
                // The channel went away. Keep this attempt, but don't journal more packages.
                suspendFailures += freshTargets.size - index
                break
            }
            when (SuspendVerdict.reportedState(pkg, result.exitCode, result.stdout)) {
                true -> confirmed += pkg
                false -> {
                    pending -= pkg
                    suspendFailures++
                }
                null -> suspendFailures++
            }
        }
        saveSuspensions(pending.toSet())

        val appOps = mutableMapOf<String, String>()
        var appOpsFailures = 0
        for (pkg in freshTargets) {
            val original = readAppOp(pkg, identity) ?: continue
            if (original == "ignore") continue // already restricted; this session doesn't own it
            appOps[pkg] = original
            check(prefs.edit().putStringSet("appops_prior", AppOpsRecord.encode(appOps)).commit()) {
                "Could not persist the Gaming Mode background restriction record"
            }
            val set = shellRunner.execPrivilegedSafeResult(
                *PrivilegeCommands.appOpsSetArgs(pkg, "ignore"), identity = identity
            )
            val readBack = if (set != null) readAppOp(pkg, identity) else null
            if (readBack != "ignore") {
                appOpsFailures++
                if (readBack == original) appOps.remove(pkg)
            }
        }
        saveAppOps(appOps)
        return Applied(confirmed, suspendFailures, appOpsFailures)
    }

    suspend fun restoreSuspensions(): RestoredSuspensions {
        val recorded = recordedSuspensions()
        if (recorded.isEmpty()) return RestoredSuspensions(emptySet(), 0, verified = true)
        val savedUid = prefs.getInt(SUSPENSION_UID, -1)
        val identity = if (savedUid == -1) shellRunner.preferredPrivilegedIdentity()
        else PrivilegedIdentity.entries.firstOrNull { it.uid == savedUid }
        val observed = buildMap {
            if (identity != null) {
                for (pkg in recorded.sorted()) {
                    val result = shellRunner.execPrivilegedSafeResult(
                        *PrivilegeCommands.unsuspendArgs(pkg), identity = identity
                    ) ?: break
                    val state = SuspendVerdict.reportedState(pkg, result.exitCode, result.stdout)
                    if (state != null) put(pkg, state)
                    else if (SuspendVerdict.isMissingPackage(pkg, result.exitCode, result.stdout + "\n" + result.stderr)) {
                        put(pkg, false)
                    }
                }
            }
        }
        val unresolved = recorded.filter { observed[it] != false }
        val live = if (unresolved.isNotEmpty()) querySuspensionStates() else emptyMap()
        val pending = unresolved.filterTo(mutableSetOf()) { live[it] != false }
        saveSuspensions(pending.toSet())
        return RestoredSuspensions(
            pending, recorded.size - pending.size,
            verified = pending.all { live[it] == true || observed[it] == true }
        )
    }

    suspend fun restoreAppOps(): List<String> {
        val record = recordedAppOps()
        if (record.isEmpty()) return emptyList()
        val identity = shellRunner.preferredPrivilegedIdentity() ?: return record.keys.sorted()
        val remaining = record.filter { (pkg, original) ->
            readAppOp(pkg, identity) != original && run {
                val set = shellRunner.execPrivilegedSafeResult(
                    *PrivilegeCommands.appOpsSetArgs(pkg, original), identity = identity
                )
                set == null || readAppOp(pkg, identity) != original
            }
        }
        saveAppOps(remaining)
        return remaining.keys.sorted()
    }

    suspend fun querySuspensionStates(): Map<String, Boolean> {
        // Revoking inventory consent doesn't prevent targeted undo on a known journal entry.
        if (!hasInventoryConsent()) return emptyMap()
        val result = shellRunner.execPrivilegedResult(PACKAGE_STATES)
        return if (result?.isSuccess == true) SuspendVerdict.parseDumpsysStates(result.stdout) else emptyMap()
    }

    suspend fun querySuspensionOwners(): Map<String, SuspendVerdict.PackageState>? {
        if (!hasInventoryConsent()) return null
        val result = shellRunner.execPrivilegedResult(PACKAGE_OWNERS)?.takeIf { it.isSuccess } ?: return null
        return SuspendVerdict.parseDumpsysPackages(result.stdout).takeIf { it.isNotEmpty() }
    }

    /** Only manual recovery may release a selected Shell suspension via an existing root grant. */
    suspend fun releaseSelectedSuspension(pkg: String, identity: PrivilegedIdentity): Boolean? {
        val args = PrivilegeCommands.unsuspendArgs(pkg)
        val direct = shellRunner.execPrivilegedSafeResult(*args, identity = identity)
        val result = direct ?: if (identity == PrivilegedIdentity.SHELL && hasInventoryConsent()) {
            shellRunner.execAsShellViaRoot(*args)
        } else null
        return result?.let { SuspendVerdict.reportedState(pkg, it.exitCode, it.stdout) }
    }

    fun saveSuspensions(pending: Set<String>) = prefs.edit {
        if (pending.isEmpty()) remove("affected_pkgs") else putStringSet("affected_pkgs", pending)
    }

    private fun saveAppOps(record: Map<String, String>) = prefs.edit {
        if (record.isEmpty()) remove("appops_prior") else putStringSet("appops_prior", AppOpsRecord.encode(record))
    }

    private suspend fun readAppOp(pkg: String, identity: PrivilegedIdentity): String? {
        val result = shellRunner.execPrivilegedSafeResult(*PrivilegeCommands.appOpsGetArgs(pkg), identity = identity)
        return result?.takeIf { it.isSuccess }?.stdout?.let(AppOpsVerdict::parseMode)
    }

    companion object {
        const val SUSPENSION_UID = "session_suspension_uid"
        // Filter before returning over Binder, whose transaction buffer can't fit a full dump.
        private const val PACKAGE_STATES =
            """dumpsys package packages | grep -E '^[[:space:]]*Package \[|^[[:space:]]*User 0:|^Hidden system packages:'"""
        // Retain every User header so another profile's owner can never be read as user 0's.
        private const val PACKAGE_OWNERS =
            """dumpsys package packages | grep -E '^[[:space:]]*Package \[|^[[:space:]]*User [0-9]+:|^[[:space:]]*suspendingPackage=|^Hidden system packages:'"""
    }
}
