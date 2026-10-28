package com.catsmoker.app.shared.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.app.R

/**
 * One piece of copy inside an [InfoButton] sheet.
 *
 * Resources are carried as ids, never as resolved strings, so a language switch repaints the
 * sheet in the new language instead of leaving the old one behind. [Formatted] exists for the
 * handful of answers that name a measured value (the screen source, a DNS provider's addresses).
 */
sealed interface InfoText {
    /** A plain string resource. */
    data class Res(@StringRes val id: Int) : InfoText

    /** A string resource with format arguments, resolved at composition. */
    data class Formatted(@StringRes val id: Int, val args: List<Any>) : InfoText

    /** Already-resolved copy, for values the catalog cannot know in advance. */
    data class Literal(val text: String) : InfoText
}

/** How a question reads: ordinary information, or a warning about a cost/risk. */
enum class InfoTone {
    Neutral,
    Caution
}

/**
 * One question and its answer, as shown inside the sheet.
 *
 * [title] is the original question, kept exactly as it was written. [paragraphs] are the original
 * answer lines in their original order. [tone] only changes colour — it never changes the text.
 */
data class InfoSection(
    val title: InfoText,
    val paragraphs: List<InfoText>,
    val tone: InfoTone = InfoTone.Neutral
)

/**
 * Everything one feature has to say about itself.
 *
 * [name] is the feature's own title, used for the button's accessible name ("More info about
 * Resolution") and for the sheet heading. [sections] keeps several questions together, so a
 * feature with "What is this?" and "Is it safe?" still has exactly one button.
 */
data class InfoTopic(
    val name: InfoText,
    val sections: List<InfoSection>
) {
    /**
     * True when any answer is about a cost, risk or safety limit. The button then uses the amber
     * warning triangle; everything else uses the neutral info icon.
     */
    val isCaution: Boolean
        get() = sections.any { it.tone == InfoTone.Caution }
}

/**
 * Small question-mark-style info control that opens the feature's questions in a bottom sheet.
 *
 * The glyph is 20 dp, but the [IconButton] keeps the 48 dp touch target required on touch screens.
 * The icon is a warning triangle only when the topic carries a risk/safety answer, so a feature
 * like "Is it safe?" is visually distinguishable from a plain explanation.
 *
 * Sheets are created only while open, so a screen with a dozen of these costs nothing until one is
 * tapped. Tapping the scrim, the system back gesture, or the close button dismisses it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoButton(
    topic: InfoTopic,
    modifier: Modifier = Modifier
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val featureName = topic.name.resolve()

    IconButton(
        onClick = { open = true },
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            imageVector = if (topic.isCaution) Icons.Filled.Warning else Icons.Outlined.Info,
            contentDescription = stringResource(R.string.info_more_about, featureName),
            tint = if (topic.isCaution) InfoCautionColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }

    if (open) {
        InfoSheet(topic = topic, onDismiss = { open = false })
    }
}

/**
 * The sheet behind every [InfoButton].
 *
 * The feature title heads the sheet, each original question is a section title, and each answer
 * keeps its paragraphs. A close button sits beside the heading; the scrim and back gesture are
 * handled by [ModalBottomSheet] itself. Long answers scroll rather than clipping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(
    topic: InfoTopic,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = topic.name.resolve(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.info_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            topic.sections.forEachIndexed { index, section ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                val accent = if (section.tone == InfoTone.Caution) {
                    InfoCautionColor
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Text(
                    text = section.title.resolve(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    section.paragraphs.forEach { paragraph ->
                        Text(
                            text = paragraph.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = 21.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoText.resolve(): String = when (this) {
    is InfoText.Res -> stringResource(id)
    is InfoText.Formatted -> stringResource(id, *args.toTypedArray())
    is InfoText.Literal -> text
}

/** The amber the whole app already uses for "this has a cost". */
private val InfoCautionColor = Color(0xFFFFB300)
