package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.pixel.diary.DiaryHoodieMarker
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.diary.MarkerDirection
import com.hoodie.app.pixel.diary.MarkerState
import com.hoodie.app.pixel.diary.journey.JourneyPalette as P
import com.hoodie.app.pixel.renderer.PixelBuffer
import kotlin.math.roundToInt

/** Como o Hoodie aparece no trecho (plano §9.4). Caminhada/corrida/desconhecido = a pé. */
enum class JourneyVehicle { ON_FOOT, BICYCLE, CAR, BUS, TRAIN }

/** Onde e como o Hoodie está num instante. */
data class JourneyMarkerState(
    val position: MapPoint,
    val direction: MarkerDirection,
    val moving: Boolean,
    val vehicle: JourneyVehicle,
    /** Veículos ficam de lado mesmo nas descidas: o sentido horizontal do trecho. */
    val facingRight: Boolean,
    val running: Boolean = false,
)

/**
 * O Hoodie andando de verdade pela rua: sai da plataforma A, desce, faz a curva,
 * atravessa e chega na plataforma B. A pé usa o mini sprite de 4 direções do
 * mapa clássico; de bicicleta pedala; de carro/ônibus/trem aparece na janela.
 */
object JourneyHoodieMarker {

    fun vehicleFor(mode: MovementMode?): JourneyVehicle = when (mode) {
        MovementMode.BICYCLE -> JourneyVehicle.BICYCLE
        MovementMode.CAR, MovementMode.VEHICLE_UNKNOWN -> JourneyVehicle.CAR
        MovementMode.BUS, MovementMode.PUBLIC_TRANSPORT -> JourneyVehicle.BUS
        MovementMode.TRAIN, MovementMode.METRO -> JourneyVehicle.TRAIN
        MovementMode.WALKING, MovementMode.RUNNING, MovementMode.OTHER, MovementMode.NONE, null -> JourneyVehicle.ON_FOOT
    }

    /** No meio do trecho: posição pelo comprimento percorrido, direção pela rua daquele ponto. */
    fun onSegment(segment: JourneySegmentLayout, mode: MovementMode?, progress: Float): JourneyMarkerState {
        val p = progress.coerceIn(0f, 1f)
        val (dx, dy) = segment.path.directionAt(p)
        return JourneyMarkerState(
            position = segment.path.pointAt(p),
            direction = DiaryHoodieMarker.direction(dx, dy),
            moving = p > 0f && p < 1f,
            vehicle = vehicleFor(mode),
            facingRight = segment.path.horizontalSign >= 0f,
            running = mode == MovementMode.RUNNING,
        )
    }

    fun atNode(node: JourneyNodeLayout) = JourneyMarkerState(
        position = node.stop, direction = MarkerDirection.FRONT, moving = false,
        vehicle = JourneyVehicle.ON_FOOT, facingRight = node.side == JourneySide.LEFT,
    )

    /** Pixels ocupados acima dos pés (para posicionar o balão do app). */
    fun heightOf(state: JourneyMarkerState) = when (state.vehicle) {
        JourneyVehicle.ON_FOOT -> DiaryHoodieMarker.HEIGHT
        JourneyVehicle.BICYCLE -> DiaryHoodieMarker.HEIGHT + 5
        JourneyVehicle.CAR -> 16
        JourneyVehicle.BUS, JourneyVehicle.TRAIN -> 17
    }

    fun paint(b: PixelBuffer, s: JourneyMarkerState, timeMs: Long) {
        val x = s.position.x.roundToInt(); val y = s.position.y.roundToInt()
        when (s.vehicle) {
            JourneyVehicle.ON_FOOT -> DiaryHoodieMarker.paint(
                b, MarkerState(s.position, s.direction, s.moving), if (s.running) timeMs * 2 else timeMs,
            )
            JourneyVehicle.BICYCLE -> bicycle(b, x, y, s, timeMs)
            JourneyVehicle.CAR -> car(b, x, y, s, timeMs)
            JourneyVehicle.BUS -> bus(b, x, y, s, timeMs)
            JourneyVehicle.TRAIN -> train(b, x, y, s, timeMs)
        }
    }

    // ───────────── Veículos (feet = centro da base) ─────────────

    private fun bob(s: JourneyMarkerState, timeMs: Long) = if (s.moving && (timeMs / 200) % 2 == 1L) 1 else 0

    /** Cabeça do Hoodie (orelhas + rosto) de perfil, recortada do sprite: [rows] linhas do topo. */
    private fun head(b: PixelBuffer, x: Int, y: Int, facingRight: Boolean, timeMs: Long, rows: Int = 9) {
        val side = DiaryHoodieMarker.sprite(if (facingRight) MarkerDirection.RIGHT else MarkerDirection.LEFT, false, timeMs)
        for (sy in 0 until rows) for (sx in 0 until side.width) {
            val c = side[sx, sy]
            if (c ushr 24 != 0) b.set(x + sx, y + sy, c)
        }
    }

    private fun wheel(b: PixelBuffer, cx: Int, cy: Int, timeMs: Long, moving: Boolean) {
        b.disc(cx, cy, 2, P.TIRE)
        val spin = if (moving) ((timeMs / 120) % 2).toInt() else 0
        b.set(cx - 1 + spin, cy, 0xFFB7B3A6.toInt())
    }

    private fun car(b: PixelBuffer, x: Int, y: Int, s: JourneyMarkerState, timeMs: Long) {
        val by = y - bob(s, timeMs)
        b.hline(x - 10, x + 10, y + 1, 0x55000000)
        // Conversível: o Hoodie vai sentado, cabeça para fora.
        head(b, x - 8, by - 15, s.facingRight, timeMs)
        b.outlined(x - 11, by - 8, x + 11, by - 2, P.CAR_A, P.OUTLINE)
        b.hline(x - 10, x + 10, by - 7, 0xFFF0AFC8.toInt())
        // Capô na frente, faróis acesos na ponta.
        val front = if (s.facingRight) x + 10 else x - 10
        b.set(front, by - 5, P.LAMP_LIGHT)
        b.outlined(if (s.facingRight) x + 2 else x - 6, by - 10, if (s.facingRight) x + 6 else x - 2, by - 8, P.GLASS, P.OUTLINE)
        wheel(b, x - 6, by - 1, timeMs, s.moving); wheel(b, x + 6, by - 1, timeMs, s.moving)
    }

    private fun bus(b: PixelBuffer, x: Int, y: Int, s: JourneyMarkerState, timeMs: Long) {
        val by = y - bob(s, timeMs)
        b.hline(x - 13, x + 13, y + 1, 0x55000000)
        b.outlined(x - 13, by - 16, x + 13, by - 2, P.CAR_C, P.OUTLINE)
        b.hline(x - 12, x + 12, by - 6, 0xFF3F72C4.toInt())
        // Janelas; o Hoodie na do meio.
        listOf(-10, -4, 2, 8).forEach { wx -> b.outlined(x + wx, by - 13, x + wx + 4, by - 9, P.GLASS, P.OUTLINE) }
        head(b, x - 6, by - 17, s.facingRight, timeMs, rows = 8)
        b.box(x - 12, by - 8, x + 12, by - 7, P.CAR_C)
        val front = if (s.facingRight) x + 12 else x - 12
        b.set(front, by - 4, P.LAMP_LIGHT)
        wheel(b, x - 8, by - 1, timeMs, s.moving); wheel(b, x + 8, by - 1, timeMs, s.moving)
    }

    private fun train(b: PixelBuffer, x: Int, y: Int, s: JourneyMarkerState, timeMs: Long) {
        val by = y - bob(s, timeMs)
        b.hline(x - 14, x + 14, y + 1, 0x55000000)
        b.outlined(x - 14, by - 15, x + 14, by - 2, 0xFFB58CF0.toInt(), P.OUTLINE)
        b.hline(x - 13, x + 13, by - 5, 0xFF6FE0E8.toInt())
        listOf(-11, -3, 5).forEach { wx -> b.outlined(x + wx, by - 12, x + wx + 5, by - 8, P.GLASS, P.OUTLINE) }
        head(b, x - 5, by - 16, s.facingRight, timeMs, rows = 8)
        b.box(x - 13, by - 7, x + 13, by - 6, 0xFFB58CF0.toInt())
        // Engates e rodas pequenas de trilho.
        b.set(if (s.facingRight) x + 15 else x - 15, by - 4, P.OUTLINE)
        listOf(-9, -3, 3, 9).forEach { wx -> b.disc(x + wx, by - 1, 1, P.TIRE) }
    }

    private fun bicycle(b: PixelBuffer, x: Int, y: Int, s: JourneyMarkerState, timeMs: Long) {
        b.hline(x - 11, x + 11, y + 1, 0x55000000)
        // O sprite de lado "andando" vira pedalada; sentado um pouco acima das rodas.
        val rider = DiaryHoodieMarker.sprite(if (s.facingRight) MarkerDirection.RIGHT else MarkerDirection.LEFT, s.moving, timeMs)
        b.blit(rider, x - DiaryHoodieMarker.FEET_X, y - DiaryHoodieMarker.FEET_Y - 5)
        val ink = P.OUTLINE
        val frame = 0xFFE5604F.toInt() // vermelho: não some no traço amarelo da ciclovia
        // Rodas grandes (pneu escuro + aro claro) com um raio girando.
        listOf(x - 7, x + 7).forEach { cx ->
            ring(b, cx, y - 3, 4, ink)
            ring(b, cx, y - 3, 3, 0xFFB7B3A6.toInt())
            val spin = if (s.moving) ((timeMs / 120) % 4).toInt() else 0
            val (sx, sy) = listOf(0 to -2, 2 to 0, 0 to 2, -2 to 0)[spin]
            b.line(cx, y - 3, cx + sx, y - 3 + sy, ink)
        }
        // Quadro em triângulo, selim e guidão.
        b.line(x - 7, y - 3, x - 1, y - 8, frame); b.line(x - 1, y - 8, x + 7, y - 3, frame)
        b.line(x - 7, y - 3, x + 1, y - 3, frame); b.line(x + 1, y - 3, x - 1, y - 8, frame)
        b.hline(x - 3, x, y - 9, ink)
        val bar = if (s.facingRight) x + 5 else x - 5
        b.line(bar, y - 10, bar + (if (s.facingRight) 1 else -1), y - 6, ink)
    }

    /** Aro de raio [r] (só a borda). */
    private fun ring(b: PixelBuffer, cx: Int, cy: Int, r: Int, color: Int) {
        for (dy in -r..r) for (dx in -r..r) {
            val d = dx * dx + dy * dy
            if (d <= r * r + r && d >= (r - 1) * (r - 1) + (r - 1) / 2 + 1) b.set(cx + dx, cy + dy, color)
        }
    }
}
