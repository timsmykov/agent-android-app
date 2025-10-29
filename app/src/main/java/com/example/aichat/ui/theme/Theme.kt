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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    onPrimary = Color(0xFF0B0618),
    secondary = Color(0xFFFF6FDB),
    onSecondary = Color(0xFF0B0618),
    tertiary = Color(0xFF5BE7FF),
    onTertiary = Color(0xFF00131A),
    background = Color(0xFF05060B),
    onBackground = Color(0xFFE6E6F3),
    surface = Color(0xFF11121E),
    onSurface = Color(0xFFE7E9FE),
    surfaceVariant = Color(0x33223E7A),
    onSurfaceVariant = Color(0xFFB4C5FF),
    error = Color(0xFFFF596A),
    onError = Color(0xFF2B0406)
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
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

private val BaseTypography = androidx.compose.material3.Typography()

private val AppTypography = Typography(
    headlineLarge = BaseTypography.headlineLarge.copy(
        fontWeight = FontWeight.SemiBold,
        lineHeight = 36.sp
    ),
    headlineMedium = BaseTypography.headlineMedium.copy(
        fontWeight = FontWeight.Medium,
        lineHeight = 30.sp
    ),
    titleLarge = BaseTypography.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp
    ),
    bodyLarge = BaseTypography.bodyLarge.copy(
        lineHeight = 22.sp
    ),
    bodyMedium = BaseTypography.bodyMedium.copy(
        lineHeight = 20.sp
    ),
    labelLarge = BaseTypography.labelLarge.copy(
        fontWeight = FontWeight.Medium
    )
)
