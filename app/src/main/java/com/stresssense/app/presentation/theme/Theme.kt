package com.stresssense.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Custom color holder for StressSense-specific colors
 * that don't map directly to Material3 color roles.
 */
data class StressSenseColors(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val lowStress: Color,
    val midStress: Color,
    val highStress: Color,
    val accentBlue: Color,
    val accentPurple: Color,
    val cardBackground: Color,
    val divider: Color
)

val LocalStressSenseColors = staticCompositionLocalOf {
    StressSenseColors(
        background = Background,
        surface = SurfaceColor,
        textPrimary = TextPrimary,
        textSecondary = TextSecondary,
        textTertiary = TextTertiary,
        lowStress = LowStress,
        midStress = MidStress,
        highStress = HighStress,
        accentBlue = AccentBlue,
        accentPurple = AccentPurple,
        cardBackground = CardBackground,
        divider = DividerColor
    )
}

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E8FF),
    onPrimaryContainer = TextPrimary,
    secondary = AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E8FF),
    onSecondaryContainer = TextPrimary,
    tertiary = LowStress,
    onTertiary = Color.White,
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceColor,
    onSurface = TextPrimary,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextSecondary,
    outline = DividerColor,
    outlineVariant = Color(0xFFE8E9F0)
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A4A80),
    onPrimaryContainer = DarkTextPrimary,
    secondary = AccentPurple,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF3D4060),
    outlineVariant = Color(0xFF30334D)
)

private val LightStressSenseColors = StressSenseColors(
    background = Background,
    surface = SurfaceColor,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textTertiary = TextTertiary,
    lowStress = LowStress,
    midStress = MidStress,
    highStress = HighStress,
    accentBlue = AccentBlue,
    accentPurple = AccentPurple,
    cardBackground = CardBackground,
    divider = DividerColor
)

private val DarkStressSenseColors = StressSenseColors(
    background = DarkBackground,
    surface = DarkSurface,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textTertiary = Color(0xFF6B6F85),
    lowStress = LowStress,
    midStress = MidStress,
    highStress = HighStress,
    accentBlue = AccentBlue,
    accentPurple = AccentPurple,
    cardBackground = DarkCard,
    divider = Color(0xFF30334D)
)

@Composable
fun StressSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val stressSenseColors = if (darkTheme) DarkStressSenseColors else LightStressSenseColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalStressSenseColors provides stressSenseColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StressSenseTypography,
            shapes = StressSenseShapes,
            content = content
        )
    }
}

/**
 * Convenience accessor for StressSense-specific colors.
 */
object StressSenseTheme {
    val colors: StressSenseColors
        @Composable
        get() = LocalStressSenseColors.current
}
