package com.catsmoker.app.system.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpoofConfigProviderTest {

    private val ourUid = 10250
    private val ourPackage = "com.catsmoker.app"
    private val targetUid = 10100
    private val targetPackage = "com.tencent.ig"

    private fun resolve(
        callingUid: Int,
        callingPackage: String?,
        explicitPackage: String?
    ): String? {
        // No PackageManager: siblings list is empty, so cross-package requests from
        // other UIDs are always rejected. Enough to test the UID-scoping contract.
        return resolvePackageNameForTest(
            callingUid = callingUid,
            callingPackage = callingPackage,
            explicitPackage = explicitPackage,
            ourUid = ourUid,
            ourPackageName = ourPackage,
            packageManager = null
        )
    }

    @Test
    fun ownUidCanResolveAnyPackage() {
        assertEquals("com.tencent.ig", resolve(ourUid, null, "com.tencent.ig"))
        assertEquals("com.pubg.newstate", resolve(ourUid, null, "com.pubg.newstate"))
    }

    @Test
    fun ownUidWithNoExplicitPackageGetsOwnPackage() {
        assertEquals("com.catsmoker.app", resolve(ourUid, null, null))
    }

    @Test
    fun otherAppCanSeeItsOwnPackage() {
        assertEquals("com.tencent.ig", resolve(targetUid, "com.tencent.ig", null))
    }

    @Test
    fun otherAppCannotSeeSomeoneElsesPackage() {
        assertNull(resolve(targetUid, "com.tencent.ig", "com.pubg.newstate"))
        assertNull(resolve(targetUid, "com.tencent.ig", "com.catsmoker.app"))
    }

    @Test
    fun otherAppWithNoExplicitGetsOwnPackage() {
        assertEquals("com.tencent.ig", resolve(targetUid, "com.tencent.ig", null))
        assertEquals("com.mobile.legends", resolve(targetUid, "com.mobile.legends", null))
    }

    @Test
    fun nullExplicitPackageFallsBackToCaller() {
        assertEquals("com.tencent.ig", resolve(targetUid, "com.tencent.ig", null))
    }
}
