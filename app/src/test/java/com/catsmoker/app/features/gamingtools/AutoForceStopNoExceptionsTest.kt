package com.catsmoker.app.features.gamingtools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour lock for Auto Force Stop's no-exceptions rule (2026-10-28).
 *
 * The card used to offer a tick-list of apps to keep open. The feature now closes every app the
 * user leaves, so no user-editable list may survive in the card, the view model, the service or
 * the stored preferences. The phone-wide protections (home screen, keyboard, this app, system
 * packages) stay in the service; they are not user exceptions.
 */
class AutoForceStopNoExceptionsTest {

    private val root = "src/main/java/com/catsmoker/app/features/gamingtools"
    private val screen = File(root, "GamingToolsScreen.kt").readText()
    private val viewModel = File(root, "GamingToolsViewModel.kt").readText()
    private val service = File(root, "tools/forcestop/AutoForceStopService.kt").readText()

    @Test
    fun cardHasNoExceptionList() {
        val content = screen
            .substringAfter("fun AutoForceStopUsageNotice(")
            .substringBefore("fun CleaningContent(")
        assertFalse("the card still lists tickable apps", content.contains("LazyColumn"))
        assertFalse("the card still draws checkboxes", content.contains("Checkbox("))
        assertFalse("the card still shows the tick hint", content.contains("gt_afs_tick_hint"))
        assertFalse("the card still reads a keep set", content.contains("kept"))
    }

    @Test
    fun serviceStopsEveryAppItIsAllowedToStop() {
        assertFalse("the service still injects a keep store", service.contains("KeepAliveStore"))
        assertFalse("the service still filters by a keep list", service.contains("getKeptPackages"))
        assertFalse("the service still reports a kept count", service.contains("gt_svc_afs_keep_some"))
        assertTrue(
            "the remove-on-leave check must not consult a user list",
            service.contains("!isProtected(left) &&")
        )
    }

    @Test
    fun viewModelHasNoKeepListWiring() {
        assertFalse(viewModel.contains("keepAliveStore"))
        assertFalse(viewModel.contains("autoForceStopKeepPackages"))
        assertFalse(viewModel.contains("toggleAutoForceStopKeepPackage"))
        assertFalse(screen.contains("onToggleAutoForceStopKeepPackage"))
    }

    @Test
    fun savedKeepListCodeIsDeleted() {
        assertFalse(
            "KeepAliveStore.kt must be deleted with the feature",
            File(root, "tools/forcestop/KeepAliveStore.kt").exists()
        )
        val app = File("src/main/java/com/catsmoker/app/system/CatsmokerApp.kt").readText()
        assertTrue(
            "an upgrading install must delete the old keep-list preferences",
            app.contains("deleteSharedPreferences(\"AutoForceStopPrefs\")")
        )
    }

    @Test
    fun subtitleNoLongerPromisesAnExceptionList() {
        val oldWording = mapOf(
            "values" to "except your keep list",
            "values-en-rGB" to "except your chosen ones",
            "values-ar" to "باستثناء قائمتك",
            "values-es" to "salvo tu lista",
            "values-pt-rBR" to "exceto os da sua lista",
            "values-zh-rCN" to "保留列表"
        )
        oldWording.forEach { (dir, phrase) ->
            val text = File("src/main/res/$dir/strings_gaming.xml").readText()
            val subtitle = text.lineSequence()
                .first { it.contains("name=\"gt_tool_afs_sub\"") }
            assertFalse("$dir subtitle still promises kept apps: $phrase", subtitle.contains(phrase))
        }
    }

    @Test
    fun everyLocaleDropsTheKeepListCopy() {
        File("src/main/res").listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { File(it, "strings_gaming.xml") }
            .filter { it.isFile }
            .forEach { file ->
                val text = file.readText()
                listOf(
                    "gt_afs_keep_none",
                    "gt_afs_keep_some",
                    "gt_afs_tick_hint",
                    "gt_svc_afs_keep_some"
                ).forEach { key ->
                    assertFalse("${file.parentFile.name} still has $key", text.contains(key))
                }
            }
    }
}
