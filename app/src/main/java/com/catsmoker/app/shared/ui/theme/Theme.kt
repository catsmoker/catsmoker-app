package com.catsmoker.app.shared.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NothingColorScheme = darkColorScheme(
    primary = AccentPrimary,        // Nothing Red
    secondary = NothingWhite,
    tertiary = AccentTertiary,      // Nothing Grey Light
    background = NothingBlack,
    surface = NothingGrey,
    surfaceVariant = NothingGreyLight,
    onPrimary = NothingWhite,
    onSecondary = NothingBlack,
    onTertiary = NothingWhite,
    onBackground = NothingWhite,
    onSurface = NothingWhite,
    onSurfaceVariant = NothingWhite.copy(alpha = 0.7f),
    outline = NothingWhite.copy(alpha = 0.12f)
)

/**
 * The Nothing look inverted: paper background, ink text, same red accent.
 *
 * Every hardcoded `Color.White/Gray/Black` body/border color in the screens was migrated to
 * scheme roles (`onSurface`, `onSurfaceVariant`, `outline`…), so both schemes read correctly.
 * Intentionally fixed colors (NothingRed accents, green/amber/red status, black terminal
 * boxes and scrims, chart data colors) are untouched in both themes.
 */
private val NothingLightScheme = lightColorScheme(
    primary = AccentPrimary,        // Nothing Red stays the brand in both themes
    secondary = Color(0xFF111111),
    tertiary = Color(0xFF5A5A5A),
    background = Color(0xFFF4F4F4),
    surface = NothingWhite,
    surfaceVariant = Color(0xFFE9E9E9),
    onPrimary = NothingWhite,
    onSecondary = NothingWhite,
    onTertiary = NothingWhite,
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF5A5A5A),
    outline = Color.Black.copy(alpha = 0.12f)
)

/**
 * Opt-in Material You palette. `dynamicColor` is true only when the user picked the
 * Dynamic theme option: on Android 12+ the scheme comes from the wallpaper, below that
 * (or when off) the fixed Nothing brand schemes apply — never a silent mix.
 */
@Composable
fun CatsmokerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> NothingColorScheme
        else -> NothingLightScheme
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            val controller = WindowCompat.getInsetsController(window, view)
            // Dark icons on the light theme, white icons on dark — edge-to-edge either way.
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme

            // Ensure edge-to-edge
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
