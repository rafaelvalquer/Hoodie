package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.domain.diary.clock.ClockSegment
import com.hoodie.app.domain.diary.clock.DayClockData
import com.hoodie.app.pixel.diary.clock.DayClockGeometry.ACTIVITY_MID
import com.hoodie.app.pixel.diary.clock.DayClockGeometry.FRACTION
import com.hoodie.app.pixel.diary.clock.DayClockGeometry.RADIUS
import com.hoodie.app.pixel.diary.clock.DayClockGeometry.SIZE
import com.hoodie.app.pixel.diary.clock.DayClockGeometry.angularPx
import com.hoodie.app.pixel.diary.clock.DayClockGeometry.arcPx
import com.hoodie.app.pixel.diary.journey.JourneyLightingRenderer
import com.hoodie.app.pixel.renderer.PixelBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * O que o mostrador desenha. [nowMinute] é o "agora" exibido: o minuto atual ao vivo,
 * o tempo do replay ou null (dia passado sem replay). Depois dele o anel é futuro.
 */
data class DayClockScene(
    val data: DayClockData,
    val nowMinute: Int?,
    val selectedId: String?,
    val timeMs: Long = 0L,
)

/**
 * Relógio do Dia 2.0 em PixelBuffer 104×104 (JVM, como o SceneRenderer).
 * Camada estática — placa, anel de atividades, seleção, marcações, céu, linha do
 * agora, plaquinhas e ícones — refeita só quando muda o minuto, a seleção ou o dia.
 * Camada dinâmica — Hoodie mini, brilho pulsante e respiração — copia a estática e
 * desenha por cima, sem alocar.
 */
object DayClockRenderer {
    private const val PLATE_RING_EVERY = 6f
    private val MINOR_TICK = floatArrayOf(42.1f, 43.1f)
    private val MAJOR_TICK = floatArrayOf(41.1f, 42.1f, 43.1f)

    /** Tabelas por dia (segmento por minuto e período do céu por minuto). */
    private class DayTables(val data: DayClockData) {
        val len = data.dayLengthMinutes
        val segAt = IntArray(len + 1) { -1 }.also { a ->
            data.segments.forEachIndexed { k, s -> for (m in s.startMinute until minOf(s.endMinute, len + 1)) a[m] = k }
        }
        val period = Array(len + 1) { m -> JourneyLightingRenderer.at(data.wallMinute(m.coerceAtMost(len - 1))).period }
        val tiny = data.segments.filter { it !is ClockSegment.Unknown && arcPx(it.minutes.toFloat(), len) < 1.2f }
    }

    @Volatile private var tables: DayTables? = null
    private fun tablesFor(data: DayClockData): DayTables =
        tables?.takeIf { it.data === data } ?: DayTables(data).also { tables = it }

    fun render(scene: DayClockScene): PixelBuffer {
        val static = renderStatic(scene.data, scene.nowMinute, scene.selectedId)
        return render(scene, static, PixelBuffer(SIZE, SIZE))
    }

    fun renderStatic(data: DayClockData, nowMinute: Int?, selectedId: String?, out: PixelBuffer? = null): PixelBuffer {
        val b = out?.takeIf { it.width == SIZE && it.height == SIZE } ?: PixelBuffer(SIZE, SIZE)
        val t = tablesFor(data)
        val len = t.len
        val px = b.pixels
        val selected = data.segment(selectedId)
        val futureFrom = nowMinute?.toFloat()
        for (i in px.indices) {
            val r = RADIUS[i]
            val f = FRACTION[i]
            val x = i % SIZE
            val y = i / SIZE
            val m = f * len
            px[i] = when {
                r < 29.5f -> if (r >= 3f && r % PLATE_RING_EVERY < 1f) DayClockPalette.PLATE_RING else DayClockPalette.PLATE
                r < 30.5f -> DayClockPalette.PLATE_EDGE
                r < 31.5f -> DayClockPalette.OUTLINE
                r < 41.5f -> activity(t, x, y, r, f, m, futureFrom)
                r < 43.6f -> DayClockPalette.OUTLINE
                r < 49.5f -> sky(t, x, y, r, m)
                r < DayClockGeometry.OUTER_EDGE -> DayClockPalette.OUTLINE
                else -> 0
            }
        }
        // Trechos com menos de ~1 px de arco: largura mínima de 1 px.
        if (t.tiny.isNotEmpty()) for (i in px.indices) {
            val r = RADIUS[i]
            if (r < 31.5f || r >= 41.5f) continue
            for (s in t.tiny) {
                if (futureFrom != null && s.startMinute >= futureFrom) continue
                val mid = (s.startMinute + s.minutes / 2f) / len
                if (angularPx(FRACTION[i], mid, r) < 0.6f) px[i] = colorOf(s, r)
            }
        }
        // Seleção (1,5 px para fora do anel), marcações de hora e linha do agora.
        for (i in px.indices) {
            val r = RADIUS[i]
            if (r < 29.5f || r >= 49.5f) continue
            val f = FRACTION[i]
            val m = f * len
            if (selected != null && r >= 41.5f && r < 43.0f) {
                val mid = (selected.startMinute + selected.minutes / 2f) / len
                val inside = m >= selected.startMinute && m <= selected.endMinute
                if (inside || angularPx(f, mid, r) < 0.75f) px[i] = DayClockPalette.SELECTION
            }
        }
        // Marcações de hora (mais claras e longas a cada 6 h) e linha do agora, ponto a ponto.
        data.hourMarks.forEach { mark ->
            val major = mark.hour % 6 == 0
            (if (major) MAJOR_TICK else MINOR_TICK).forEach { rr ->
                val p = DayClockGeometry.pointAt(mark.minute, rr, len)
                b.set(p.x, p.y, if (major) DayClockPalette.TICK_MAJOR else DayClockPalette.TICK)
            }
        }
        if (nowMinute != null) {
            var rr = 30f
            while (rr < 49.5f) {
                val p = DayClockGeometry.pointAt(nowMinute, rr, len)
                b.set(p.x, p.y, DayClockPalette.NOW)
                rr += 0.5f
            }
        }
        plaques(b, data)
        icons(b, data, futureFrom)
        return b
    }

    private fun activity(t: DayTables, x: Int, y: Int, r: Float, f: Float, m: Float, futureFrom: Float?): Int {
        if (futureFrom != null && m >= futureFrom) return if ((x + y) and 1 == 0) DayClockPalette.OUTLINE else DayClockPalette.DEEP
        val k = t.segAt[floor(m).toInt().coerceIn(0, t.len)]
        if (k < 0) return DayClockPalette.TRACK
        val s = t.data.segments[k]
        // Separador de 1 px entre trechos.
        if (s.startMinute > 0 && angularPx(f, s.startMinute.toFloat() / t.len, r) < 0.5f) return DayClockPalette.OUTLINE
        return colorOf(s, r, f)
    }

    private fun colorOf(s: ClockSegment, r: Float, f: Float = -1f): Int = when (s) {
        is ClockSegment.Stay -> DayClockPalette.category(s.category).let { if (r < 32.5f) it.edge else it.fill }
        is ClockSegment.Move -> when {
            r < 32.5f -> DayClockPalette.PATH_DARK
            f < 0f -> DayClockPalette.mode(s.mode)
            r >= DayClockGeometry.TRAIL.start && r < DayClockGeometry.TRAIL.endInclusive && trailOn(s.mode, f) -> DayClockPalette.mode(s.mode)
            else -> DayClockPalette.PATH
        }
        is ClockSegment.Unknown -> {
            val step = floor(f * 2 * PI * ACTIVITY_MID).toInt()
            if (f >= 0f && r >= 36f && r < 37f && step % 4 == 0) DayClockPalette.TRACK_MARK else DayClockPalette.TRACK
        }
    }

    /** Pontilhado para "a pé" (e sem meio); tracejado para bicicleta e veículos. */
    private fun trailOn(mode: MovementMode?, f: Float): Boolean {
        val s = floor(f * 2 * PI * ACTIVITY_MID).toInt()
        val dashed = mode == MovementMode.BICYCLE || (mode != null && mode.isVehicle)
        return if (dashed) s % 4 < 3 else s % 2 == 0
    }

    private fun sky(t: DayTables, x: Int, y: Int, r: Float, m: Float): Int {
        val w = 1.5f / (2 * PI.toFloat() * r) * t.len
        val p = t.period[floor(m).toInt().coerceIn(0, t.len)]
        val p1 = t.period[floor(m - w).toInt().coerceIn(0, t.len)]
        val p2 = t.period[floor(m + w).toInt().coerceIn(0, t.len)]
        if (p1 != p2) return DayClockPalette.sky(if ((x + y) and 1 == 0) p1 else p2)
        if (p == DayPeriod.NIGHT) {
            val h = ((x * 73_856_093) xor (y * 19_349_663)) and 0x7FFF_FFFF
            when (h % 31) { 0 -> return DayClockPalette.STAR; 9 -> return DayClockPalette.STAR_DIM }
        }
        return DayClockPalette.sky(p)
    }

    /** Plaquinhas 00 / 06 / 12 / 18 no anel do céu, onde a hora local cai. */
    private fun plaques(b: PixelBuffer, data: DayClockData) {
        data.hourMarks.filter { it.hour % 6 == 0 }.forEach { mark ->
            val c = DayClockGeometry.pointAt(mark.minute, 46.5f, data.dayLengthMinutes)
            val text = "%02d".format(mark.hour)
            val w = PixelDigits.width(text) + 2
            val h = PixelDigits.H + 2
            val x0 = c.x - w / 2
            val y0 = c.y - h / 2
            b.box(x0, y0, x0 + w - 1, y0 + h - 1, DayClockPalette.OUTLINE)
            PixelDigits.draw(b, text, x0 + 1, y0 + 1, DayClockPalette.DIGIT)
        }
    }

    /** Ícone 7×7 no meio do arco das paradas com pelo menos 9 px de arco. */
    private fun icons(b: PixelBuffer, data: DayClockData, futureFrom: Float?) {
        data.stays.forEach { s ->
            if (arcPx(s.minutes.toFloat(), data.dayLengthMinutes) < 9f) return@forEach
            val mid = s.startMinute + s.minutes / 2f
            if (futureFrom != null && mid >= futureFrom) return@forEach
            val c = DayClockGeometry.pointAt(mid, ACTIVITY_MID, data.dayLengthMinutes)
            ClockIcons.draw(b, s.category, c.x - 3, c.y - 3)
        }
    }

    /** Onde o Hoodie fica: no "agora" exibido, ou no fim do dia registrado. */
    fun hoodieMinute(data: DayClockData, nowMinute: Int?): Float? = when {
        nowMinute != null -> nowMinute.toFloat()
        data.isEmpty -> null
        else -> data.segments.last { it !is ClockSegment.Unknown }.let { it.endMinute.toFloat() }
    }

    /** Camada dinâmica: copia a estática e desenha brilho + Hoodie. */
    fun render(scene: DayClockScene, static: PixelBuffer, out: PixelBuffer): PixelBuffer {
        out.copyFrom(static)
        val data = scene.data
        val minute = hoodieMinute(data, scene.nowMinute) ?: return out
        val current = data.segmentAt((minute - 0.01f).coerceAtLeast(0f))
        val move = current as? ClockSegment.Move
        val moving = move != null && scene.nowMinute != null
        val c = DayClockGeometry.pointAt(minute, ACTIVITY_MID, data.dayLengthMinutes)
        // Brilho pulsante (anel de 1 px que respira entre 7 e 8 px).
        val pulse = (scene.timeMs / 500) % 2 == 0L
        val rr = if (pulse) 7f else 8f
        val color = if (pulse) DayClockPalette.GLOW else DayClockPalette.GLOW_DIM
        for (dy in -9..9) for (dx in -9..9) {
            val d = sqrt((dx * dx + dy * dy).toFloat())
            // Disco escuro atrás do Hoodie: contraste sobre qualquer cor do anel (inclusive o azul do Trabalho).
            if (d < 6.5f) {
                val x = c.x + dx; val y = c.y + dy
                if (x in 0 until SIZE && y in 0 until SIZE && out.pixels[y * SIZE + x] ushr 24 != 0) out.pixels[y * SIZE + x] = DayClockPalette.OUTLINE
            }
            if (abs(d - rr) < 0.5f) {
                val x = c.x + dx; val y = c.y + dy
                if (x in 0 until SIZE && y in 0 until SIZE && out.pixels[y * SIZE + x] ushr 24 != 0) out.pixels[y * SIZE + x] = color
            }
        }
        val ride = if (moving) ClockHoodieMarker.rideOf(move?.mode) else ClockHoodieMarker.Ride.FOOT
        val frame = ClockHoodieMarker.frameAt(scene.timeMs, moving)
        val sprite = ClockHoodieMarker.sprite(frame, ride, move?.mode)
        val bob = ClockHoodieMarker.breath(scene.timeMs, moving)
        out.blit(sprite, c.x - sprite.width / 2, c.y - sprite.height / 2 + bob)
        return out
    }
}
