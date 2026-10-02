package com.catsmoker.app.features.gamingtools.tools.permops

import android.content.pm.PermissionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the dangerous-permission toggle's pure core: which requested permissions are
 * user-toggleable (dangerous protection only — normal/signature permissions are the
 * platform's business, and toggling them is either a no-op or a breakage), and the
 * revoked-by-us record that makes restore exact.
 */
class PermToggleTest {

    private val protections = mapOf(
        "android.permission.CAMERA" to PermissionInfo.PROTECTION_DANGEROUS,
        "android.permission.INTERNET" to PermissionInfo.PROTECTION_NORMAL,
        "android.permission.MANAGE_DOCUMENTS" to PermissionInfo.PROTECTION_SIGNATURE,
    )

    @Test
    fun onlyDangerousPermissionsAreToggleable() {
        val entries = PermToggle.filterToggleable(
            requested = listOf(
                "android.permission.CAMERA",
                "android.permission.INTERNET",
                "android.permission.MANAGE_DOCUMENTS"
            ),
            protections = protections,
            granted = setOf("android.permission.CAMERA", "android.permission.INTERNET"),
            revokedByUs = emptySet()
        )
        assertEquals(listOf("android.permission.CAMERA"), entries.map { it.permission })
        assertTrue(entries.single().granted)
    }

    @Test
    fun protectionFlagsDoNotHideDangerousBase() {
        // API 28+ packs flags into the protection int; the base mask still reads dangerous.
        val withFlags = mapOf(
            "android.permission.CAMERA" to
                (PermissionInfo.PROTECTION_DANGEROUS or PermissionInfo.PROTECTION_FLAG_PRIVILEGED)
        )
        val entries = PermToggle.filterToggleable(
            requested = listOf("android.permission.CAMERA"),
            protections = withFlags,
            granted = emptySet(),
            revokedByUs = emptySet()
        )
        assertEquals(1, entries.size)
        assertFalse(entries.single().granted)
    }

    @Test
    fun unknownProtectionsAreNotToggleable() {
        val entries = PermToggle.filterToggleable(
            requested = listOf("com.vendor.ODD"),
            protections = emptyMap(),
            granted = emptySet(),
            revokedByUs = emptySet()
        )
        assertTrue(entries.isEmpty())
    }

    @Test
    fun revokedByUsMarksRestoreCandidates() {
        val entries = PermToggle.filterToggleable(
            requested = listOf("android.permission.CAMERA"),
            protections = protections,
            granted = emptySet(),
            revokedByUs = setOf("android.permission.CAMERA")
        )
        assertTrue(entries.single().wasRevokedByUs)
    }

    @Test
    fun recordHelpersAreExact() {
        val empty = emptyMap<String, Set<String>>()
        val after = PermToggle.recordRevoked(empty, "com.a", "android.permission.CAMERA")
        assertEquals(setOf("android.permission.CAMERA"), after["com.a"])
        val cleared = PermToggle.clearRegranted(after, "com.a", "android.permission.CAMERA")
        assertTrue(cleared["com.a"].isNullOrEmpty())
        // Clearing one of two keeps the other.
        val two = PermToggle.recordRevoked(after, "com.a", "android.permission.ACCESS_FINE_LOCATION")
        val one = PermToggle.clearRegranted(two, "com.a", "android.permission.CAMERA")
        assertEquals(setOf("android.permission.ACCESS_FINE_LOCATION"), one["com.a"])
    }
}
