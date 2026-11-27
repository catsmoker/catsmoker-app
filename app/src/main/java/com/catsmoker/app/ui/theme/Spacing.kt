package com.catsmoker.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The app's vertical rhythm, named instead of retyped per screen.
 *
 * Cards are the unit of this UI, so the gaps around them are design decisions rather than
 * local padding. The Settings General cards once had no gap at all (two `SectionCard`s as
 * direct siblings in a plain `Column`) while other screens hand-inserted 8, 12, 16 or 24 dp,
 * which is how the same visual relationship ended up looking different on every screen.
 * Screens compose cards through [com.catsmoker.app.ui.components.CardGroup], which
 * applies [CardGap]; [SectionGap] and [LabelGap] complete the scale.
 */
object CatsmokerSpacing {
    /**
     * Between two cards of the same group, whether stacked or side by side.
     *
     * 8 dp: two cards already carry 16 dp of their own padding, so the visible band between two
     * blocks of text was 44 dp — wide enough to read as a hole rather than as a group boundary.
     */
    val CardGap = 8.dp

    /** Between two labelled sections on a scrolling screen. */
    val SectionGap = 24.dp

    /** Between a section label and the first card it introduces. */
    val LabelGap = 8.dp
}
