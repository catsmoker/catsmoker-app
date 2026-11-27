package com.catsmoker.app.screens.editgamefiles.editor

import com.catsmoker.app.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catsmoker.app.ui.components.CatsmokerButton

/**
 * What the persistent deploy bar draws, as plain data.
 *
 * Deliberately primitives only: the host publishes this on every composition and compares it
 * with the previous value, so equality has to mean "the bar would look the same". Callbacks
 * live in [DeployActions] instead, because a freshly allocated lambda is never equal to the
 * previous one and would republish on every frame.
 */
data class DeployView(
    /** Label of the primary write, e.g. "APPLY TO GAME". */
    val applyLabel: String,
    val dirty: Boolean,
    val busy: Boolean,
    val pendingCount: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /**
     * False for an editor whose working copy has no undo history (Wuthering Waves generates a
     * config rather than editing a copy). Two permanently disabled arrows would be furniture that
     * teaches the user the bar does things it does not.
     */
    val showHistory: Boolean = true
)

/**
 * The bar's callbacks, read at tap time rather than stored in composition state so a new lambda
 * identity can never invalidate the bar.
 */
class DeployActions(
    val apply: () -> Unit,
    val undo: () -> Unit = {},
    val redo: () -> Unit = {}
)

/**
 * The seam between an embedded editor and the screen that owns the scroll container.
 *
 * The per-game editors are composed inside File Engineering's single `verticalScroll`, and their
 * apply button used to sit wherever their "DEPLOY" card fell — measured at roughly 40% of a
 * 5-swipe form, so changing one chip near the top meant scrolling most of the screen to apply it.
 * A bar pinned to the bottom cannot live inside the scroll, but the state it shows belongs to the
 * editor's own ViewModel, so the editor *publishes* what the bar should say and the host renders
 * it outside the scroll.
 *
 * `null` in [view] means "no editor is publishing", and the bar disappears. That matters when the
 * selected game changes: the editor is removed from the tree and its `onDispose` clears the bar,
 * so a stale APPLY for the previous game can never be pressed.
 */
@Stable
class DeployHost {
    /** Read at tap time; never part of the published state. */
    internal var actions: DeployActions? = null

    var view: DeployView? by mutableStateOf(null)
        private set

    fun publish(view: DeployView?, actions: DeployActions? = null) {
        this.actions = actions
        // Equality guard: publishing the same view from a recomposition must not invalidate the
        // reader, or the host and the bar would keep waking each other up.
        if (this.view != view) this.view = view
    }
}

/**
 * Present only while an editor that supports the bar is embedded. A standalone editor route has
 * no host and draws its own deploy block inline, exactly as before.
 */
val LocalDeployHost = staticCompositionLocalOf<DeployHost?> { null }

/**
 * Publishes [view] to the enclosing [DeployHost], and draws [inline] when there is no host.
 *
 * [inline] is the editor's original deploy block. Keeping it means the standalone routes and the
 * Compose previews still render a complete editor, and the bar is an additional surface rather
 * than the only way to apply.
 */
@Composable
fun HostedDeploy(
    view: DeployView,
    actions: DeployActions,
    inline: @Composable () -> Unit
) {
    val host = LocalDeployHost.current
    if (host == null) {
        inline()
        return
    }
    // SideEffect runs after a successful composition: publishing during composition would write
    // state a parent may already have read in the same frame.
    SideEffect { host.publish(view, actions) }
    DisposableEffect(host) {
        onDispose { host.publish(null, null) }
    }
}

/**
 * The pinned bar: pending state, undo/redo for the working copy, and the one primary write.
 *
 * The primary button stays disabled while nothing is pending — the inline card said "No unsaved
 * changes." next to a dead button, and the bar can say the same thing in the same place. It is
 * drawn above the navigation bar inset rather than under it, and it is a `Surface`, so content
 * scrolling beneath it is visibly separated instead of showing through.
 */
@Composable
fun DeployBar(host: DeployHost, modifier: Modifier = Modifier) {
    val view = host.view ?: return
    val pendingColor = Color(0xFFFFB74D)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    // Vertical padding stays small so the 48 dp undo/redo targets and the button
                    // are not coerced smaller by a tight bar: at 8 dp the icons measured 40 dp.
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .defaultMinSize(minHeight = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (view.dirty) {
                            stringResource(R.string.gf_unsaved)
                        } else {
                            stringResource(R.string.gf_no_pending_changes)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (view.dirty) pendingColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (view.dirty && view.pendingCount > 0) {
                        Text(
                            text = stringResource(R.string.gf_hsr_pending_count, view.pendingCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = pendingColor
                        )
                    }
                }
                if (view.showHistory) {
                IconButton(
                    onClick = { host.actions?.undo?.invoke() },
                    enabled = view.canUndo && !view.busy
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.gf_undo),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { host.actions?.redo?.invoke() },
                    enabled = view.canRedo && !view.busy
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.gf_redo),
                        modifier = Modifier.size(20.dp)
                    )
                }
                }
                CatsmokerButton(
                    onClick = { host.actions?.apply?.invoke() },
                    enabled = view.dirty && !view.busy,
                    isLoading = view.busy,
                    // The bar is one row: the button gives up its generous horizontal padding so
                    // the pending-state line beside it gets the width instead of wrapping.
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    loadingLabel = stringResource(R.string.gf_applying)
                ) {
                    Text(view.applyLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
