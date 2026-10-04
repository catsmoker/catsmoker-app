package com.catsmoker.app.system.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Arabic→English switch restarted into Arabic because [AppearanceStore.setLanguage]
 * persisted with async `apply()` while the caller kills the process immediately after
 * ([LocaleHelper.restartApp] ends it with `exit(0)`): the queued write died unflushed
 * and the fresh process read the stale tag. The write must stay synchronous so the
 * recreated process always sees the newly picked language.
 *
 * No Robolectric/MockK in this module (JUnit + org.json only), so this pins the
 * invariant on the source — same file-reading precedent as `LocaleParityTest`.
 */
class LocaleRestartPersistenceTest {

    @Test
    fun setLanguagePersistsSynchronously() {
        val source = File("src/main/java/com/catsmoker/app/system/config/AppearanceStore.kt")
            .readText()
        val start = source.indexOf("fun setLanguage(")
        assertTrue("setLanguage missing from AppearanceStore", start >= 0)
        val tail = source.substring(start)
        // End of the function body: next same-indent member or closing brace.
        val end = Regex("\n    (fun |private fun |val |const |@Volatile)").find(tail)
            ?.range?.first ?: tail.length
        val body = tail.substring(0, end)
        assertTrue(
            "setLanguage must commit synchronously (commit = true): the locale " +
                "restart kills the process before an async apply() flushes",
            body.contains("commit = true") || body.contains("commit=true")
        )
    }
}
