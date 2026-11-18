package com.catsmoker.app.ui.components

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight

/**
 * Adds a static reflection above the Material background and behind the content. Sampling
 * only the reflection keeps text and ripples sharp and preserves the original surface colors.
 */
@Composable
internal fun Modifier.liquidGlassFinish(
    shape: RoundedCornerShape,
    enabled: Boolean = true,
    showRim: Boolean = true,
    reflectionColor: Color = MaterialTheme.colorScheme.primary
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        LocalInspectionMode.current || !LocalView.current.isHardwareAccelerated
    ) {
        return this
    }

    val glow = reflectionColor.copy(alpha = if (enabled) 0.12f else 0.05f)
    val backdrop = rememberCanvasBackdrop {
        drawRect(
            Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = Offset(
                    x = size.width * 0.22f,
                    y = 0f
                ),
                radius = (size.width * 0.85f).coerceAtLeast(1f)
            )
        )
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            // Short windows can squeeze chart and card contents below the lens size.
            if (size.minDimension >= 24.dp.toPx()) {
                blur(3.dp.toPx())
                lens(4.dp.toPx(), 8.dp.toPx())
            }
        },
        highlight = if (showRim) ({ Highlight(width = 1.dp, alpha = if (enabled) 0.55f else 0.25f) }) else null,
        shadow = null
    )
}
