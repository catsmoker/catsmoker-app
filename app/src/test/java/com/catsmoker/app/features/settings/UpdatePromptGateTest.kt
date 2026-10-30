package com.catsmoker.app.features.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Later" on the update dialog is a mute for the *automatic* nag, not a switch that turns
 * "Check for Updates" into a dead button. A manual tap is an explicit question, so it must
 * answer even for a release the user postponed — otherwise the only feedback is the
 * "Checking for updates…" toast, and the button appears broken for the rest of that
 * release's life. This lock keeps the dismissal tied to `silent` only.
 */
class UpdatePromptGateTest {

    private fun viewModelSource(): String {
        val relative = "src/main/java/com/catsmoker/app/features/settings/SettingsViewModel.kt"
        return listOf(File(relative), File("app/$relative"))
            .firstOrNull { it.isFile }?.readText()
            ?: error("missing source under test: $relative")
    }

    private fun functionBody(source: String, name: String): String =
        source.substringAfter("fun $name").substringBefore("\n    }")

    @Test
    fun `a dismissed release still answers a manual check`() {
        val available = functionBody(viewModelSource(), "performUpdateCheck")
            .substringAfter("CheckResult.Available")
            .substringBefore("it.copy(")
        assertTrue(
            "the manual path must bypass the dismissed tag: expected `!silent || ...`",
            available.contains("!silent || dismissed != result.info.tagName")
        )
    }

    @Test
    fun `a dismissed release still stays quiet on the silent auto check`() {
        val available = functionBody(viewModelSource(), "performUpdateCheck")
            .substringAfter("CheckResult.Available")
        // The comparison has to survive: dropping it would re-prompt on every app start.
        assertTrue(available.contains("dismissed != result.info.tagName"))
        assertTrue("silent must still gate the prompt", available.contains("silent"))
    }

    @Test
    fun `the post-install confirmation names the version, not the raw tag`() {
        val pending = functionBody(viewModelSource(), "checkPendingUpdateResult")
        assertTrue(
            "the toast must resolve `11-2.0.5` to `2.0.5`",
            pending.contains("SelfUpdater.parseTagVersion(pending).name")
        )
        assertFalse(
            "the raw tag must not be shown",
            pending.contains("sys_update_installed, pending)")
        )
    }
}
