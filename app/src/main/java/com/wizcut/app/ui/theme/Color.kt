package com.wizcut.app.ui.theme

import androidx.compose.ui.graphics.Color

// Light neutrals – accents and details on the dark background
val White = Color(0xFFFFFFFF)      // Primary accent: button, active slider track, checked switch
val LightGray = Color(0xFFC9C9C9)  // Secondary text, unchecked switch thumb
val MidGray = Color(0xFF6E6E6E)    // Outlines, dividers, unchecked switch border
val DimGray = Color(0xFF4A4A4A)    // Subtle outlines (disabled and unfocused states)

// Dark neutrals – background and surfaces
val DarkBackground = Color(0xFF151515)  // App background
val DarkSurface = Color(0xFF232323)     // Cards and elevated surfaces
val SurfaceVariant = Color(0xFF444444)  // Inactive slider track, unchecked switch track

// Semantic
val Black = Color(0xFF000000)
val ErrorLight = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerLight = Color(0xFFF9DEDC)

/**
 * Approximate sRGB color of the light a bulb emits at a given color
 * temperature: low kelvin reads amber, high kelvin reads neutral
 * white. Used to tint the warmth controls with the light they set.
 * Bulb ranges outside 1500–6500 K are clamped to this scale.
 */
fun warmthColor(kelvin: Int): Color {
    val t = ((kelvin - 1500f) / (6500f - 1500f)).coerceIn(0f, 1f)
    val eased = t * t * (3f - 2f * t)

    return Color(
        red = 1f,
        green = 0.66f + 0.34f * eased,
        blue = 0.38f + 0.62f * eased
    )
}
