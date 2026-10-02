package com.catsmoker.app.features.gamingtools.tools.permops

import android.content.pm.PermissionInfo

/**
 * One toggleable permission: dangerous protection only, with its live grant state and
 * whether this tool revoked it (the restore set).
 */
data class ToggleablePerm(
    val permission: String,
    val granted: Boolean,
    val wasRevokedByUs: Boolean
)

/** A revoke awaiting the user's explicit confirmation: app, label and permission named. */
data class PermConfirm(
    val pkg: String,
    val label: String,
    val permission: String
)

/**
 * Pure core of the manual dangerous-permission toggle: which requested permissions are
 * user-toggleable and the revoked-by-us record bookkeeping. The shell and PackageManager
 * halves live in [PermToggleManager]; this object stays JVM-testable.
 */
object PermToggle {

    fun filterToggleable(
        requested: List<String>,
        protections: Map<String, Int>,
        granted: Set<String>,
        revokedByUs: Set<String>
    ): List<ToggleablePerm> {
        return requested
            .filter { isDangerous(protections[it]) }
            .distinct()
            .map { perm ->
                ToggleablePerm(
                    permission = perm,
                    granted = perm in granted,
                    wasRevokedByUs = perm in revokedByUs
                )
            }
    }

    /**
     * Dangerous base protection, flag bits ignored. The mask is correct on every version:
     * pre-28 values carry no flag bits (masking is identity), 28+ packs flags above the base.
     * No version branch — which also keeps JVM unit tests (whose SDK stub reads 0) honest.
     */
    private fun isDangerous(protection: Int?): Boolean {
        if (protection == null) return false
        return protection and PermissionInfo.PROTECTION_MASK_BASE == PermissionInfo.PROTECTION_DANGEROUS
    }

    /** Records a confirmed revoke so restore can regrant exactly what was taken. */
    fun recordRevoked(
        record: Map<String, Set<String>>,
        pkg: String,
        permission: String
    ): Map<String, Set<String>> {
        val next = record.toMutableMap()
        next[pkg] = (next[pkg] ?: emptySet()) + permission
        return next
    }

    /** Drops a confirmed regrant from the record. */
    fun clearRegranted(
        record: Map<String, Set<String>>,
        pkg: String,
        permission: String
    ): Map<String, Set<String>> {
        val next = record.toMutableMap()
        val remaining = (next[pkg] ?: emptySet()) - permission
        if (remaining.isEmpty()) next.remove(pkg) else next[pkg] = remaining
        return next
    }
}
