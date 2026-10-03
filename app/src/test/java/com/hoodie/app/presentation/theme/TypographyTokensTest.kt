package com.hoodie.app.presentation.theme

import androidx.compose.ui.text.font.FontFamily
import com.hoodie.app.pixel.phoneinsights.RetroFontStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyTokensTest {
    private val t = HoodieTypographyTokens

    @Test
    fun `pixel typography follows hierarchy`() {
        assertTrue(t.DisplaySize.value > t.HeadlineSize.value)
        assertTrue(t.HeadlineSize.value > t.TitleLargeSize.value)
        assertTrue(t.TitleLargeSize.value > t.TitleMediumSize.value)
        assertTrue(t.TitleMediumSize.value > t.LabelLargeSize.value)
        assertTrue(t.LabelLargeSize.value > t.LabelSmallSize.value)
        assertTrue(t.BodyLargeSize.value > t.BodyMediumSize.value)
        assertTrue(t.BodyMediumSize.value > t.BodySmallSize.value)
    }

    @Test
    fun `line height is always larger than font size`() {
        listOf(
            "display" to (t.DisplaySize to t.DisplayLineHeight),
            "headline" to (t.HeadlineSize to t.HeadlineLineHeight),
            "titleLarge" to (t.TitleLargeSize to t.TitleLargeLineHeight),
            "titleMedium" to (t.TitleMediumSize to t.TitleMediumLineHeight),
            "labelLarge" to (t.LabelLargeSize to t.LabelLargeLineHeight),
            "labelSmall" to (t.LabelSmallSize to t.LabelSmallLineHeight),
            "bodyLarge" to (t.BodyLargeSize to t.BodyLargeLineHeight),
            "bodyMedium" to (t.BodyMediumSize to t.BodyMediumLineHeight),
            "bodySmall" to (t.BodySmallSize to t.BodySmallLineHeight),
        ).forEach { (name, p) ->
            val (size, line) = p
            assertTrue("$name: entrelinha ${line.value} <= fonte ${size.value}", line.value > size.value)
            // Press Start 2P não precisa de 1,5×; mais que isso volta a inflar botões e cards.
            assertTrue("$name: entrelinha ${line.value / size.value}× > 1,5×", line.value / size.value <= 1.5f)
        }
    }

    @Test
    fun `scale matches the approved sizes`() {
        assertEquals(listOf(22f, 15f, 13f, 11f, 10f, 8f), listOf(t.DisplaySize, t.HeadlineSize, t.TitleLargeSize, t.TitleMediumSize, t.LabelLargeSize, t.LabelSmallSize).map { it.value })
        assertEquals(listOf(30f, 21f, 18f, 16f, 15f, 12f), listOf(t.DisplayLineHeight, t.HeadlineLineHeight, t.TitleLargeLineHeight, t.TitleMediumLineHeight, t.LabelLargeLineHeight, t.LabelSmallLineHeight).map { it.value })
    }

    @Test
    fun `titles buttons and labels use the pixel font`() {
        listOf(HoodieTypography.displaySmall, HoodieTypography.headlineSmall, HoodieTypography.titleLarge, HoodieTypography.titleMedium, HoodieTypography.labelLarge, HoodieTypography.labelSmall)
            .forEach { assertEquals(PixelFont, it.fontFamily) }
    }

    @Test
    fun `long reading text keeps the system font`() {
        listOf(HoodieTypography.bodyLarge, HoodieTypography.bodyMedium, HoodieTypography.bodySmall)
            .forEach { assertEquals(FontFamily.SansSerif, it.fontFamily) }
        assertEquals(FontFamily.SansSerif, RetroFontStyles.Body.fontFamily)
        assertEquals(FontFamily.SansSerif, RetroFontStyles.Small.fontFamily)
    }

    @Test
    fun `hud styles stay pixel and keep the big numbers prominent`() {
        listOf(RetroFontStyles.PanelTitle, RetroFontStyles.HudNumberLarge, RetroFontStyles.HudNumber, RetroFontStyles.HudLabel)
            .forEach { assertEquals(PixelFont, it.fontFamily) }
        assertTrue(RetroFontStyles.HudNumberLarge.fontSize.value > HoodieTypographyTokens.DisplaySize.value)
        assertTrue(RetroFontStyles.HudNumber.fontSize.value > HoodieTypographyTokens.TitleLargeSize.value)
    }
}
