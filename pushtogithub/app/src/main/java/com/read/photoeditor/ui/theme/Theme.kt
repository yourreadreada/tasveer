package com.read.photoeditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// AI Studio Dark Stone Palette
val Stone950 = Color(0xFF0C0A09) // Page canvas background
val Stone900 = Color(0xFF1C1917) // Cards & Elevated surfaces
val Stone850 = Color(0xFF221F1D) // Intermediate surface
val Stone800 = Color(0xFF292524) // Outlines & Dividers
val Stone700 = Color(0xFF44403C) // Secondary borders & pill outlines
val Stone600 = Color(0xFF57534E) // Muted icons
val Stone500 = Color(0xFF78716C) // Muted labels & icons
val Stone400 = Color(0xFFA8A29E) // Subtext
val Stone300 = Color(0xFFD6D3D1) // Secondary headers
val Stone200 = Color(0xFFE7E5E4) // Body text
val Stone100 = Color(0xFFF5F5F4) // Primary headings
val Stone50 = Color(0xFFFAFAF9)  // Crisp white accents

// Brand & Accent Colors
val Amber500 = Color(0xFFF59E0B) // Primary action & selection
val Amber400 = Color(0xFFFBBF24) // Light amber hover & "Edit Look →"
val Amber300 = Color(0xFFFCD34D) // Bright amber text
val Orange600 = Color(0xFFEA580C) // Tasveer brand camera badge
val Orange500 = Color(0xFFF97316) // Gradient middle
val Amber600 = Color(0xFFD97706) // Gradient start
val Emerald400 = Color(0xFF34D399) // Dataset / Training log green
val Red400 = Color(0xFFF87171)     // Danger / Delete red

val TasveerDarkColorScheme = darkColorScheme(
    background = Stone950,
    surface = Stone900,
    surfaceVariant = Stone800,
    primary = Amber500,
    onPrimary = Stone950,
    secondary = Orange600,
    onSecondary = Color.White,
    tertiary = Amber400,
    onTertiary = Stone950,
    onBackground = Stone100,
    onSurface = Stone100,
    onSurfaceVariant = Stone400,
    outline = Stone800,
    outlineVariant = Stone700
)

@Composable
fun TasveerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TasveerDarkColorScheme,
        content = content
    )
}
