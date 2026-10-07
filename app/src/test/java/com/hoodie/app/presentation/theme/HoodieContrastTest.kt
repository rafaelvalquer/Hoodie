package com.hoodie.app.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contraste WCAG dos textos secundários sobre os fundos reais da interface. */
class HoodieContrastTest {
    private fun ratio(fg: Color, bg: Color): Double {
        val a = fg.luminance().toDouble()
        val b = bg.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private val backgrounds = mapOf("Night" to HoodieColors.Night, "Panel" to HoodieColors.Panel, "PanelLight" to HoodieColors.PanelLight)

    @Test
    fun `muted text passes AA on every background`() {
        backgrounds.forEach { (name, bg) -> assertTrue("Muted sobre $name: ${ratio(HoodieColors.Muted, bg)}", ratio(HoodieColors.Muted, bg) >= 4.5) }
    }

    @Test
    fun `tiny 8sp labels use the stronger muted tone`() {
        backgrounds.forEach { (name, bg) ->
            assertTrue("MutedStrong sobre $name: ${ratio(HoodieColors.MutedStrong, bg)}", ratio(HoodieColors.MutedStrong, bg) >= 6.0)
        }
        assertTrue(ratio(HoodieColors.MutedStrong, HoodieColors.PanelLight) > ratio(HoodieColors.Muted, HoodieColors.PanelLight))
    }
}
