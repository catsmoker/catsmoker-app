package com.catsmoker.app.features.gamingtools.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.app.R

/**
 * The Advanced group's fold rule, its animation length and its chevron angle, kept pure so they can
 * be tested without a device.
 *
 * Only presentation lives here. The two pickers inside the group own their own stored sets and
 * callbacks, so folding the group can never add, remove or freeze an app — the same rule the
 * pickers' own disclosures follow.
 */
internal object GamingModeAdvanced {
    /** Closed until the user opens it; the remembered value comes from the store. */
    const val DEFAULT_EXPANDED = false

    /** Long enough to read as a movement, short enough not to delay the tap. */
    const val DURATION_MS = 200

    /** One `ExpandMore` glyph flipped upside down, so there is only ever one icon to ship. */
    private const val CHEVRON_HALF_TURN = 180f

    /** Flips only the disclosure state. */
    fun toggle(expanded: Boolean): Boolean = !expanded

    /** `0°` points the chevron down (closed), `180°` points it up (open). */
    fun chevronDegrees(expanded: Boolean): Float = if (expanded) CHEVRON_HALF_TURN else 0f
}

/**
 * One collapsed-by-default "Advanced" block for the Gaming Mode card.
 *
 * The card's two app pickers ("Apps kept running", "Extra suspended apps") are configuration the
 * user sets once and then leaves alone, yet they were the tallest thing on the card. Behind this
 * disclosure the power button, the automatic-mode switch and the read-back stay on one screen, and
 * the pickers are one tap away. The whole header is the tap target, not just the arrow.
 *
 * The expanded flag is hoisted because the caller persists it — the group opens in the same shape
 * after leaving the screen or restarting the app. Errors and refusals deliberately do not live
 * inside this block, for the same reason [GamingModeDetailsSection] keeps them outside its fold: a
 * collapsed section must never hide why something failed.
 *
 * @param expanded whether the group's content is shown. The chevron rotates to match.
 * @param onToggle flips the disclosure; it must only persist that flag, never run an optimization.
 * @param content the group's body, composed only while [expanded] is true.
 */
@Composable
fun GamingModeAdvancedSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    // The label names the action the tap performs, so TalkBack announces "Expand advanced
    // settings" while closed and "Collapse advanced settings" while open.
    val actionLabel = stringResource(
        if (expanded) R.string.gt_advanced_collapse else R.string.gt_advanced_expand
    )
    val chevronRotation by animateFloatAsState(
        targetValue = GamingModeAdvanced.chevronDegrees(expanded),
        animationSpec = tween(durationMillis = GamingModeAdvanced.DURATION_MS),
        label = "advancedChevron"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.08f))
                .clickable(
                    onClickLabel = actionLabel,
                    role = Role.Button,
                    onClick = onToggle
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.gt_advanced_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = stringResource(R.string.gt_advanced_sub),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = actionLabel,
                tint = accent,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = GamingModeAdvanced.DURATION_MS)
            ) + fadeIn(animationSpec = tween(durationMillis = GamingModeAdvanced.DURATION_MS)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = GamingModeAdvanced.DURATION_MS)
            ) + fadeOut(animationSpec = tween(durationMillis = GamingModeAdvanced.DURATION_MS))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
    }
}
