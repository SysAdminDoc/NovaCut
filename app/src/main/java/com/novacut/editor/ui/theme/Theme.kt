package com.novacut.editor.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Catppuccin Mocha palette
object Mocha {
    val Crust = Color(0xFF11111B)
    val Mantle = Color(0xFF181825)
    val Base = Color(0xFF1E1E2E)
    val Surface0 = Color(0xFF313244)
    val Surface1 = Color(0xFF45475A)
    val Surface2 = Color(0xFF585B70)
    val Overlay0 = Color(0xFF6C7086)
    val Overlay1 = Color(0xFF7F849C)
    val Overlay2 = Color(0xFF9399B2)
    val Subtext0 = Color(0xFFA6ADC8)
    val Subtext1 = Color(0xFFBAC2DE)
    val Text = Color(0xFFCDD6F4)
    val Lavender = Color(0xFFB4BEFE)
    val Blue = Color(0xFF89B4FA)
    val Sapphire = Color(0xFF74C7EC)
    val Sky = Color(0xFF89DCEB)
    val Teal = Color(0xFF94E2D5)
    val Green = Color(0xFFA6E3A1)
    val Yellow = Color(0xFFF9E2AF)
    val Peach = Color(0xFFFAB387)
    val Maroon = Color(0xFFEBA0AC)
    val Red = Color(0xFFF38BA8)
    val Mauve = Color(0xFFCBA6F7)
    val Pink = Color(0xFFF5C2E7)
    val Flamingo = Color(0xFFF2CDCD)
    val Rosewater = Color(0xFFF5E0DC)
}

private val NovaCutColorScheme = darkColorScheme(
    primary = Mocha.Mauve,
    onPrimary = Mocha.Crust,
    primaryContainer = Mocha.Mauve.copy(alpha = 0.3f),
    onPrimaryContainer = Mocha.Mauve,
    secondary = Mocha.Blue,
    onSecondary = Mocha.Crust,
    secondaryContainer = Mocha.Blue.copy(alpha = 0.3f),
    onSecondaryContainer = Mocha.Blue,
    tertiary = Mocha.Teal,
    onTertiary = Mocha.Crust,
    tertiaryContainer = Mocha.Teal.copy(alpha = 0.3f),
    onTertiaryContainer = Mocha.Teal,
    error = Mocha.Red,
    onError = Mocha.Crust,
    errorContainer = Mocha.Red.copy(alpha = 0.3f),
    onErrorContainer = Mocha.Red,
    background = Mocha.Base,
    onBackground = Mocha.Text,
    surface = Mocha.Base,
    onSurface = Mocha.Text,
    surfaceVariant = Mocha.Surface0,
    onSurfaceVariant = Mocha.Subtext1,
    outline = Mocha.Surface2,
    outlineVariant = Mocha.Surface1,
    inverseSurface = Mocha.Text,
    inverseOnSurface = Mocha.Base,
    inversePrimary = Mocha.Mauve,
    surfaceDim = Mocha.Crust,
    surfaceBright = Mocha.Surface1,
    surfaceContainerLowest = Mocha.Crust,
    surfaceContainerLow = Mocha.Mantle,
    surfaceContainer = Mocha.Base,
    surfaceContainerHigh = Mocha.Surface0,
    surfaceContainerHighest = Mocha.Surface1
)

@Composable
fun NovaCutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NovaCutColorScheme,
        typography = Typography(),
        content = content
    )
}
