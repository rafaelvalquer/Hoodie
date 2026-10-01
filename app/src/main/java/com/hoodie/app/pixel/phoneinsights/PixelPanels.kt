package com.hoodie.app.pixel.phoneinsights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hoodie.app.presentation.theme.HoodieColors

/**
 * Painel de HUD: contorno escuro, sombra dura, faixa de título com "rebites"
 * nos cantos e scanlines discretas no fundo. Mesmo DNA do PixelPanel do app.
 */
@Composable
fun HudPanel(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = HoodieColors.Blue,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .drawBehind {
                val s = 4.dp.toPx()
                drawRect(HoodieColors.Outline, topLeft = Offset(s, s), size = size)
            }
            .background(HoodieColors.Panel)
            .drawBehind {
                // Scanlines a cada 4 dp: textura leve de tela CRT.
                val step = 4.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawRect(RetroUiTheme.Scanline, Offset(0f, y), Size(size.width, 1f))
                    y += step
                }
            }
            .border(2.dp, HoodieColors.Outline)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Row(
            Modifier.fillMaxWidth().background(accent).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Rivet()
            Box(Modifier.width(6.dp))
            Text(title.uppercase(), style = RetroFontStyles.PanelTitle, color = HoodieColors.Outline, modifier = Modifier.weight(1f), maxLines = 1)
            trailing?.let { Text(it.uppercase(), style = RetroFontStyles.HudLabel, color = HoodieColors.Outline, maxLines = 1) }
            Box(Modifier.width(6.dp))
            Rivet()
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(HoodieColors.Outline))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun Rivet() {
    Box(Modifier.size(6.dp).background(HoodieColors.Outline).padding(1.dp).background(Color(0x66FFFFFF)))
}

/** Tile de número estilo placar: valor grande, rótulo pequeno e um quadradinho de cor. */
@Composable
fun HudStatTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(HoodieColors.PanelLight)
            .border(2.dp, HoodieColors.Outline)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color).border(1.dp, HoodieColors.Outline))
            Box(Modifier.width(6.dp))
            Text(label.uppercase(), style = RetroFontStyles.HudLabel, color = HoodieColors.Muted, maxLines = 1)
        }
        Text(value, style = RetroFontStyles.HudNumber, color = HoodieColors.Ink, maxLines = 1)
    }
}

/** Badge pequeno de categoria/contexto: bloco de cor com contorno. */
@Composable
fun PixelTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(color).border(1.dp, HoodieColors.Outline).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text.uppercase(), style = RetroFontStyles.HudLabel, color = HoodieColors.Outline, maxLines = 1)
    }
}
