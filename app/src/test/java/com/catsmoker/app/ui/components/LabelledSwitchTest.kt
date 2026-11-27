package com.catsmoker.app.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A `Switch` announces only its own state. Every toggle in this app draws its words in a sibling
 * `Text`, so a bare `Switch` reached TalkBack as "on, switch" with nothing named — twenty-six of
 * them across the six Gaming Tools sections alone.
 *
 * The rule this pins: a switch that owns its state must go through [LabelledSwitch], which requires
 * the label. A switch with `onCheckedChange = null` is driven by a parent that carries the words and
 * is not reported. A source sweep is the only check available here because the defect is invisible
 * to every other test — it compiles, renders and works, and only a screen reader notices.
 */
class LabelledSwitchTest {

    private val sources = File("src/main/java/com/catsmoker/app").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filterNot { it.path.endsWith("ui${File.separator}components${File.separator}LabelledSwitch.kt") }
        .toList()

    @Test
    fun everySwitchThatOwnsItsStateNamesItsSetting() {
        val offenders = sources.filter { file ->
            file.readText().lineSequence().any { line ->
                line.trimStart().startsWith("Switch(") && !line.contains("onCheckedChange = null")
            }
        }.map { it.path }
        assertTrue(
            "these switches need a label (use LabelledSwitch):\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun theWrapperIsAThinSwitchWithADescription() {
        val wrapper = File("src/main/java/com/catsmoker/app/ui/components/LabelledSwitch.kt").readText()
        assertTrue(wrapper.contains("contentDescription = label"))
        assertTrue(wrapper.contains("Switch("))
    }
}
