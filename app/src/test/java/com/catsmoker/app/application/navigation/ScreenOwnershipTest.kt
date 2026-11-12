package com.catsmoker.app.application.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-layout guards, not a substitute for driving navigation on a device. */
class ScreenOwnershipTest {
    private fun source(sourceSet: String, path: String): String =
        File("src/$sourceSet/java/com/catsmoker/app/$path").readText()

    @Test
    fun sharedDestinationsHaveTheirOwnScreenAreas() {
        val screens = mapOf(
            "home/HomeScreen.kt" to "HomeRoute",
            "permissions/PermissionScreen.kt" to "PermissionRoute",
            "gaming/GamingToolsScreen.kt" to "GamingToolsRoute",
            "gaming/crosshair/CrosshairLibraryScreen.kt" to "CrosshairLibraryRoute",
            "editgamefiles/EditGameFilesScreen.kt" to "EditGameFilesRoute",
            "about/AboutScreen.kt" to "AboutRoute",
            "legal/LicensesScreen.kt" to "LicensesRoute",
            "legal/PrivacyPolicyScreen.kt" to "PrivacyPolicyRoute",
            "legal/TermsScreen.kt" to "TermsRoute",
            "settings/SettingsScreen.kt" to "SettingsRoute",
            "logs/LogsScreen.kt" to "LogsRoute",
            "donate/DonateScreen.kt" to "DonateRoute"
        )
        val graph = source("main", "application/navigation/AppNavHost.kt")
        screens.forEach { (path, route) ->
            val owner = "com.catsmoker.app.screens.${path.substringBeforeLast('/').replace('/', '.')}"
            val screen = source("main", "screens/$path")
            assertTrue(path, screen.startsWith("package $owner\n"))
            assertTrue(path, screen.contains("fun $route("))
            assertTrue(route, graph.contains("import $owner.$route"))
        }
        assertEquals(screens.size, Regex("composable\\(Routes\\.").findAll(graph).count())
        assertFalse(File("src/main/java/com/catsmoker/app/screens/about/LegalScreens.kt").exists())
    }

    @Test
    fun spoofDestinationsStayFullOnlyWithTheirExistingSharedState() {
        val screens = mapOf(
            "hub/SpoofDeviceScreen.kt" to "SpoofRoute",
            "profiles/ProfilesListScreen.kt" to "ProfilesListScreen",
            "profileeditor/ProfileEditorScreen.kt" to "ProfileEditorScreen",
            "appassignment/AppAssignmentScreen.kt" to "AppAssignmentScreen",
            "safemode/SafeModeScreen.kt" to "SafeModeScreen",
            "diagnostics/DiagnosticsScreen.kt" to "DiagnosticsScreen"
        )
        val graph = source("full", "application/navigation/SpoofNavGraph.kt")
        screens.forEach { (path, screen) ->
            val owner = "com.catsmoker.app.screens.spoofing.${path.substringBefore('/')}"
            assertTrue(path, source("full", "screens/spoofing/$path").contains("package $owner"))
            assertTrue(screen, graph.contains("import $owner.$screen"))
        }
        assertEquals(screens.size, Regex("composable\\(SpoofRoutes\\.").findAll(graph).count())
        assertEquals(
            4,
            Regex("val viewModel: SpoofDeviceViewModel = hiltViewModel\\(parentEntry\\)").findAll(graph).count()
        )
        assertTrue(graph.contains("getBackStackEntry(SpoofRoutes.SPOOF_DEVICE)"))
        for (sourceSet in listOf("main", "playstore")) {
            assertFalse(File("src/$sourceSet/java/com/catsmoker/app/screens/spoofing").exists())
            assertFalse(File("src/$sourceSet/java/com/catsmoker/app/features/spoofdevice").exists())
        }
    }

    @Test
    fun routeValuesAndCrosshairParentLifetimeStayStable() {
        val routes = source("main", "application/navigation/Routes.kt")
        val values = Regex("const val (\\w+) = \"([^\"]+)\"").findAll(routes)
            .associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(
            mapOf(
                "MAIN" to "main", "PERMISSION" to "permission", "GAMING_TOOLS" to "gaming_tools",
                "CROSSHAIR_LIBRARY" to "crosshair_library", "EDIT_GAME_FILES" to "edit_game_files",
                "ABOUT" to "about", "LICENSES" to "licenses", "PRIVACY" to "privacy",
                "TERMS" to "terms", "SETTINGS" to "settings", "LOGS" to "logs", "DONATE" to "donate"
            ),
            values
        )
        val graph = source("main", "application/navigation/AppNavHost.kt")
        assertTrue(graph.contains("getBackStackEntry(Routes.GAMING_TOOLS)"))
        assertTrue(graph.contains("GamingToolsViewModel = hiltViewModel(parentEntry)"))
        assertTrue(graph.contains("gamingToolsViewModel.onSelectCrosshair(asset)"))
        assertTrue(graph.contains("popUpTo(Routes.PERMISSION) { inclusive = true }"))
    }

    @Test
    fun flavorAdaptersStillHaveMatchingPackagesAndPaths() {
        val adapters = listOf(
            "screens/home/DistributionQuickAction.kt",
            "integrations/ads/AdsInit.kt", "integrations/ads/AdBanner.kt", "integrations/ads/AdManager.kt",
            "application/privacy/DistributionPrivacyGate.kt", "application/sharing/ProfileShareHandler.kt",
            "platform/storage/StorageAccess.kt", "application/distribution/VariantCapabilities.kt"
        )
        adapters.forEach { path ->
            val expected = "package com.catsmoker.app.${path.substringBeforeLast('/').replace('/', '.')}"
            for (sourceSet in listOf("full", "playstore")) {
                assertTrue("$sourceSet/$path", source(sourceSet, path).lineSequence().any { it == expected })
            }
        }
    }
}
