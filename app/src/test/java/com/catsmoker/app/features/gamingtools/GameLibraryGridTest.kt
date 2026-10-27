package com.catsmoker.app.features.gamingtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks the launcher-style "Your Games" grid.
 *
 * The previous layout was a vertical list whose row expanded into three buttons. It was compact
 * per row but still one row per game, and the actions were far from the icon that identifies the
 * game. The grid shows the library as icons (the object the user recognises), and the icon opens a
 * popup with the three actions. These tests pin the two decisions that are easy to regress: the
 * layout must stay a plain chunked grid inside the already-scrolling screen, and the popup must
 * remain the only place actions live.
 */
class GameLibraryGridTest {

    private val screen = File(
        "src/main/java/com/catsmoker/app/features/gamingtools",
        "GamingToolsScreen.kt"
    ).readText()

    /** Only the tile composable: counting menu items across the whole screen would match other menus. */
    private val tile = screen
        .substringAfter("fun GameLibraryTile(")
        .substringBefore("\n@Composable")

    @Test
    fun libraryIsAPlainGridInsideTheScrollableScreen() {
        assertTrue(screen.contains("private const val GAME_GRID_COLUMNS = 4"))
        assertTrue(screen.contains("private const val GAME_LIBRARY_PREVIEW = GAME_GRID_COLUMNS * 2"))
        assertTrue(screen.contains("visibleGames.chunked(GAME_GRID_COLUMNS)"))
        // LazyVerticalGrid inside this screen's verticalScroll would be measured with infinite
        // height; the grid must stay a chunked set of Rows.
        assertFalse(screen.contains("LazyVerticalGrid("))
        // The last, incomplete row keeps the same column widths.
        assertTrue(screen.contains("repeat(GAME_GRID_COLUMNS - rowGames.size)"))
        // Inline expansion is gone: the actions live in the popup only.
        assertFalse(screen.contains("expandedGamePkg"))
        assertFalse(screen.contains("AnimatedVisibility(visible = isExpanded)"))
        assertFalse(screen.contains("GameLibraryCard("))
    }

    @Test
    fun theGameIconIsTheButtonAndTheNameIsJustALabel() {
        assertTrue(tile.contains("IconButton(onClick = onToggleMenu"))
        assertTrue(tile.contains("Modifier.size(56.dp)"))
        assertTrue(tile.contains("R.string.gt_library_actions"))
        // No clickable wrapper around the name: the icon is the control.
        assertFalse(tile.contains(".clickable"))
        assertTrue(tile.contains("text = game.appName"))
        assertTrue(tile.contains("textAlign = TextAlign.Center"))
        assertTrue(tile.contains("minLines = 2"))
        assertTrue(tile.contains("maxLines = 2"))
    }

    @Test
    fun thePopupOffersExactlyTheThreeExistingActions() {
        assertTrue(tile.contains("DropdownMenu(expanded = isMenuOpen"))
        assertEquals(
            "the popup must offer exactly Launch, Pre-compile and Remove",
            3,
            Regex("DropdownMenuItem\\(").findAll(tile).count()
        )
        listOf("gt_library_launch", "gt_library_optimize", "gt_library_remove").forEach { key ->
            assertTrue("popup lost $key", tile.contains(key))
        }
        // Actions stay addressed by package name, never by label.
        assertTrue(tile.contains("onLaunch(game.packageName)"))
        assertTrue(tile.contains("onOptimize(game.packageName)"))
        assertTrue(tile.contains("onRemove(game.packageName)"))
        // Remove keeps its destructive styling.
        assertTrue(tile.contains("MaterialTheme.colorScheme.error"))
    }

    @Test
    fun onlyOnePopupCanBeOpenAtATime() {
        assertTrue(screen.contains("var openGameMenuPkg by rememberSaveable"))
        assertTrue(screen.contains("isMenuOpen = openGameMenuPkg == game.packageName"))
        assertTrue(screen.contains("onDismissMenu = { openGameMenuPkg = null }"))
        assertTrue(tile.contains("onDismissRequest = onDismissMenu"))
        // Every action closes the popup before running, so a returning dialog cannot sit under a
        // stale menu.
        assertEquals(
            "each of the three actions must dismiss before it acts",
            3,
            Regex("onDismissMenu\\(\\)").findAll(tile).count()
        )
    }

    @Test
    fun theCompileActionStillReportsItsSpinner() {
        assertTrue(tile.contains("isOptimizing"))
        assertTrue(tile.contains("CircularProgressIndicator("))
        assertTrue(tile.contains("enabled = !isOptimizing"))
    }
}
