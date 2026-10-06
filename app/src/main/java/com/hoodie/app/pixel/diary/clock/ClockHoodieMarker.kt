package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.pixel.diary.DiaryHoodieMarker
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.diary.MarkerDirection
import com.hoodie.app.pixel.diary.journey.JourneyHoodieMarker
import com.hoodie.app.pixel.diary.journey.JourneyMarkerState
import com.hoodie.app.pixel.diary.journey.JourneyVehicle
import com.hoodie.app.pixel.renderer.PixelBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hoodie do mostrador: o MESMO marcador da Jornada (`JourneyHoodieMarker`) — a pé com
 * piscada e passos, de bicicleta, carro, ônibus, trem ou metrô — centrado no anel de
 * 30 px. Pinta num buffer de rascunho e copia só os pixels opacos (as sombras
 * translúcidas do mapa ficam de fora). Nenhuma alocação por frame.
 */
object ClockHoodieMarker {
    private const val BOX_W = 40
    private const val BOX_H = 32
    private const val FEET_X = BOX_W / 2
    private const val FEET_Y = BOX_H - 4
    private val scratch = PixelBuffer(BOX_W, BOX_H)

    /** Sobe 1 px a cada ~0,6 s parado (respiração). */
    fun breath(timeMs: Long, moving: Boolean): Int = if (!moving && (timeMs / 600) % 2 == 1L) -1 else 0

    /** Estado do marcador: andando no sentido horário do anel (tangente no [minute]). */
    fun state(minute: Float, dayLength: Int, mode: MovementMode?, moving: Boolean): JourneyMarkerState {
        val a = minute / dayLength * 2 * PI
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val vehicle = if (moving) JourneyHoodieMarker.vehicleFor(mode) else JourneyVehicle.ON_FOOT
        return JourneyMarkerState(
            position = MapPoint(FEET_X.toFloat(), FEET_Y.toFloat()),
            direction = if (moving) DiaryHoodieMarker.direction(dx, dy) else MarkerDirection.FRONT,
            moving = moving,
            vehicle = vehicle,
            facingRight = dx >= 0f,
            running = mode == MovementMode.RUNNING,
        )
    }

    /** Pinta o Hoodie com o centro visual em ([cx], [cy]). */
    fun paint(out: PixelBuffer, cx: Int, cy: Int, s: JourneyMarkerState, timeMs: Long) = synchronized(scratch) {
        scratch.clear()
        JourneyHoodieMarker.paint(scratch, s, timeMs)
        val h = JourneyHoodieMarker.heightOf(s)
        val ox = cx - FEET_X
        val oy = cy + h / 2 - FEET_Y + breath(timeMs, s.moving)
        val src = scratch.pixels
        for (y in 0 until BOX_H) {
            val ty = oy + y
            if (ty < 0 || ty >= out.height) continue
            for (x in 0 until BOX_W) {
                val c = src[y * BOX_W + x]
                val tx = ox + x
                if (c ushr 24 == 0xFF && tx >= 0 && tx < out.width) out.pixels[ty * out.width + tx] = c
            }
        }
    }

    /** Caixa opaca ocupada por um estado (para testes de encaixe no anel). */
    fun bounds(s: JourneyMarkerState, timeMs: Long): Pair<Int, Int> = synchronized(scratch) {
        scratch.clear()
        JourneyHoodieMarker.paint(scratch, s, timeMs)
        var x0 = BOX_W; var x1 = -1; var y0 = BOX_H; var y1 = -1
        for (y in 0 until BOX_H) for (x in 0 until BOX_W) if (scratch.pixels[y * BOX_W + x] ushr 24 == 0xFF) {
            x0 = minOf(x0, x); x1 = maxOf(x1, x); y0 = minOf(y0, y); y1 = maxOf(y1, y)
        }
        (x1 - x0 + 1) to (y1 - y0 + 1)
    }

    /**
     * Cores opacas que o marcador da Jornada usa (todas as poses, veículos e quadros).
     * Já validadas na Jornada; entram na paleta fechada do relógio.
     */
    val COLORS: Set<Int> by lazy {
        val modes = MovementMode.entries + listOf(null)
        buildSet {
            modes.forEach { mode ->
                listOf(true, false).forEach { moving ->
                    listOf(0f, 360f, 720f, 1080f).forEach { minute ->
                        (0L until 3_200L step 40L).forEach { t ->
                            synchronized(scratch) {
                                scratch.clear()
                                JourneyHoodieMarker.paint(scratch, state(minute, 1440, mode, moving), t)
                                scratch.pixels.forEach { c -> if (c ushr 24 == 0xFF) add(c) }
                            }
                        }
                    }
                }
            }
        }
    }
}
