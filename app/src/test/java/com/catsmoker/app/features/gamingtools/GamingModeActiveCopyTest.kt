package com.catsmoker.app.features.gamingtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

/**
 * Behaviour lock for the Gaming Mode card's Active title (2026-10-28).
 *
 * The title read "System Locked" (and its equivalents in every translation). Nothing is locked
 * when a session runs — the card means "the optimizations this session applied are on". That
 * wording made the state look like a restriction on the phone, so the title now says the mode is
 * active, and it must never reuse the separate "Locked" wording the card shows when Gaming Mode
 * cannot run on the device at all.
 */
class GamingModeActiveCopyTest {

    private fun stringValue(dir: String, key: String): String {
        val file = File("src/main/res/$dir/strings_gaming.xml")
        return file.readLines()
            .first { it.contains("name=\"$key\"") }
            .substringAfter('>')
            .substringBefore("</string>")
            .trim()
    }

    @Test
    fun theEnglishActiveTitleSaysTheModeIsOn() {
        assertEquals("Active", stringValue("values", "gt_gm_active"))
        assertEquals("Active", stringValue("values-en-rGB", "gt_gm_active"))
    }

    @Test
    fun noTranslationClaimsTheSystemIsLockedWhileActive() {
        listOf("values", "values-en-rGB").forEach { dir ->
            val active = stringValue(dir, "gt_gm_active")
            assertFalse("$dir still says the system is locked: $active", active.contains("Lock", ignoreCase = true))
        }
    }

    @Test
    fun activeNeverReusesTheCannotRunWording() {
        // "Locked" is what the card says when root/Shizuku is missing. The running state must be
        // a different word in every language, or the two states read the same to the user.
        listOf("values", "values-en-rGB", "values-ar", "values-es", "values-pt-rBR", "values-zh-rCN")
            .forEach { dir ->
                assertNotEquals(
                    "$dir uses its cannot-run wording for the running state",
                    stringValue(dir, "gt_gm_locked"),
                    stringValue(dir, "gt_gm_active")
                )
            }
    }
}
