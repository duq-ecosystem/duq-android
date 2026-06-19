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

/**
 * DUQ Duck Color Palette 2026
 * Cyberpunk aesthetic with rubber duck branding
 */
object DuqColors {
    // Primary - Duck Yellow (brand color)
    val primary = Color(0xFFFFD60A)
    val primaryBright = Color(0xFFFFE84D)
    val primaryDim = Color(0xFFC9A800)
    val primaryDark = Color(0xFF8B7500)

    // Accent - Orange Beak
    val accent = Color(0xFFFF8C00)
    val accentDim = Color(0xFFCC7000)
    val accentDark = Color(0xFF994D00)

    // Background - Cyberpunk Dark
    val background = Color(0xFF0A0A0A)
    val surface = Color(0xFF0F0F0F)
    val surfaceVariant = Color(0xFF141414)
    val surfaceElevated = Color(0xFF1A1A1A)

    // Glow effects (for cyberpunk aesthetic)
    val glowYellow = Color(0x15FFD60A)      // 8% yellow
    val glowYellowMedium = Color(0x26FFD60A) // 15% yellow
    val glowYellowStrong = Color(0x40FFD60A) // 25% yellow

    // Glass effect (subtle white overlay)
    val glassSurface = Color(0x0DFFFFFF)     // 5% white
    val glassBorder = Color(0x1AFFFFFF)      // 10% white
    val glassHighlight = Color(0x33FFFFFF)   // 20% white

    // Text
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFA0A0A0)  // Alias for compatibility
    val textDim = Color(0xFFA0A0A0)
    val textTertiary = Color(0xFF666666)   // Alias for compatibility
    val textMuted = Color(0xFF666666)

    // Status colors
    val success = Color(0xFF10B981)
    val warning = Color(0xFFF59E0B)
    val error = Color(0xFFEF4444)

    // AI State colors
    val idle = primary                        // Yellow - ready
    val listening = primaryBright             // Bright yellow - active
    val processing = accent                   // Orange - thinking
    val speaking = success                    // Green - output
    val errorState = error                    // Red - error

    // AI Confidence indicators
    val aiConfident = success                 // Green - high confidence
    val aiModerate = primary                  // Yellow - moderate
    val aiUncertain = accent                  // Orange - low confidence

    // Streaming text cursor
    val cursorBlink = primary
}

private val DuqColorScheme = darkColorScheme(
    primary = DuqColors.primary,
    secondary = DuqColors.accent,
    tertiary = DuqColors.primaryBright,
    background = DuqColors.background,
    surface = DuqColors.surface,
    surfaceVariant = DuqColors.surfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = DuqColors.textPrimary,
    onSurface = DuqColors.textPrimary,
    error = DuqColors.error,
    outline = Color(0xFF2A2A2A)
)

@Composable
fun DuqAndroidTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DuqColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
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
