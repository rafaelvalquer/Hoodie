package com.hoodie.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun pixel(size: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit) =
    TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = size, lineHeight = lineHeight, letterSpacing = 0.sp)

private fun body(size: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit) =
    TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = size, lineHeight = lineHeight, letterSpacing = 0.sp)

/** Pixel nos títulos/botões/labels; fonte do sistema nos textos de leitura. */
val HoodieTypography = Typography(
    displaySmall = pixel(HoodieTypographyTokens.DisplaySize, HoodieTypographyTokens.DisplayLineHeight),
    headlineSmall = pixel(HoodieTypographyTokens.HeadlineSize, HoodieTypographyTokens.HeadlineLineHeight),
    titleLarge = pixel(HoodieTypographyTokens.TitleLargeSize, HoodieTypographyTokens.TitleLargeLineHeight),
    titleMedium = pixel(HoodieTypographyTokens.TitleMediumSize, HoodieTypographyTokens.TitleMediumLineHeight),
    labelLarge = pixel(HoodieTypographyTokens.LabelLargeSize, HoodieTypographyTokens.LabelLargeLineHeight),
    labelSmall = pixel(HoodieTypographyTokens.LabelSmallSize, HoodieTypographyTokens.LabelSmallLineHeight),
    bodyLarge = body(HoodieTypographyTokens.BodyLargeSize, HoodieTypographyTokens.BodyLargeLineHeight),
    bodyMedium = body(HoodieTypographyTokens.BodyMediumSize, HoodieTypographyTokens.BodyMediumLineHeight),
    bodySmall = body(HoodieTypographyTokens.BodySmallSize, HoodieTypographyTokens.BodySmallLineHeight),
)
