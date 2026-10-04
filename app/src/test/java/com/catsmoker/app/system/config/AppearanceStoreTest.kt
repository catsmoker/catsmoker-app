package com.catsmoker.app.system.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Dynamic Color theme option must only be offered on Android 12+ (API 31):
 * below that the OS has no Material You palette and picking it silently fell back
 * to the default theme. Capability is passed explicitly (pure function) because
 * `Build.VERSION.SDK_INT` is 0 on the JVM.
 */
class AppearanceStoreTest {

    @Test
    fun dynamicHiddenBelowS() {
        val modes = AppearanceStore.availableThemeModes(supportsDynamic = false)
        assertEquals(
            listOf(
                AppearanceStore.ThemeMode.SYSTEM,
                AppearanceStore.ThemeMode.DARK,
                AppearanceStore.ThemeMode.LIGHT
            ),
            modes
        )
        assertFalse(modes.contains(AppearanceStore.ThemeMode.DYNAMIC))
    }

    @Test
    fun dynamicShownOnSPlus() {
        val modes = AppearanceStore.availableThemeModes(supportsDynamic = true)
        assertEquals(
            listOf(
                AppearanceStore.ThemeMode.SYSTEM,
                AppearanceStore.ThemeMode.DARK,
                AppearanceStore.ThemeMode.LIGHT,
                AppearanceStore.ThemeMode.DYNAMIC
            ),
            modes
        )
        assertTrue(modes.contains(AppearanceStore.ThemeMode.DYNAMIC))
    }
}
