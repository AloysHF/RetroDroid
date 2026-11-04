package com.aloyshf.retrodroid.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.aloyshf.retrodroid.data.ThemeMode

private val RetroDroidDarkColors = darkColorScheme(
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

private val RetroDroidLightColors = lightColorScheme(
    primary = Color(0xFF0E7C63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F5E2),
    onPrimaryContainer = Color(0xFF04211A),
    secondary = Color(0xFF3D5FA8),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F8F7),
    onBackground = Color(0xFF15181C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15181C),
    surfaceVariant = Color(0xFFE2E8E5),
    onSurfaceVariant = Color(0xFF454B52),
    outline = Color(0xFF9AA3A9),
    error = Color(0xFFB3261E),
)

@Composable
fun RetroDroidTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) RetroDroidDarkColors else RetroDroidLightColors,
        content = content,
    )
}
