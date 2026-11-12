package com.catsmoker.app.ui.theme

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Source/resource guards, not a substitute for device checks of cutouts and navigation modes. */
class EdgeToEdgeLayoutTest {
    private val sourceRoot = File("src/main/java/com/catsmoker/app")
    private val resourceRoot = File("src/main/res")
    private val platformTheme = "Platform.Theme.Catsmoker"
    private val cutoutMode = "android:windowLayoutInDisplayCutoutMode"
    private val barColors = setOf("android:statusBarColor", "android:navigationBarColor")

    @Test
    fun activityKeepsAndroidXEdgeToEdgeCompatibility() {
        val activity = File(sourceRoot, "system/MainActivity.kt").readText()
        assertTrue(activity.contains("import androidx.activity.enableEdgeToEdge"))
        assertTrue(activity.contains("enableEdgeToEdge()"))
    }

    @Test
    fun screenFramesProtectSystemBarsAndLandscapeCutouts() {
        val frames = listOf(
            "ui/components/ScreenScaffold.kt",
            "screens/home/HomeScreen.kt",
            "screens/permissions/OnboardingUi.kt",
            "application/startup/StartupScreen.kt",
            "screens/gaming/crosshair/CrosshairLibraryScreen.kt"
        )
        val insets = Regex(
            "(?:\\.systemBarsPadding\\(\\)|\\.windowInsetsPadding\\(WindowInsets\\.systemBars\\))" +
                "\\s*\\.displayCutoutPadding\\(\\)"
        )
        frames.forEach { path ->
            assertTrue("Missing bar/cutout padding in $path", insets.containsMatchIn(File(sourceRoot, path).readText()))
        }
    }

    @Test
    fun localeAndNightThemesCannotOverridePlatformWindowSettings() {
        listOf("values", "values-night", "values-ja").forEach { directory ->
            val base = style("$directory/themes.xml", "Base.Theme.Catsmoker")
            assertEquals(platformTheme, base.getAttribute("parent"))
            assertTrue(items(base).keys.intersect(barColors + cutoutMode).isEmpty())
        }
    }

    @Test
    fun olderAndroidKeepsTransparentBarsAndSupportedCutoutModes() {
        val modes = mapOf("values" to null, "values-v28" to "shortEdges", "values-v30" to "always")
        modes.forEach { (directory, mode) ->
            val parent = style("$directory/platform_theme.xml", platformTheme)
            assertEquals("Theme.Material3.DayNight.NoActionBar", parent.getAttribute("parent"))
            val settings = items(parent)
            barColors.forEach { color -> assertEquals("@android:color/transparent", settings[color]) }
            assertEquals(mode, settings[cutoutMode])
        }
    }

    @Test
    fun android15ThemeUsesAlwaysWithoutAppBarColors() {
        val parent = style("values-v35/platform_theme.xml", platformTheme)
        assertEquals("Theme.Material3.DayNight.NoActionBar", parent.getAttribute("parent"))
        val settings = items(parent)
        assertEquals("always", settings[cutoutMode])
        assertFalse(settings.keys.any { it in barColors })
    }

    @Test
    fun themeStillControlsSystemBarIconContrast() {
        val theme = File(sourceRoot, "ui/theme/Theme.kt").readText()
        assertTrue(theme.contains("isAppearanceLightStatusBars = !darkTheme"))
        assertTrue(theme.contains("isAppearanceLightNavigationBars = !darkTheme"))
    }

    private fun style(path: String, name: String): Element {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resourceRoot, path))
        val styles = document.getElementsByTagName("style")
        return (0 until styles.length).map { styles.item(it) as Element }
            .single { it.getAttribute("name") == name }
    }

    private fun items(style: Element): Map<String, String> {
        val items = style.getElementsByTagName("item")
        return (0 until items.length).associate { index ->
            val item = items.item(index) as Element
            item.getAttribute("name") to item.textContent.trim()
        }
    }
}
