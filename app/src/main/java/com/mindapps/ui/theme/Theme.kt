package com.mindapps.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Pure black and white - no gray, no gradients
private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = Black,
    onPrimary = White,
    primaryContainer = Black,
    onPrimaryContainer = White,
    secondary = Black,
    onSecondary = White,
    secondaryContainer = White,
    onSecondaryContainer = Black,
    tertiary = Black,
    onTertiary = White,
    tertiaryContainer = White,
    onTertiaryContainer = Black,
    error = Black,
    onError = White,
    errorContainer = White,
    onErrorContainer = Black,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    surfaceVariant = White,
    onSurfaceVariant = Black,
    outline = Black,
    outlineVariant = Black,
    scrim = Black,
    inverseSurface = Black,
    inverseOnSurface = White,
    inversePrimary = White,
    surfaceDim = White,
    surfaceBright = White,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = White,
    surfaceContainerHighest = White
)

private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    primaryContainer = White,
    onPrimaryContainer = Black,
    secondary = White,
    onSecondary = Black,
    secondaryContainer = Black,
    onSecondaryContainer = White,
    tertiary = White,
    onTertiary = Black,
    tertiaryContainer = Black,
    onTertiaryContainer = White,
    error = White,
    onError = Black,
    errorContainer = Black,
    onErrorContainer = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Black,
    onSurfaceVariant = White,
    outline = White,
    outlineVariant = White,
    scrim = White,
    inverseSurface = White,
    inverseOnSurface = Black,
    inversePrimary = Black,
    surfaceDim = Black,
    surfaceBright = Black,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Black,
    surfaceContainer = Black,
    surfaceContainerHigh = Black,
    surfaceContainerHighest = Black
)

@Composable
fun MindAppsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
