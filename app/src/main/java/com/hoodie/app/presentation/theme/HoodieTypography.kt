package com.hoodie.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Entrelinha fixa com o texto centralizado na linha. Sem isso o glifo (e, pior, o emoji da fonte de
 * fallback) encosta no rótulo/valor vizinho porque a sobra de altura vai toda para um lado.
 */
val HoodieLineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

fun pixelTextStyle(size: TextUnit, lineHeight: TextUnit, letterSpacing: TextUnit = 0.sp) =
    TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = size, lineHeight = lineHeight, letterSpacing = letterSpacing, lineHeightStyle = HoodieLineHeightStyle)

fun bodyTextStyle(size: TextUnit, lineHeight: TextUnit, letterSpacing: TextUnit = 0.sp) =
    TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = size, lineHeight = lineHeight, letterSpacing = letterSpacing, lineHeightStyle = HoodieLineHeightStyle)

private fun pixel(size: TextUnit, lineHeight: TextUnit) = pixelTextStyle(size, lineHeight)

private fun body(size: TextUnit, lineHeight: TextUnit) = bodyTextStyle(size, lineHeight)

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
