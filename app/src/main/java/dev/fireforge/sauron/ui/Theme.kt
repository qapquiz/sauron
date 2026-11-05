package dev.fireforge.sauron.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The four DMG-01 Game Boy screen colors, lightest to darkest. Every color
// in the scheme is one of these four or a blend of two of them, so the app
// reads as one green LCD.
private const val LCD = 0xFF9BBC0F // lightest: the screen background
private const val LCD_LIGHT = 0xFF8BAC0F // light green: filled surfaces
private const val LCD_DARK = 0xFF306230 // dark green: secondary text, outlines
private const val LCD_DARKEST = 0xFF0F380F // darkest: the pixels

private fun gb(argb: Long) = Color(argb)

/** Halfway blend of two palette members, staying inside the green family. */
private fun blend(a: Color, b: Color) = Color(
    red = (a.red + b.red) / 2f,
    green = (a.green + b.green) / 2f,
    blue = (a.blue + b.blue) / 2f,
    alpha = 1f,
)

private val GameBoyColors = darkColorScheme(
    primary = gb(LCD_DARKEST),
    onPrimary = gb(LCD),
    primaryContainer = gb(LCD_DARK),
    onPrimaryContainer = gb(LCD),
    secondary = gb(LCD_DARK),
    onSecondary = gb(LCD),
    secondaryContainer = gb(LCD_LIGHT),
    onSecondaryContainer = gb(LCD_DARKEST),
    tertiary = gb(LCD_DARK),
    onTertiary = gb(LCD),
    tertiaryContainer = gb(LCD_LIGHT),
    onTertiaryContainer = gb(LCD_DARKEST),
    background = gb(LCD),
    onBackground = gb(LCD_DARKEST),
    surface = gb(LCD),
    onSurface = gb(LCD_DARKEST),
    surfaceVariant = gb(LCD_LIGHT),
    onSurfaceVariant = gb(LCD_DARK),
    surfaceDim = blend(gb(LCD), gb(LCD_DARK)),
    surfaceBright = gb(LCD),
    surfaceContainerLowest = gb(LCD),
    surfaceContainerLow = blend(gb(LCD), gb(LCD_LIGHT)),
    surfaceContainer = gb(LCD_LIGHT),
    surfaceContainerHigh = blend(gb(LCD_LIGHT), gb(LCD_DARK)),
    surfaceContainerHighest = blend(blend(gb(LCD_LIGHT), gb(LCD_DARK)), gb(LCD_DARK)),
    outline = gb(LCD_DARK),
    outlineVariant = blend(gb(LCD_LIGHT), gb(LCD_DARK)),
    error = gb(LCD_DARKEST),
    onError = gb(LCD),
    errorContainer = gb(LCD_LIGHT),
    onErrorContainer = gb(LCD_DARKEST),
    scrim = gb(LCD_DARKEST),
)

// Square corners: Game Boy pixels are rectangles. Shapes are set per
// component (RectangleShape) because material3's Shapes constructors are
// internal in this artifact, so the theme cannot override them globally.

// Tabular figures everywhere money renders, so amount columns align.
private val SauronTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp,
        fontFeatureSettings = "tnum",
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = "tnum",
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = "tnum",
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp,
    ),
)

@Composable
fun SauronTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GameBoyColors,
        typography = SauronTypography,
        content = content,
    )
}
