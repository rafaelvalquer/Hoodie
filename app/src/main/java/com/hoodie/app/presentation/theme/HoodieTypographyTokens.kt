package com.hoodie.app.presentation.theme

import androidx.compose.ui.unit.sp

/**
 * Escala tipográfica do Hoodie em um só lugar. A fonte pixel (Press Start 2P) é larga: o
 * entrelinha fica perto de 1,4× em vez de 1,5× para não inflar botões e cards.
 */
object HoodieTypographyTokens {
    // Fonte pixel: títulos, números, botões, labels curtos, menus.
    val DisplaySize = 22.sp
    val DisplayLineHeight = 30.sp

    val HeadlineSize = 15.sp
    val HeadlineLineHeight = 21.sp

    val TitleLargeSize = 13.sp
    val TitleLargeLineHeight = 18.sp

    val TitleMediumSize = 11.sp
    val TitleMediumLineHeight = 16.sp

    val LabelLargeSize = 10.sp
    val LabelLargeLineHeight = 15.sp

    val LabelSmallSize = 8.sp
    val LabelSmallLineHeight = 12.sp

    // Fonte do sistema: descrições, ajuda, privacidade, mensagens e erros longos.
    val BodyLargeSize = 15.sp
    val BodyLargeLineHeight = 21.sp

    val BodyMediumSize = 13.sp
    val BodyMediumLineHeight = 19.sp

    val BodySmallSize = 11.sp
    val BodySmallLineHeight = 16.sp
}
