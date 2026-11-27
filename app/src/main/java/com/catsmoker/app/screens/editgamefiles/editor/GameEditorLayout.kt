package com.catsmoker.app.screens.editgamefiles.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.catsmoker.app.ui.components.CardHeading
import com.catsmoker.app.ui.components.SectionCard
import com.catsmoker.app.ui.theme.CatsmokerSpacing
import kotlinx.coroutines.launch

/**
 * Only standalone editors own a scroll container; embedded editors scroll with the picker.
 *
 * Either way this is the frame that publishes [LocalEditorScrollToTop], so an editor switching view
 * can return to the top whichever route it was opened through.
 */
@Composable
internal fun GameEditorLayout(
    standalone: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = if (standalone) rememberScrollState() else null
    val scope = rememberCoroutineScope()
    CompositionLocalProvider(
        LocalEditorScrollToTop provides {
            scroll?.let { state -> scope.launch { state.animateScrollTo(0) } }
            Unit
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().then(
                if (scroll != null) Modifier.verticalScroll(scroll) else Modifier
            ),
            verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap),
            content = content
        )
    }
}

@Composable
internal fun GameEditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeading(title)
            content()
        }
    }
}
