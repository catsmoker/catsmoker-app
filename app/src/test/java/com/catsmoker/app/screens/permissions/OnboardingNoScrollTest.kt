package com.catsmoker.app.screens.permissions

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three first-run screens are deliberately non-scrollable: every step must be readable at
 * a glance, including on the shortest supported phone. A scroll container is the easy way to
 * "fix" a layout that no longer fits, so this test fails the build if one is reintroduced.
 *
 * The two long steps (Terms, Permissions) do keep one deliberate exception: in landscape, or on
 * any viewport shorter than ~420 dp, the *inner* document/grid scrolls inside a
 * `maxHeight < 420.dp` branch so the action row and footer stay reachable. That branch is a
 * short-viewport fallback, never the default layout, so a scroll container is only allowed in a
 * file that also gates it on the measured height.
 *
 * The shared info sheet is exempt by design - it lives in [InfoButton] and may scroll because
 * the permission explanations are optional detail, not the step itself.
 */
class OnboardingNoScrollTest {

    private val onboardingSources = listOf(
        "src/main/java/com/catsmoker/app/screens/permissions/PermissionScreen.kt",
        "src/main/java/com/catsmoker/app/screens/permissions/AppearanceStepScreen.kt",
        "src/main/java/com/catsmoker/app/screens/permissions/AgreementStepScreen.kt",
        "src/main/java/com/catsmoker/app/screens/permissions/PermissionsListScreen.kt",
        "src/main/java/com/catsmoker/app/screens/permissions/OnboardingUi.kt"
    )

    /** Steps whose whole frame must fit; they carry no scroll container at all. */
    private val fixedFrameSources = listOf(
        "src/main/java/com/catsmoker/app/screens/permissions/PermissionScreen.kt",
        "src/main/java/com/catsmoker/app/screens/permissions/AppearanceStepScreen.kt",
        "src/main/java/com/catsmoker/app/screens/permissions/OnboardingUi.kt"
    )

    private val scrollTokens = listOf(
        "verticalScroll",
        "horizontalScroll",
        "rememberScrollState",
        "LazyColumn",
        "LazyRow",
        "LazyVerticalGrid"
    )

    private val fallbackGuard = "maxHeight < 420.dp"

    @Test
    fun fixedFrameStepsUseNoScrollContainer() {
        fixedFrameSources.forEach { path ->
            val source = File(path).readText()
            scrollTokens.forEach { token ->
                assertFalse("$path must not use $token - this step has to fit without scrolling", source.contains(token))
            }
        }
    }

    @Test
    fun scrollContainersAreOnlyShortViewportFallbacks() {
        onboardingSources.forEach { path ->
            val source = File(path).readText()
            if (scrollTokens.any { source.contains(it) }) {
                assertTrue(
                    "$path may only scroll inside a '$fallbackGuard' fallback branch",
                    source.contains(fallbackGuard)
                )
            }
        }
    }

    @Test
    fun allThreeStepsShareTheSameFrame() {
        val source = onboardingSources.joinToString("\n") { File(it).readText() }
        val scaffoldCalls = Regex("""\bOnboardingScaffold\s*\(""").findAll(source).count()
        assertTrue(
            "Welcome, Terms and Permissions must each use OnboardingScaffold (found $scaffoldCalls)",
            scaffoldCalls >= 3
        )
    }
}
