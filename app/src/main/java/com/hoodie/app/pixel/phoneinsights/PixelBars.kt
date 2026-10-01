package com.hoodie.app.pixel.phoneinsights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hoodie.app.presentation.theme.HoodieColors
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Barra de RPG em blocos: enche bloco a bloco (sem meio-bloco borrado), com
 * brilho no topo e sombra embaixo de cada bloco. Anima ao aparecer.
 */
@Composable
fun SegmentedPixelBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    segments: Int = 16,
    height: Dp = 12.dp,
) {
    val target = fraction.coerceIn(0f, 1f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target, tween(durationMillis = 520)) }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = 2.dp.toPx()
        val outline = 1.dp.toPx()
        val segW = (size.width - gap * (segments - 1)) / segments
        // Qualquer uso > 0 aparece como pelo menos um bloco.
        val filled = if (anim.value <= 0f) 0 else ceil(anim.value * segments).toInt().coerceIn(1, segments)
        repeat(segments) { i ->
            val x = i * (segW + gap)
            drawRect(HoodieColors.Outline, Offset(x, 0f), Size(segW, size.height))
            val inner = Offset(x + outline, outline)
            val innerSize = Size(segW - outline * 2, size.height - outline * 2)
            if (i < filled) {
                drawRect(color, inner, innerSize)
                drawRect(Color(0x55FFFFFF), inner, Size(innerSize.width, outline * 2))
                drawRect(RetroUiTheme.shade(color), Offset(inner.x, inner.y + innerSize.height - outline * 2), Size(innerSize.width, outline * 2))
            } else {
                drawRect(RetroUiTheme.Track, inner, innerSize)
            }
        }
    }
}

/** Uma barra única dividida em fatias proporcionais (ex.: tempo por categoria). */
@Composable
fun StackedPixelBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 18.dp) {
    val total = parts.sumOf { it.first.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    Canvas(modifier.fillMaxWidth().height(height)) {
        val outline = 2.dp.toPx()
        drawRect(HoodieColors.Outline, Offset.Zero, size)
        val innerW = size.width - outline * 2
        val innerH = size.height - outline * 2
        drawRect(RetroUiTheme.Track, Offset(outline, outline), Size(innerW, innerH))
        var x = outline
        parts.forEachIndexed { i, (value, color) ->
            // Fatias em pixel inteiro: sem borda borrada entre cores.
            val w = if (i == parts.lastIndex) outline + innerW - x else floor(innerW * value / total)
            if (w <= 0f) return@forEachIndexed
            drawRect(color, Offset(x, outline), Size(w, innerH))
            drawRect(RetroUiTheme.shade(color), Offset(x, outline + innerH - outline), Size(w, outline))
            if (i > 0) drawRect(HoodieColors.Outline, Offset(x, outline), Size(1.dp.toPx(), innerH))
            x += w
        }
    }
}
