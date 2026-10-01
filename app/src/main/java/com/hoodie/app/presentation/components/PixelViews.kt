package com.hoodie.app.presentation.components

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.PixelScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.pixel.sprite.Expression
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.HoodiePose
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * A cena viva. Um único relógio de frames (withFrameMillis) dirige a máquina de
 * estados e o renderer; cada animação tem seu próprio FPS. Quando a tela some,
 * a composição pausa e nada roda em background — o relógio real continua e o
 * estado é recalculado ao voltar.
 */
@Composable
fun HoodieSceneView(
    visual: VisualState?,
    modifier: Modifier = Modifier,
    reactions: Flow<AnimationId>? = null,
    greet: Boolean = true,
    speed: Float = 1f,
    periodOverride: DayPeriod? = null,
) {
    val machine = remember { AnimationStateMachine() }
    val renderer = remember { SceneRenderer() }
    val bitmap = remember { Bitmap.createBitmap(PixelScene.SCENE_W, PixelScene.SCENE_H, Bitmap.Config.ARGB_8888) }
    val image = remember { bitmap.asImageBitmap() }
    var frames by remember { mutableIntStateOf(0) }
    val clock = remember { VirtualClock() }
    val currentSpeed by rememberUpdatedState(speed)
    val currentPeriod by rememberUpdatedState(periodOverride)

    LaunchedEffect(visual) {
        // O Hoodie às vezes percebe que você abriu o app e acena (15%).
        visual?.let { machine.setVisual(it, clock.now(currentSpeed), greet = greet && Random.nextInt(100) < 15) }
    }
    LaunchedEffect(reactions) { reactions?.collect { machine.react(it, clock.now(currentSpeed)) } }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameMillis {
                val t = clock.now(currentSpeed)
                if (t - last >= FRAME_MS) {
                    last = t
                    val time = LocalTime.now()
                    val period = currentPeriod ?: DayPeriod.of(time.hour)
                    machine.frame(t, time.hour * 60 + time.minute, period)?.let { f ->
                        val buf = renderer.render(f, t)
                        bitmap.setPixels(buf.pixels, 0, buf.width, 0, 0, buf.width, buf.height)
                        frames++
                    }
                }
            }
        }
    }
    PixelImage(image, PixelScene.SCENE_W, PixelScene.SCENE_H, modifier) { frames }
}

/** Tempo virtual (permite câmera lenta/rápida no Pixel Lab). */
private class VirtualClock {
    private var lastReal = -1L
    private var virtual = 1L
    fun now(speed: Float): Long {
        val real = SystemClock.uptimeMillis()
        if (lastReal >= 0) virtual += ((real - lastReal) * speed).toLong()
        lastReal = real
        return virtual
    }
}

private const val FRAME_MS = 33L

/**
 * Desenha um bitmap lógico escalado por fator INTEIRO e sem filtro: nunca
 * interpolação bilinear em pixel art.
 */
@Composable
fun PixelImage(image: ImageBitmap, logicalW: Int, logicalH: Int, modifier: Modifier = Modifier, invalidate: () -> Int = { 0 }) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { if (maxHeight == Dp.Infinity) (logicalH * 6).toFloat() else maxHeight.toPx() }
        val scale = max(1, floor(min(maxW / logicalW, maxH / logicalH)).toInt())
        val w = with(density) { (logicalW * scale).toDp() }
        val h = with(density) { (logicalH * scale).toDp() }
        Canvas(Modifier.size(w, h)) {
            invalidate()
            drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(logicalW, logicalH), dstSize = IntSize(logicalW * scale, logicalH * scale), filterQuality = FilterQuality.None)
        }
    }
}

/** Sprite animado isolado (onboarding, perfil, galeria). */
@Composable
fun AnimatedHoodie(
    anim: AnimationId,
    modifier: Modifier = Modifier,
    size: Dp = 144.dp,
    expression: Expression? = null,
    speed: Float = 1f,
) {
    val bitmap = remember { Bitmap.createBitmap(HoodiePainter.WIDTH, HoodiePainter.HEIGHT, Bitmap.Config.ARGB_8888) }
    val image = remember { bitmap.asImageBitmap() }
    var frames by remember { mutableIntStateOf(0) }
    val currentAnim by rememberUpdatedState(anim)
    val currentExpr by rememberUpdatedState(expression)
    val currentSpeed by rememberUpdatedState(speed)
    LaunchedEffect(Unit) {
        val clock = VirtualClock()
        var lastPose: HoodiePose? = null
        while (true) {
            withFrameMillis {
                // Animações curtas (acenar, comemorar) se repetem com uma pausa entre elas.
                val t = clock.now(currentSpeed)
                val elapsed = if (currentAnim.loop) t else t % (currentAnim.durationMs + 1_500)
                var pose = currentAnim.frameAt(elapsed)
                currentExpr?.eyes?.let { e -> if (pose.eyes == com.hoodie.app.pixel.sprite.Eyes.OPEN) pose = pose.copy(eyes = e) }
                if (pose != lastPose) {
                    lastPose = pose
                    val px = HoodiePainter.sprite(pose).pixels
                    bitmap.setPixels(px, 0, HoodiePainter.WIDTH, 0, 0, HoodiePainter.WIDTH, HoodiePainter.HEIGHT)
                    frames++
                }
            }
        }
    }
    Box(modifier.size(size * HoodiePainter.WIDTH / HoodiePainter.HEIGHT, size), contentAlignment = Alignment.Center) {
        PixelImage(image, HoodiePainter.WIDTH, HoodiePainter.HEIGHT) { frames }
    }
}

/** Miniatura estática de cena (Scene Gallery). */
@Composable
fun SceneThumbnail(scene: SceneId, period: DayPeriod, modifier: Modifier = Modifier) {
    val image = remember(scene, period) {
        val s = SceneRegistry[scene]
        val buf: PixelBuffer = SceneRenderer().renderEmpty(s, SceneEnv(period, 10 * 60 + 8, tvOn = true), 1_000)
        val bmp = Bitmap.createBitmap(buf.width, buf.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(buf.pixels, 0, buf.width, 0, 0, buf.width, buf.height)
        bmp.asImageBitmap()
    }
    PixelImage(image, PixelScene.SCENE_W, PixelScene.SCENE_H, modifier)
}
