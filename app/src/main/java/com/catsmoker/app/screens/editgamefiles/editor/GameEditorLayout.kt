package com.catsmoker.app.screens.editgamefiles.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.catsmoker.app.ui.components.CardHeading
import com.catsmoker.app.ui.components.SectionCard
import com.catsmoker.app.ui.theme.CatsmokerSpacing

/** Only standalone editors own a scroll container; embedded editors scroll with the picker. */
@Composable
internal fun GameEditorLayout(
    standalone: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().then(
            if (standalone) Modifier.verticalScroll(rememberScrollState()) else Modifier
        ),
        verticalArrangement = Arrangement.spacedBy(CatsmokerSpacing.CardGap),
        content = content
    )
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
