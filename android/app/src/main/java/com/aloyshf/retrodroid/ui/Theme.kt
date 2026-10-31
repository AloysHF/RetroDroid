package com.aloyshf.retrodroid.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RetroDroidColors = darkColorScheme(
    primary = Color(0xFF5EE1B4),
    onPrimary = Color(0xFF04211A),
    primaryContainer = Color(0xFF14453A),
    onPrimaryContainer = Color(0xFFB8F5E2),
    secondary = Color(0xFF9DB8FF),
    onSecondary = Color(0xFF0B1B3A),
    background = Color(0xFF0F1014),
    onBackground = Color(0xFFE7E9EE),
    surface = Color(0xFF1A1C22),
    onSurface = Color(0xFFE7E9EE),
    surfaceVariant = Color(0xFF252830),
    onSurfaceVariant = Color(0xFFB7BCC8),
    outline = Color(0xFF4A4F5C),
    error = Color(0xFFFF8A80),
)

@Composable
fun RetroDroidTheme(content: @Composable () -> Unit) {
    // RetroDroid is always dark; the emulated image is the bright part.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = RetroDroidColors, content = content)
}
