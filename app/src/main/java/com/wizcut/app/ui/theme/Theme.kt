package com.wizcut.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Fixed dark scheme: dark gray background with white and gray details.
 *
 * The grays are spaced far enough apart that every control reads clearly:
 * - Unchecked switch: LightGray thumb on a SurfaceVariant track (~6:1 contrast).
 * - Dividers and text field borders: MidGray on the background (~5:1 contrast).
 * - Active controls (button, slider track, checked switch): white on dark.
 */
private val WiZcutColorScheme = darkColorScheme(

    // Primary – white accents: filled button, active slider track,
    // focused text field border and checked switch track.
    primary = White,
    onPrimary = DarkBackground,
    primaryContainer = MidGray,
    onPrimaryContainer = White,

    // Secondary – grays. secondaryContainer also backs the inactive
    // slider track in Material 3, so it matches SurfaceVariant.
    secondary = LightGray,
    onSecondary = DarkBackground,
    secondaryContainer = SurfaceVariant,
    onSecondaryContainer = LightGray,

    // Tertiary
    tertiary = MidGray,
    onTertiary = White,
    tertiaryContainer = DimGray,
    onTertiaryContainer = White,

    // Background
    background = DarkBackground,
    onBackground = White,

    // Surface
    surface = DarkSurface,
    onSurface = White,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = LightGray,

    // Surface variations
    surfaceTint = DarkSurface, // keep elevated surfaces neutral gray
    inverseSurface = LightGray,
    inverseOnSurface = DarkBackground,
    inversePrimary = DarkBackground,

    // Outline – text field borders, dividers, unchecked switch border
    outline = MidGray,
    outlineVariant = DimGray,

    // Other
    scrim = Black,
    error = ErrorLight,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerLight
)

@Composable
fun WiZcutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WiZcutColorScheme,
        typography = Typography,
        content = content
    )
}
