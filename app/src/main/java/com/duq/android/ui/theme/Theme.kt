package com.duq.android.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Duq color palette - clean, modern, intelligent
object DuqColors {
    // Primary - Cyan/Teal (AI intelligence)
    val primary = Color(0xFF00D4FF)
    val primaryVariant = Color(0xFF00A8CC)
    val primaryDark = Color(0xFF007A99)

    // Accent - Soft purple (creativity)
    val accent = Color(0xFF9D4EDD)
    val accentLight = Color(0xFFBB86FC)

    // Background
    val background = Color(0xFF0A0A0F)
    val surface = Color(0xFF12121A)
    val surfaceVariant = Color(0xFF1A1A24)
    val surfaceElevated = Color(0xFF22222E)

    // Text
    val textPrimary = Color(0xFFF5F5F5)
    val textSecondary = Color(0xFFB0B0B0)
    val textTertiary = Color(0xFF707070)

    // Status colors
    val success = Color(0xFF00E676)
    val warning = Color(0xFFFFB74D)
    val error = Color(0xFFFF5252)

    // State colors for Duq orb
    val idle = primary
    val listening = Color(0xFF00E5FF)
    val processing = Color(0xFFFFAB40)
    val speaking = success
    val errorState = error
}

private val DuqColorScheme = darkColorScheme(
    primary = DuqColors.primary,
    secondary = DuqColors.accent,
    tertiary = DuqColors.accentLight,
    background = DuqColors.background,
    surface = DuqColors.surface,
    surfaceVariant = DuqColors.surfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = DuqColors.textPrimary,
    onSurface = DuqColors.textPrimary,
    error = DuqColors.error,
    outline = Color(0xFF2A2A36)
)

@Composable
fun DuqAndroidTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DuqColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
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
