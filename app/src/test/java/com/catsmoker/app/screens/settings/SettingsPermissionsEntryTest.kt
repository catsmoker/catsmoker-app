package com.catsmoker.app.screens.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPermissionsEntryTest {
    @Test
    fun permissionsHasOneEntryAndRetainsTheSeparateAndroidSettingsShortcut() {
        val settings = File("src/main/java/com/catsmoker/app/screens/settings/SettingsScreen.kt").readText()
        assertEquals(1, Regex("onClick = onOpenPermissions").findAll(settings).count())
        assertEquals(1, Regex("R\\.string\\.sys_permissions_title").findAll(settings).count())
        assertFalse(settings.contains("R.string.policy_access_title"))
        assertTrue(settings.contains("onClick = { openAppSystemSettings(context) }"))
        assertTrue(settings.contains("R.string.sys_permissions_open_system"))
        val adsSlot = settings.substringAfter("adsControls = {").substringBefore("storeControls =")
        assertTrue(adsSlot.contains("DistributionAdsSettings(onOpenDonate)"))
        assertFalse(adsSlot.contains("onOpenPermissions"))
    }
}
