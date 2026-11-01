package com.catsmoker.app.shared.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.theme.PressPink

/**
 * App-wide filled button. Signature-compatible with Material3 [Button] — the only difference
 * is press feedback: the container turns [PressPink] while the finger is down, so a slow
 * action that hasn't shown its result yet still answers the tap instantly.
 *
 * Disabled buttons never turn pink: a greyed-out control must keep reading as unavailable.
 *
 * When [isLoading] is true the button stops accepting taps and shows a spinner next to
 * [loadingLabel] (default "Working…"). It stays *disabled* for accessibility and for the
 * ripple, but is painted in its normal enabled colours: Material3's disabled container
 * (surface at 12%) leaves the label unreadable in the light theme, where both the surface
 * and `onPrimary` are white.
 */
@Composable
fun CatsmokerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingLabel: String? = null,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading,
        shape = shape,
        colors = buttonColors(colors, enabled = enabled, isPressed = isPressed, isLoading = isLoading),
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = { LoadingAwareContent(isLoading, loadingLabel, content) }
    )
}

/**
 * App-wide outlined button. Same contract as [CatsmokerButton]: transparent at rest, [PressPink]
 * while pressed, never pink when disabled, and normal enabled colours while [isLoading].
 */
@Composable
fun CatsmokerOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingLabel: String? = null,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading,
        shape = shape,
        colors = buttonColors(colors, enabled = enabled, isPressed = isPressed, isLoading = isLoading),
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = { LoadingAwareContent(isLoading, loadingLabel, content) }
    )
}

/**
 * Colours for one button state.
 *
 * While [isLoading] the *disabled* slots carry the enabled colours: the button is disabled
 * (no ripple, no second tap) but must still read as a live control, and the caller's own
 * [colors] — including an error-tinted one — keep applying. The pressed-pink swap stays off
 * while busy so a multi-second operation doesn't sit on a colour meant for a tap.
 */
private fun buttonColors(
    colors: ButtonColors,
    enabled: Boolean,
    isPressed: Boolean,
    isLoading: Boolean
): ButtonColors = when {
    isLoading -> colors.copy(
        disabledContainerColor = colors.containerColor,
        disabledContentColor = colors.contentColor
    )
    isPressed && enabled -> colors.copy(containerColor = PressPink)
    else -> colors
}

/**
 * Button content: the caller's own [content], or the spinner plus [loadingLabel] while busy.
 * No `fillMaxWidth` here — the button's own row already centres its content, and filling would
 * stretch a wrap-content button to full width the moment it starts loading.
 */
@Composable
private fun RowScope.LoadingAwareContent(
    isLoading: Boolean,
    loadingLabel: String?,
    content: @Composable RowScope.() -> Unit
) {
    if (!isLoading) {
        content()
        return
    }
    val contentColor = LocalContentColor.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        InlineLoadingSpinner(color = contentColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = loadingLabel ?: stringResource(R.string.core_working),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}
