package com.hoodie.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.pixel.effects.TapEffectStyle
import com.hoodie.app.pixel.effects.TapEffects

/**
 * Escolha do efeito de toque enquanto ele está em teste: o Pixel Lab muda para o app inteiro
 * até o app fechar. Depois de escolhido, vira o padrão em [HoodieConfig.TAP_EFFECT].
 */
object TapEffectPreview {
    var style by mutableStateOf<TapEffectStyle?>(HoodieConfig.TAP_EFFECT)
    /** A aba de teste desenha o próprio efeito (com câmera lenta); a camada do app se recolhe. */
    var labOpen by mutableStateOf(false)
}

private data class ActiveTap(val position: Offset, val startedAt: Long, val seed: Int)

/**
 * Mostra um efeito em pixel art onde a tela foi tocada. Observa os toques sem consumi-los
 * (botões, rolagem e gestos continuam iguais) e não desenha nada quando [style] é null.
 * [speed] < 1 deixa o efeito em câmera lenta (só para teste).
 */
@Composable
fun TapEffectLayer(
    style: TapEffectStyle?,
    modifier: Modifier = Modifier,
    pixelSize: Dp = 3.dp,
    speed: Float = 1f,
    content: @Composable () -> Unit,
) {
    val taps = remember { mutableStateListOf<ActiveTap>() }
    var now by remember { mutableLongStateOf(0L) }
    var counter by remember { mutableLongStateOf(0L) }
    LaunchedEffect(taps.isNotEmpty(), style, speed) {
        while (taps.isNotEmpty() && style != null) {
            withFrameMillis { frame ->
                now = frame
                taps.removeAll { (frame - it.startedAt) * speed >= style.durationMs }
            }
        }
    }
    Box(
        modifier.pointerInput(style) {
            if (style == null) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    // Passagem inicial e sem consumir: só observa o toque.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.filter { it.changedToDown() }.forEach { change ->
                        if (taps.size >= MAX_TAPS) taps.removeAt(0)
                        counter++
                        taps += ActiveTap(change.position, change.uptimeMillis, counter.toInt())
                        now = change.uptimeMillis
                    }
                }
            }
        },
    ) {
        content()
        if (style != null && taps.isNotEmpty()) {
            Canvas(Modifier.matchParentSize()) {
                val px = pixelSize.toPx()
                taps.forEach { tap ->
                    val age = ((now - tap.startedAt) * speed).toLong()
                    TapEffects.cells(style, age, tap.seed).forEach { cell ->
                        drawRect(
                            Color(cell.color).copy(alpha = cell.alpha),
                            topLeft = Offset(tap.position.x + (cell.dx - 0.5f) * px, tap.position.y + (cell.dy - 0.5f) * px),
                            size = Size(px, px),
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_TAPS = 6
