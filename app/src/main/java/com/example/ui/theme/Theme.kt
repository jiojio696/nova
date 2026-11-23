package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NovaDarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = ObsidianVoid,
    primaryContainer = Color(0xFF0C2B47),
    onPrimaryContainer = CyanBright,
    secondary = IndigoPrism,
    onSecondary = ObsidianVoid,
    secondaryContainer = Color(0xFF232954),
    onSecondaryContainer = Color(0xFFC7D2FE),
    tertiary = NovaStatusActive,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = InkPrimaryDark,
    surface = ObsidianSurface,
    onSurface = InkPrimaryDark,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = InkSecondaryDark,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianElevated,
    surfaceContainerHigh = ObsidianCard,
    surfaceContainerHighest = Color(0xFF223152),
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1E293B),
    error = NovaStatusError,
    onError = ObsidianVoid,
    errorContainer = Color(0xFF3E1622),
    onErrorContainer = Color(0xFFFECDD3)
)

private val NovaLightColorScheme = lightColorScheme(
    primary = CyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = IndigoDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF312E81),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = DaylightBg,
    onBackground = InkPrimaryLight,
    surface = DaylightSurface,
    onSurface = InkPrimaryLight,
    surfaceVariant = DaylightElevated,
    onSurfaceVariant = InkSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = DaylightBg,
    surfaceContainer = DaylightElevated,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFD5DFEE),
    outline = DaylightBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337)
)

val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NovaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NovaDarkColorScheme else NovaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NovaTypography,
        shapes = NovaShapes,
        content = content
    )
}
