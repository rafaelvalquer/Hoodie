package com.hoodie.app.pixel.phoneinsights

import androidx.compose.ui.graphics.Color
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.presentation.theme.HoodieColors

/**
 * Cores do Diário Digital. Tons suaves e dessaturados para conviver com a
 * paleta noturna do Hoodie; [shade] dá o tom de baixo de cada bloco para as
 * barras terem "volume" de pixel art.
 */
object RetroUiTheme {
    val Screen = Color(0xFF8FD3FF)
    val Unlock = Color(0xFFF2CF5B)
    val Sessions = Color(0xFF7FE0C2)
    val Longest = Color(0xFFE58AAE)
    val Track = Color(0xFF1F2242)
    val Scanline = Color(0x0FFFFFFF)

    fun category(c: HoodieAppCategory): Color = when (c) {
        HoodieAppCategory.SOCIAL -> Color(0xFFD98AD6) // rosa/roxo
        HoodieAppCategory.MUSIC -> Color(0xFF6FD6B5) // verde/teal
        HoodieAppCategory.VIDEO -> Color(0xFFEE8A6A) // vermelho/laranja
        HoodieAppCategory.WORK -> Color(0xFF7FA6EA) // azul
        HoodieAppCategory.NAVIGATION -> Color(0xFFE9CF6A) // amarelo
        HoodieAppCategory.GAMES -> Color(0xFF9B7FD8) // roxo escuro
        HoodieAppCategory.ENTERTAINMENT -> Color(0xFFF0A86B)
        HoodieAppCategory.READING -> Color(0xFFB8C98A)
        HoodieAppCategory.SHOPPING -> Color(0xFFE88FA6)
        HoodieAppCategory.TOOLS -> Color(0xFF9FB3C8)
        HoodieAppCategory.OTHER -> HoodieColors.Muted
    }

    /** Mesma cor um tom abaixo: a "sombra" de dentro do bloco. */
    fun shade(c: Color): Color = Color(c.red * 0.72f, c.green * 0.72f, c.blue * 0.72f, c.alpha)

    fun context(c: UserContextType): Color = when (c) {
        UserContextType.HOME -> Color(0xFF86C99A)
        UserContextType.WORK -> Color(0xFF88AFE9)
        UserContextType.COMMUTING -> Color(0xFFE6C86D)
        UserContextType.LUNCH -> Color(0xFFE99C7D)
        UserContextType.GYM -> Color(0xFFE58AAE)
        else -> Color(0xFFB79AE9)
    }
}
