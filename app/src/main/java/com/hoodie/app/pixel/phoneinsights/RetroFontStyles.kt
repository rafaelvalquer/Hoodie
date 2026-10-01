package com.hoodie.app.pixel.phoneinsights

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hoodie.app.presentation.theme.PixelFont

/**
 * Tipografia do HUD. Usa a família do tema (monoespaçada, cara de console
 * portátil); quando a pixel font própria (OFL) entrar no projeto, basta trocar
 * [PixelFont] e tudo aqui acompanha.
 */
object RetroFontStyles {
    /** Títulos de painel: caixa alta, espaçado, estilo menu de jogo. */
    val PanelTitle = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp)
    /** Números grandes de placar arcade (tempo de tela). */
    val HudNumberLarge = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = 1.sp)
    /** Contadores dos tiles (desbloqueios, sessões). */
    val HudNumber = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Black, fontSize = 19.sp, letterSpacing = 1.sp)
    /** Rótulos minúsculos sob os números. */
    val HudLabel = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.5.sp)
    /** Corpo legível (nomes de apps, frases do Hoodie). */
    val Body = TextStyle(fontFamily = PixelFont, fontSize = 13.sp, letterSpacing = 0.3.sp)
    val BodyBold = Body.copy(fontWeight = FontWeight.Bold)
    val Small = TextStyle(fontFamily = PixelFont, fontSize = 11.sp, letterSpacing = 0.5.sp)
}
