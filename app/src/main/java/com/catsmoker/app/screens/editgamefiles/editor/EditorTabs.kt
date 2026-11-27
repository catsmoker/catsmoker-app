package com.catsmoker.app.screens.editgamefiles.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.catsmoker.app.ui.components.SectionTab
import com.catsmoker.app.ui.components.SectionTabRow

/**
 * The scroll owner's "back to the top" seam, for editors that switch view under the user.
 *
 * Switching a tab replaces the content, and the scroll offset belongs to the destination, not to
 * the editor: without this, picking QUALITY while scrolled to the bottom left the user staring at
 * the last card of the previous view. A local rather than a parameter because the offset lives in
 * `EditGameFilesScreen` for embedded editors and in `GameEditorLayout` for standalone routes, and
 * threading a callback through every editor signature would put scroll plumbing in three screens
 * that have no other interest in it. A no-op default keeps standalone previews working.
 */
val LocalEditorScrollToTop = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * One view of an embedded editor.
 *
 * Three long editors (GRID, Star Rail, Wuthering Waves) put every knob on one scrolling form, which
 * is how a preference changed at the top came to need five swipes to reach APPLY. The bar fixed
 * the apply; these tabs fix the reach, by showing one group of cards at a time and letting the
 * pinned bar keep the write one tap away from every group.
 *
 * [id] is the enum name, not the label, so a translation can never reset an open tab.
 */
data class EditorTabSpec(val id: String, val label: String, val icon: ImageVector)

/**
 * The tab row plus its saved selection.
 *
 * Returns the selected id; the caller keeps a `when` over its own enum. Choosing a tab also returns
 * the scroll container to the top, because the new group is shorter than the old one often enough
 * that carrying the offset over lands mid-card.
 */
@Composable
fun rememberEditorTab(tabs: List<EditorTabSpec>, default: String): EditorTabState {
    var selected by rememberSaveable { mutableStateOf(default) }
    val scrollToTop = LocalEditorScrollToTop.current
    return EditorTabState(
        selected = selected,
        row = {
            SectionTabRow(
                sections = tabs.map { SectionTab(it.id, it.label, it.icon) },
                selectedId = selected,
                onSelect = { tab ->
                    if (tab.id != selected) {
                        selected = tab.id
                        scrollToTop()
                    }
                }
            )
        }
    )
}

/** The selected id and a slot that draws the switcher; split so the row can sit above the cards. */
class EditorTabState(
    val selected: String,
    val row: @Composable () -> Unit
)
