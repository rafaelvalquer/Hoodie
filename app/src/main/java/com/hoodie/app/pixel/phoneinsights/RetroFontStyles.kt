package com.hoodie.app.pixel.phoneinsights

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hoodie.app.presentation.theme.PixelFont

/** Pixel font for HUD titles/numbers; readable system font for descriptions. */
object RetroFontStyles {
    /** Títulos de painel: caixa alta, espaçado, estilo menu de jogo. */
    val PanelTitle = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 9.sp, lineHeight = 14.sp, letterSpacing = 0.sp)
    /** Números grandes de placar arcade (tempo de tela). */
    val HudNumberLarge = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 39.sp, letterSpacing = 1.sp)
    /** Contadores dos tiles (desbloqueios, sessões). */
    val HudNumber = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 1.sp)
    /** Rótulos minúsculos sob os números. */
    val HudLabel = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 8.sp, lineHeight = 12.sp, letterSpacing = 0.sp)
    /** Corpo legível (nomes de apps, frases do Hoodie). */
    val Body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp)
    val BodyBold = Body.copy(fontWeight = FontWeight.Bold)
    val Small = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
}
