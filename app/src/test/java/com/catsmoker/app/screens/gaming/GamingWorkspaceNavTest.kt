package com.catsmoker.app.screens.gaming

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Gaming Tools section switcher against going back to a sideways-scrolling strip.
 *
 * The workspace has six peer sections. It used to draw them as filter chips in a
 * `Row + horizontalScroll`, which on a 411 dp phone measured 954 px of visible row for 2274 px
 * of content: the third chip was clipped mid-word and the last two sections were off-screen, so
 * the screen hid most of its own navigation. The fix is one equal-width tab per section (or a
 * rail on a wide window), and it only stays a fix while the labels stay short enough to fit a
 * sixth of a 320 dp phone. Both properties are cheap to break by accident, so they are asserted
 * here rather than rediscovered on a device.
 */
class GamingWorkspaceNavTest {

    private val screen = File(
        "src/main/java/com/catsmoker/app/screens/gaming/GamingToolsScreen.kt"
    ).readText()

    private val tabStrings = File("src/main/res/values/strings_gaming_workspace.xml").readText()

    private val tabRow = File(
        "src/main/java/com/catsmoker/app/ui/components/SectionTabs.kt"
    ).readText()

    @Test
    fun everySectionIsReachableFromTheSwitcher() {
        val enumEntries = Regex("""^\s{4}([A-Z_]+)\(R\.string\.(gt_tab_\w+), Icons\.Default\.(\w+)\)""", RegexOption.MULTILINE)
            .findAll(screen).toList()
        assertEquals("all six workspace sections need a tab label and an icon", 6, enumEntries.size)
        assertEquals(6, enumEntries.map { it.groupValues[1] }.distinct().size)
        // The enum's own name is the saved-state key; the tab caption is a separate string so a
        // translation cannot silently reset which section the user was reading.
        assertTrue(screen.contains("SectionTab(id = it.name"))
    }

    @Test
    fun tabCaptionsStayShortEnoughForOneRow() {
        val captions = Regex("""<string name="gt_tab_\w+">([^<]+)</string>""")
            .findAll(tabStrings).map { it.groupValues[1] }.toList()
        assertEquals(6, captions.size)
        captions.forEach { caption ->
            // Six tabs across a 320 dp window is 53 dp each; "Maintenance" at 11 sp dot-matrix
            // does not fit there, "Clean" does.
            assertTrue("tab caption too long for a sixth of a small phone: '$caption'", caption.length <= 11)
            assertFalse("tab caption must not be empty", caption.isBlank())
        }
    }

    @Test
    fun switcherNeverScrollsSideways() {
        assertFalse(
            "the section switcher must not be a horizontally scrolling chip strip again",
            screen.contains("FilterChip(")
        )
        // The two branches of the switcher: tabs below the context bar, or a rail beside the body.
        val switcherBlock = screen.substringAfter("if (sectionRail) {")
            .substringBefore("sectionBody(Modifier.weight(1f))")
        assertFalse("switcher block reintroduced horizontalScroll", switcherBlock.contains("horizontalScroll"))
        assertTrue("expanded windows use the rail", switcherBlock.contains("SectionRail("))
        assertTrue("compact windows use the tab row", switcherBlock.contains("SectionTabRow("))
        assertEquals(
            "the two switchers must share one select handler",
            1,
            Regex("""val selectSection: \(SectionTab\) -> Unit""").findAll(screen).count()
        )
    }

    @Test
    fun sectionBodyKeepsOneScrollOwner() {
        // The switcher is fixed; only the section body scrolls, and each section keeps its own
        // position through SaveableStateProvider so switching away and back does not lose it.
        assertTrue(screen.contains("sectionStates.SaveableStateProvider(section.name)"))
        assertEquals(
            "one vertical scroll belongs to the section body",
            1,
            Regex("""bodyModifier\.fillMaxWidth\(\)\.verticalScroll""").findAll(screen).count()
        )
    }

    @Test
    fun tabRowExposesTabSemanticsAndKeepsTouchTargets() {
        assertTrue("tabs must announce as a tab group", tabRow.contains("selectableGroup()"))
        assertTrue("tabs must announce a tab role", tabRow.contains("role = Role.Tab"))
        // 48 dp is the accessibility minimum for a touch target; the row is 56 dp and each tab
        // fills it, so shrinking it below 48 would trade reachability for chrome.
        assertTrue(tabRow.contains("heightIn(min = height)"))
        assertTrue(tabRow.contains("Dp = 56.dp"))
    }
}
