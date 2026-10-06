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
 * Relógio do Dia 2.0 em PixelBuffer 312×312 (pixel art fina; JVM, como o SceneRenderer).
 * Camada estática — placa, anel de atividades, seleção, marcações, céu, linha do
 * agora, plaquinhas e ícones — refeita só quando muda o minuto, a seleção ou o dia.
 * Camada dinâmica — Hoodie, brilho pulsante e respiração — copia a estática e
 * desenha por cima, sem alocar.
 */
object DayClockRenderer {
    // Raios (px lógicos, de dentro para fora).
    private const val PLATE_END = 88.5f
    private const val BEVEL_END = 92.5f
    private const val ACT_START = 94.5f
    private const val INNER_EDGE_END = 96.5f
    private const val OUTER_EDGE_START = 123.5f
    private const val ACT_END = 124.5f
    private const val SEL_END = 128.5f
    private const val TICK_END = 130.8f
    private const val SKY_END = 148.5f
    private const val PLATE_RING_EVERY = 18f
    /** Trechos com menos disto de arco ganham largura mínima. */
    private const val TINY_PX = 2.4f
    /** Ícone só em arcos com pelo menos isto. */
    private const val ICON_MIN_ARC = 24f

    /** Tabelas por dia (segmento por minuto e período do céu por minuto). */
    private class DayTables(val data: DayClockData) {
        val len = data.dayLengthMinutes
        val segAt = IntArray(len + 1) { -1 }.also { a ->
            data.segments.forEachIndexed { k, s -> for (m in s.startMinute until minOf(s.endMinute, len + 1)) a[m] = k }
        }
        val period = Array(len + 1) { m -> JourneyLightingRenderer.at(data.wallMinute(m.coerceAtMost(len - 1))).period }
        val tiny = data.segments.filter { it !is ClockSegment.Unknown && arcPx(it.minutes.toFloat(), len) < TINY_PX }
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
                r < PLATE_END -> if (r >= 9f && r % PLATE_RING_EVERY < 1f) DayClockPalette.PLATE_RING else DayClockPalette.PLATE
                r < BEVEL_END -> DayClockPalette.PLATE_EDGE
                r < ACT_START -> DayClockPalette.OUTLINE
                r < ACT_END -> activity(t, x, y, r, f, m, futureFrom)
                r < TICK_END -> DayClockPalette.OUTLINE
                r < SKY_END -> sky(t, x, y, r, m)
                r < DayClockGeometry.OUTER_EDGE -> DayClockPalette.OUTLINE
                else -> 0
            }
        }
        stars(b, t)
        // Trechos curtíssimos: largura mínima de ~2 px, desenhados como linhas radiais (custo fixo por trecho).
        t.tiny.forEach { s ->
            if (futureFrom != null && s.startMinute >= futureFrom) return@forEach
            val mid = s.startMinute + s.minutes / 2f
            var rr = ACT_START
            while (rr < ACT_END) {
                for (k in 0..1) {
                    val p = DayClockGeometry.pointAt(mid + (k - 0.5f) * 0.9f / (2 * PI.toFloat() * rr) * len, rr, len)
                    b.set(p.x, p.y, colorOf(s, rr))
                }
                rr += 0.5f
            }
        }
        // Seleção: faixa dourada de 4 px logo fora do anel.
        if (selected != null) for (i in px.indices) {
            val r = RADIUS[i]
            if (r < ACT_END || r >= SEL_END) continue
            val f = FRACTION[i]
            val m = f * len
            val mid = (selected.startMinute + selected.minutes / 2f) / len
            if ((m >= selected.startMinute && m <= selected.endMinute) || angularPx(f, mid, r) < TINY_PX) px[i] = DayClockPalette.SELECTION
        }
        // Marcações: hora = 1 × 4 px; a cada 6 h = 2 × 7 px, mais claras.
        data.hourMarks.forEach { mark ->
            val major = mark.hour % 6 == 0
            radial(b, mark.minute.toFloat(), len, if (major) ACT_END else 125.5f, if (major) 131f else 129.5f,
                widthPx = if (major) 2 else 1, color = if (major) DayClockPalette.TICK_MAJOR else DayClockPalette.TICK)
        }
        // Linha do agora: 2 px, da placa até a borda do céu.
        if (nowMinute != null) radial(b, nowMinute.toFloat(), len, 89f, SKY_END, widthPx = 2, color = DayClockPalette.NOW)
        plaques(b, data)
        icons(b, data, futureFrom)
        return b
    }

    /** Linha radial no [minute], de [r0] a [r1], com [widthPx] px de espessura (1 ou 2). */
    private fun radial(b: PixelBuffer, minute: Float, len: Int, r0: Float, r1: Float, widthPx: Int, color: Int) {
        var rr = r0
        while (rr <= r1) {
            for (k in 0 until widthPx) {
                val offset = (k - (widthPx - 1) / 2f) * 0.9f / (2 * PI.toFloat() * rr) * len
                val p = DayClockGeometry.pointAt(minute + offset, rr, len)
                b.set(p.x, p.y, color)
            }
            rr += 0.5f
        }
    }

    private fun activity(t: DayTables, x: Int, y: Int, r: Float, f: Float, m: Float, futureFrom: Float?): Int {
        // Futuro: xadrez escuro em blocos de 2 px.
        if (futureFrom != null && m >= futureFrom) return if (((x shr 1) + (y shr 1)) and 1 == 0) DayClockPalette.OUTLINE else DayClockPalette.DEEP
        val k = t.segAt[floor(m).toInt().coerceIn(0, t.len)]
        if (k < 0) return DayClockPalette.TRACK
        val s = t.data.segments[k]
        // Separador de 1 px entre trechos.
        if (s.startMinute > 0 && angularPx(f, s.startMinute.toFloat() / t.len, r) < 0.6f) return DayClockPalette.OUTLINE
        return colorOf(s, r, f)
    }

    private fun colorOf(s: ClockSegment, r: Float, f: Float = -1f): Int = when (s) {
        // Permanência: bloco sólido com borda interna de 2 px e externa de 1 px mais escuras.
        is ClockSegment.Stay -> DayClockPalette.category(s.category).let { if (r < INNER_EDGE_END || r >= OUTER_EDGE_START) it.edge else it.fill }
        is ClockSegment.Move -> when {
            r < INNER_EDGE_END || r >= OUTER_EDGE_START -> DayClockPalette.PATH_DARK
            f < 0f -> DayClockPalette.mode(s.mode)
            r >= DayClockGeometry.TRAIL.start && r < DayClockGeometry.TRAIL.endInclusive && trailOn(s.mode, f) -> DayClockPalette.mode(s.mode)
            else -> DayClockPalette.PATH
        }
        is ClockSegment.Unknown -> {
            val step = floor(f * 2 * PI * ACTIVITY_MID).toInt()
            if (f >= 0f && r >= 108.5f && r < 110.5f && step % 9 < 2) DayClockPalette.TRACK_MARK else DayClockPalette.TRACK
        }
    }

    /** Pontilhado (3 on / 3 off) para "a pé" e sem meio; tracejado (6 / 3) para bicicleta e veículos. */
    private fun trailOn(mode: MovementMode?, f: Float): Boolean {
        val s = floor(f * 2 * PI * ACTIVITY_MID).toInt()
        val dashed = mode == MovementMode.BICYCLE || (mode != null && mode.isVehicle)
        return if (dashed) s % 9 < 6 else s % 6 < 3
    }

    private fun sky(t: DayTables, x: Int, y: Int, r: Float, m: Float): Int {
        // Dithering em xadrez numa faixa de ~4 px na troca de período.
        val w = 2f / (2 * PI.toFloat() * r) * t.len
        val p = t.period[floor(m).toInt().coerceIn(0, t.len)]
        val p1 = t.period[floor(m - w).toInt().coerceIn(0, t.len)]
        val p2 = t.period[floor(m + w).toInt().coerceIn(0, t.len)]
        if (p1 != p2) return DayClockPalette.sky(if ((x + y) and 1 == 0) p1 else p2)
        return DayClockPalette.sky(p)
    }

    private fun starHash(x: Int, y: Int) = ((x * 73_856_093) xor (y * 19_349_663)) and 0x7FFF_FFFF

    /** Estrelas determinísticas no céu da noite: algumas em "+" de 3 px, outras de 1 px. */
    private fun stars(b: PixelBuffer, t: DayTables) {
        val night = DayClockPalette.sky(DayPeriod.NIGHT)
        val px = b.pixels
        fun nightAt(x: Int, y: Int): Boolean {
            if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return false
            val i = y * SIZE + x
            val r = RADIUS[i]
            return r >= TICK_END + 1f && r < SKY_END - 1f && px[i] == night
        }
        for (i in px.indices) {
            val r = RADIUS[i]
            if (r < TICK_END || r >= SKY_END) continue
            val x = i % SIZE
            val y = i / SIZE
            if (!nightAt(x, y)) continue
            when (starHash(x, y) % 233) {
                0 -> if (nightAt(x - 1, y) && nightAt(x + 1, y) && nightAt(x, y - 1) && nightAt(x, y + 1)) {
                    px[i] = DayClockPalette.STAR
                    px[i - 1] = DayClockPalette.STAR_DIM; px[i + 1] = DayClockPalette.STAR_DIM
                    px[i - SIZE] = DayClockPalette.STAR_DIM; px[i + SIZE] = DayClockPalette.STAR_DIM
                }
                1, 2, 3 -> px[i] = DayClockPalette.STAR_DIM
                4 -> px[i] = DayClockPalette.STAR
            }
        }
    }

    /** Plaquinhas 00 / 06 / 12 / 18 no anel do céu, onde a hora local cai. */
    private fun plaques(b: PixelBuffer, data: DayClockData) {
        data.hourMarks.filter { it.hour % 6 == 0 }.forEach { mark ->
            val c = DayClockGeometry.pointAt(mark.minute, DayClockGeometry.PLAQUE_RADIUS, data.dayLengthMinutes)
            val text = "%02d".format(mark.hour)
            val w = PixelDigits.width(text) + 6
            val h = PixelDigits.H + 6
            val x0 = c.x - w / 2
            val y0 = c.y - h / 2
            b.box(x0, y0, x0 + w - 1, y0 + h - 1, DayClockPalette.OUTLINE)
            // Aro interno de 1 px, como as plaquinhas da Jornada.
            b.hline(x0 + 1, x0 + w - 2, y0 + 1, DayClockPalette.PLAQUE_RIM)
            b.hline(x0 + 1, x0 + w - 2, y0 + h - 2, DayClockPalette.PLAQUE_RIM)
            b.vline(x0 + 1, y0 + 1, y0 + h - 2, DayClockPalette.PLAQUE_RIM)
            b.vline(x0 + w - 2, y0 + 1, y0 + h - 2, DayClockPalette.PLAQUE_RIM)
            PixelDigits.draw(b, text, x0 + 3, y0 + 3, DayClockPalette.DIGIT)
        }
    }

    /** Ícone 15×15 no meio do arco das paradas com pelo menos [ICON_MIN_ARC] px de arco. */
    private fun icons(b: PixelBuffer, data: DayClockData, futureFrom: Float?) {
        val half = ClockIcons.SIZE / 2
        data.stays.forEach { s ->
            if (arcPx(s.minutes.toFloat(), data.dayLengthMinutes) < ICON_MIN_ARC) return@forEach
            val mid = s.startMinute + s.minutes / 2f
            if (futureFrom != null && mid >= futureFrom) return@forEach
            val c = DayClockGeometry.pointAt(mid, ACTIVITY_MID, data.dayLengthMinutes)
            ClockIcons.draw(b, s.category, c.x - half, c.y - half)
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
        // Disco escuro (contraste sobre qualquer cor do anel) + brilho pulsante de 2 px (raio 15 ↔ 16,5).
        val pulse = (scene.timeMs / 500) % 2 == 0L
        val rr = if (pulse) 15f else 16.5f
        val glow = if (pulse) DayClockPalette.GLOW else DayClockPalette.GLOW_DIM
        val px = out.pixels
        for (dy in -18..18) for (dx in -18..18) {
            val x = c.x + dx
            val y = c.y + dy
            if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) continue
            val i = y * SIZE + x
            if (px[i] ushr 24 == 0) continue
            val d = sqrt((dx * dx + dy * dy).toFloat())
            if (d < 13f) px[i] = DayClockPalette.OUTLINE
            else if (abs(d - rr) < 1f) px[i] = glow
        }
        val state = ClockHoodieMarker.state(minute, data.dayLengthMinutes, move?.mode, moving)
        ClockHoodieMarker.paint(out, c.x, c.y, state, scene.timeMs)
        return out
    }
}
