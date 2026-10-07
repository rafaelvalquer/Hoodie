package com.hoodie.app.presentation.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import com.hoodie.app.R
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.unit.dp

/** Paleta retrô derivada do Hoodie (mesmos tons do sprite). */
object HoodieColors {
    val Night = Color(0xFF1B1D33)
    val Panel = Color(0xFF26294A)
    val PanelLight = Color(0xFF33375E)
    val Outline = Color(0xFF0F1124)
    val Ink = Color(0xFFE9EDFF)
    val Muted = Color(0xFF9AA3C7)
    /** Rótulos de 8 sp: 6,3:1 sobre PanelLight (o Muted dá 4,56:1, no limite para texto tão pequeno). */
    val MutedStrong = Color(0xFFB8C0E0)
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

/** Press Start 2P, SIL OFL 1.1; license is bundled in assets/licenses. */
val PixelFont = FontFamily(Font(R.font.hoodie_pixel))

private val shapes = Shapes(
    extraSmall = CutCornerShape(2.dp),
    small = CutCornerShape(3.dp),
    medium = CutCornerShape(4.dp),
    large = CutCornerShape(6.dp),
    extraLarge = CutCornerShape(8.dp),
)

/** Espaços padrão da interface. */
object HoodieSpacing {
    /** Entre o rótulo pequeno (SectionLabel) e o valor logo abaixo. */
    val LabelToValue = 4.dp
}

@Composable
fun HoodieTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = HoodieTypography, shapes = shapes) {
        // Todo Text sem estilo explícito é texto de leitura (fonte do sistema), não o padrão do Material.
        CompositionLocalProvider(
            LocalContentColor provides scheme.onBackground,
            LocalTextStyle provides HoodieTypography.bodyMedium,
            content = content,
        )
    }
}
