package com.wizcut.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,

    secondary = LightGray,
    onSecondary = Black,

    tertiary = Gray,
    onTertiary = Black,

    background = DarkBackground,
    onBackground = White,

    surface = DarkSurface,
    onSurface = White,

    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = LightGray,

    outline = Gray
)

@Composable
fun WiZcutTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}