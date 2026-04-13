package com.jarvis.android.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Iron Man color scheme - black with red accents
private val IronManColorScheme = darkColorScheme(
    primary = Color(0xFFE62429),           // Iron Man red
    secondary = Color(0xFFFF3B3B),         // Lighter red accent
    tertiary = Color(0xFFFFD700),          // Gold accent
    background = Color(0xFF0D0D0D),        // Deep black
    surface = Color(0xFF1A1A1A),           // Dark gray
    surfaceVariant = Color(0xFF252525),    // Slightly lighter
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFCF6679),
    outline = Color(0xFF3D3D3D)            // Subtle borders
)

@Composable
fun JarvisAndroidTheme(
    content: @Composable () -> Unit
) {
    // Always use Iron Man dark theme
    val colorScheme = IronManColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Black status bar and navigation bar for immersive look
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
