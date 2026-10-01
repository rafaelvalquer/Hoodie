package com.hoodie.app.pixel.phoneinsights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hoodie.app.presentation.theme.HoodieColors
import kotlin.math.ceil

/**
 * Histograma de 24 colunas (uso por hora) em "pixels" de 4 dp: cada coluna
 * sobe em degraus inteiros, como um equalizador 16-bit. O bloco do topo é
 * claro para dar leitura rápida do pico.
 */
@Composable
fun HourlyPixelChart(
    hourlyMs: List<Long>,
    modifier: Modifier = Modifier,
    color: Color = RetroUiTheme.Screen,
    highlightHour: Int? = null,
    height: Dp = 72.dp,
) {
    val max = (hourlyMs.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val px = 4.dp.toPx()
            val base = 2.dp.toPx()
            val levels = ((size.height - base) / px).toInt().coerceAtLeast(1)
            val colW = size.width / 24f
            val barW = (colW - 2.dp.toPx()).coerceAtLeast(px)
            drawRect(HoodieColors.Outline, Offset(0f, size.height - base), Size(size.width, base))
            hourlyMs.take(24).forEachIndexed { hour, ms ->
                val steps = if (ms <= 0) 0 else ceil(ms.toDouble() / max * levels).toInt().coerceIn(1, levels)
                val x = hour * colW + (colW - barW) / 2
                val c = if (hour == highlightHour) HoodieColors.Gold else color
                repeat(steps) { s ->
                    val y = size.height - base - (s + 1) * px
                    drawRect(if (s == steps - 1) Color.White.copy(alpha = .85f) else c, Offset(x, y + 1f), Size(barW, px - 1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00h", "06h", "12h", "18h", "23h").forEach {
                Text(it, style = RetroFontStyles.HudLabel, color = HoodieColors.Muted)
            }
        }
    }
}
