package com.catsmoker.app.features.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The three first-run screens are deliberately non-scrollable: every step must be readable at
 * a glance, including on the shortest supported phone. A scroll container is the easy way to
 * "fix" a layout that no longer fits, so this test fails the build if one is reintroduced.
 *
 * The shared info sheet is exempt by design — it lives in [InfoButton] and may scroll because
 * the permission explanations are optional detail, not the step itself.
 */
class OnboardingNoScrollTest {

    private val onboardingSources = listOf(
        "src/main/java/com/catsmoker/app/features/permissions/PermissionScreen.kt",
        "src/main/java/com/catsmoker/app/features/permissions/OnboardingUi.kt"
    )

    private val scrollTokens = listOf(
        "verticalScroll",
        "horizontalScroll",
        "rememberScrollState",
        "LazyColumn",
        "LazyRow",
        "LazyVerticalGrid"
    )

    @Test
    fun onboardingScreensNeverUseScrollContainers() {
        onboardingSources.forEach { path ->
            val source = File(path).readText()
            scrollTokens.forEach { token ->
                assertFalse("$path must not use $token — onboarding has to fit without scrolling", source.contains(token))
            }
        }
    }

    @Test
    fun allThreeStepsShareTheSameFrame() {
        val source = File(onboardingSources.first()).readText()
        val scaffoldCalls = Regex("""\bOnboardingScaffold\s*\(""").findAll(source).count()
        assertTrue(
            "Welcome, Terms and Permissions must each use OnboardingScaffold (found $scaffoldCalls)",
            scaffoldCalls >= 3
        )
    }
}
