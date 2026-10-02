package com.catsmoker.app.features.gamingtools.tools.permops

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import com.catsmoker.app.system.shell.ShellRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manual dangerous-permission revoke/regrant for one target app at a time.
 *
 * Explicitly user-initiated, never silent or automatic: every revoke names the app, the
 * permission and the consequence up front (the UI confirms before calling), and every
 * confirmed revoke is recorded in this tool's own prefs file so restore regrants exactly
 * what was taken — the regrant guarantee. Only dangerous-protection permissions are
 * toggleable (see [PermToggle]); normal/signature permissions are the platform's business.
 *
 * Shell channel is root → Shizuku via [ShellRunner] (plain `pm grant/revoke` need no
 * downward retry); reads use PackageManager directly and need no privilege. A revoke the
 * shell refuses records nothing — the record only ever holds confirmed takes.
 */
@Singleton
class PermToggleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRunner: ShellRunner
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Factual outcome of one toggle: what was attempted and what the shell answered. */
    data class ToggleOutcome(val success: Boolean, val detail: String)

    /**
     * The target's dangerous permissions with live grant states. Empty when the package is
     * unknown, requests nothing toggleable, or cannot be read — all "nothing to toggle",
     * never an error to act on.
     */
    fun listToggleable(pkg: String): List<ToggleablePerm> {
        val pm = context.packageManager
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(
                    pkg,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            }
        }.getOrNull() ?: return emptyList()
        val requested = info.requestedPermissions?.toList().orEmpty()
        if (requested.isEmpty()) return emptyList()
        val flags = info.requestedPermissionsFlags ?: IntArray(0)
        val granted = requested.filterIndexed { i, _ ->
            i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        }.toSet()
        val protections = requested.associateWith { perm ->
            // getPermissionInfo was never migrated to the PackageInfoFlags type (unlike
            // getPackageInfo above), so the int form stands on every version.
            @Suppress("DEPRECATION")
            runCatching { pm.getPermissionInfo(perm, 0).protection }.getOrNull()
        }.mapNotNull { (perm, protection) ->
            if (protection == null) null else perm to protection
        }.toMap()
        return PermToggle.filterToggleable(requested, protections, granted, revokedFor(pkg))
    }

    /**
     * Revokes one dangerous permission after the UI's explicit confirmation, recording the
     * take only when the shell confirms it.
     */
    suspend fun revoke(pkg: String, permission: String): ToggleOutcome {
        if (!shellRunner.hasPrivilege()) {
            return ToggleOutcome(false, "no privileged channel")
        }
        val result = shellRunner.execSafeResult("pm", "revoke", pkg, permission)
        if (!result.isSuccess) {
            return ToggleOutcome(false, result.stderr.ifBlank { result.stdout }.ifBlank { "exit ${result.exitCode}" })
        }
        recordRevoked(pkg, permission)
        return ToggleOutcome(true, "revoked")
    }

    /** Regrants one permission, clearing it from the record when the shell confirms it. */
    suspend fun regrant(pkg: String, permission: String): ToggleOutcome {
        if (!shellRunner.hasPrivilege()) {
            return ToggleOutcome(false, "no privileged channel")
        }
        val result = shellRunner.execSafeResult("pm", "grant", pkg, permission)
        if (!result.isSuccess) {
            return ToggleOutcome(false, result.stderr.ifBlank { result.stdout }.ifBlank { "exit ${result.exitCode}" })
        }
        clearRegranted(pkg, permission)
        return ToggleOutcome(true, "granted")
    }

    /**
     * Regrants everything this tool revoked for [pkg], in record order. Permissions that
     * refuse stay recorded (and reported) rather than dropped — a half-restored app must
     * say which half, not claim wholeness.
     */
    suspend fun restoreAll(pkg: String): RestoreOutcome {
        val pending = revokedFor(pkg).toList()
        var restored = 0
        val failed = mutableListOf<String>()
        for (permission in pending) {
            val outcome = regrant(pkg, permission)
            if (outcome.success) restored++ else failed.add(permission)
        }
        return RestoreOutcome(restored = restored, failed = failed)
    }

    data class RestoreOutcome(val restored: Int, val failed: List<String>)

    /** Permissions this tool revoked for [pkg] and has not yet regranted. */
    fun revokedFor(pkg: String): Set<String> = readRecord()[pkg] ?: emptySet()

    private fun recordRevoked(pkg: String, permission: String) {
        val next = PermToggle.recordRevoked(readRecord(), pkg, permission)
        writeRecord(next)
    }

    private fun clearRegranted(pkg: String, permission: String) {
        val next = PermToggle.clearRegranted(readRecord(), pkg, permission)
        writeRecord(next)
    }

    private fun readRecord(): Map<String, Set<String>> {
        // One key per package would scatter the record; a single serialized map keeps the
        // restore set atomic. Small by construction (only confirmed takes).
        val raw = prefs.getStringSet(KEY_RECORD, emptySet()) ?: emptySet()
        return raw.mapNotNull { entry ->
            val at = entry.indexOf('@')
            if (at <= 0) null else entry.substring(0, at) to entry.substring(at + 1)
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    }

    private fun writeRecord(record: Map<String, Set<String>>) {
        val raw = record.flatMap { (pkg, perms) -> perms.map { "$pkg@$it" } }.toSet()
        prefs.edit { putStringSet(KEY_RECORD, raw) }
    }

    companion object {
        private const val PREFS_NAME = "perm_toggle_prefs"
        private const val KEY_RECORD = "revoked_record"
    }
}
