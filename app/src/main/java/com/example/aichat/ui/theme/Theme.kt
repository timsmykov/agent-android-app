package com.example.aichat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF5B6CFF),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFFF4FD8),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF0E0F12),
    onBackground = Color(0xFFECECEC),
    surface = Color(0x331FFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0x1A5B6CFF),
    onSurfaceVariant = Color(0xFFB8C1FF),
    error = Color(0xFFFF4757),
    onError = Color(0xFFFFFFFF)
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12),
    small = androidx.compose.foundation.shape.RoundedCornerShape(16),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28)
)

@Composable
fun AIChatTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = DarkPalette
    val view = LocalView.current
    val systemUiController = rememberSystemUiController()

    if (!view.isInEditMode) {
        SideEffect {
            systemUiController.setStatusBarColor(colorScheme.background, darkIcons = false)
            systemUiController.setNavigationBarColor(colorScheme.background, darkIcons = false)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = AppShapes,
        content = content
    )
}
