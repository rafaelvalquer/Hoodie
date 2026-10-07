package com.hoodie.app.pixel.phoneinsights

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hoodie.app.presentation.theme.HoodieTypographyTokens
import com.hoodie.app.presentation.theme.bodyTextStyle
import com.hoodie.app.presentation.theme.pixelTextStyle

/** Pixel font for HUD titles/numbers; readable system font for descriptions. */
object RetroFontStyles {
    /** Títulos de painel: caixa alta, espaçado, estilo menu de jogo. */
    val PanelTitle = pixelTextStyle(9.sp, 14.sp)
    /** Números grandes de placar arcade (tempo de tela). */
    val HudNumberLarge = pixelTextStyle(26.sp, 39.sp, letterSpacing = 1.sp)
    /** Contadores dos tiles (desbloqueios, sessões). */
    val HudNumber = pixelTextStyle(14.sp, 21.sp, letterSpacing = 1.sp)
    /** Rótulos minúsculos sob os números. */
    val HudLabel = pixelTextStyle(8.sp, 12.sp)
    /** Corpo legível (nomes de apps, frases do Hoodie): mesma escala do `bodyMedium` do tema. */
    val Body = bodyTextStyle(HoodieTypographyTokens.BodyMediumSize, HoodieTypographyTokens.BodyMediumLineHeight)
    val BodyBold = Body.copy(fontWeight = FontWeight.Bold)
    /** Texto de apoio: mesma escala do `bodySmall` do tema. */
    val Small = bodyTextStyle(HoodieTypographyTokens.BodySmallSize, HoodieTypographyTokens.BodySmallLineHeight)
}
