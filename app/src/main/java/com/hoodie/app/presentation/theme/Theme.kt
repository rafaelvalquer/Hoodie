package com.hoodie.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta retrô derivada do Hoodie (mesmos tons do sprite). */
object HoodieColors {
    val Night = Color(0xFF1B1D33)
    val Panel = Color(0xFF26294A)
    val PanelLight = Color(0xFF33375E)
    val Outline = Color(0xFF0F1124)
    val Ink = Color(0xFFE9EDFF)
    val Muted = Color(0xFF9AA3C7)
    val Blue = Color(0xFF86A9E8)
    val Hood = Color(0xFFB9CBEF)
    val Gold = Color(0xFFF2CF5B)
    val Coral = Color(0xFFE58AAE)
    val Mint = Color(0xFF7FE0C2)
}

private val scheme = darkColorScheme(
    primary = HoodieColors.Blue,
    onPrimary = HoodieColors.Outline,
    secondary = HoodieColors.Gold,
    onSecondary = HoodieColors.Outline,
    tertiary = HoodieColors.Coral,
    background = HoodieColors.Night,
    onBackground = HoodieColors.Ink,
    surface = HoodieColors.Panel,
    onSurface = HoodieColors.Ink,
    surfaceVariant = HoodieColors.PanelLight,
    onSurfaceVariant = HoodieColors.Muted,
    surfaceContainer = HoodieColors.Panel,
    surfaceContainerHigh = HoodieColors.PanelLight,
    surfaceContainerHighest = HoodieColors.PanelLight,
    outline = HoodieColors.Outline,
)

/** Monoespaçada para títulos: lembra o texto dos consoles portáteis. */
val PixelFont = FontFamily.Monospace

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 2.sp),
    headlineSmall = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp),
    labelLarge = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontFamily = PixelFont, fontSize = 11.sp, letterSpacing = 1.sp),
)

private val shapes = Shapes(
    extraSmall = CutCornerShape(2.dp),
    small = CutCornerShape(3.dp),
    medium = CutCornerShape(4.dp),
    large = CutCornerShape(6.dp),
    extraLarge = CutCornerShape(8.dp),
)

@Composable
fun HoodieTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
