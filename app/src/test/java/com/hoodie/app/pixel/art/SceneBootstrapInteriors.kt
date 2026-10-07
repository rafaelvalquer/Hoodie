package com.hoodie.app.pixel.art

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.art.SceneBootstrapStudio.Canvas
import com.hoodie.app.pixel.art.SceneBootstrapStudio.H
import com.hoodie.app.pixel.art.SceneBootstrapStudio.W
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Rascunhos dos interiores V3 (trem A, metrô A e ônibus) — docs/transport-art-bible.md.
 * Mesma receita para os três: janela panorâmica recortada pela máscara, banco quente com trama
 * (contraste com o Hoodie azul-claro e sem painel chapado), detalhes pintados e um oclusor
 * em primeiro plano. Azul só no céu, do lado de fora.
 */
object SceneBootstrapInteriors {
    private const val OUTLINE = 0xFF1A1C33.toInt()

    /** Céu por período (3 tons), compartilhado pelo trem e pelo ônibus. */
    data class Sky(val top: Int, val mid: Int, val low: Int)
    private val SKY = mapOf(
        DayPeriod.MORNING to Sky(0xFFE3A893.toInt(), 0xFFF0C6A4.toInt(), 0xFFF7E1BF.toInt()),
        DayPeriod.DAY to Sky(0xFF86B3D6.toInt(), 0xFFAFCFE2.toInt(), 0xFFD8E6E3.toInt()),
        DayPeriod.EVENING to Sky(0xFF5E4C7C.toInt(), 0xFFC77A78.toInt(), 0xFFEFAF7E.toInt()),
        DayPeriod.NIGHT to Sky(0xFF161C36.toInt(), 0xFF232B4A.toInt(), 0xFF333A5C.toInt()),
    )

    /** Cores da cabine (≤ ~14) — tons quentes, nada no azul do Hoodie. */
    data class Cabin(
        val ceiling: Int, val wall: Int, val wallShade: Int, val frame: Int, val frameLight: Int,
        val seat: Int, val seatPattern: Int, val seatDark: Int,
        val metal: Int, val metalLight: Int, val floor: Int, val floorDot: Int,
        val accent: Int, val lamp: Int, val silhouette: Int,
    )

    enum class Kind { TRAIN, METRO, BUS }

    // ───────────── Geometria comum ─────────────
    private const val WIN_TOP = 58
    private const val WIN_BOT = 138
    private const val BACK_TOP = 152
    private const val SEAT_Y = 228          // superfície do assento (quadril)
    private const val FLOOR_Y = 262
    private val MULLIONS = listOf(78, 158)

    private fun hash(a: Int, b: Int): Int {
        var x = a * -0x61c88647 + b * 0x45d9f3b
        x = (x xor (x ushr 16)) * 0x45d9f3b
        return x xor (x ushr 16)
    }

    private fun Canvas.moquette(x0: Int, y0: Int, x1: Int, y1: Int, c: Cabin) {
        rect(x0, y0, x1, y1, c.seat)
        // Trama de tecido de transporte: losangos em xadrez de 6 px.
        for (y in y0..y1) for (x in x0..x1) {
            val u = Math.floorMod(x + y, 6); val v = Math.floorMod(x - y, 6)
            if (u == 0 || v == 0) set(x, y, c.seatPattern)
            if (u == 0 && v == 0) set(x, y, c.seatDark)
        }
    }

    // ───────────── Fundo (pela janela) ─────────────

    private fun skyFar(p: DayPeriod, kind: Kind) = Canvas().apply {
        val s = SKY.getValue(p)
        if (kind == Kind.METRO) { rect(0, 0, W - 1, H - 1, 0xFF1E1A22.toInt()); return@apply }
        rect(0, 0, W - 1, 84, s.top); rect(0, 85, W - 1, 110, s.mid); rect(0, 111, W - 1, H - 1, s.low)
        for (x in 0 until W) { if (x % 2 == 0) { set(x, 84, s.mid); set(x + 1, 110, s.low) } }
        if (p == DayPeriod.NIGHT) listOf(20 to 66, 64 to 74, 110 to 62, 170 to 70, 210 to 64).forEach { (x, y) -> set(x, y, 0xFFF4EED6.toInt()) }
    }

    /** Trem: morros e campos; ônibus: prédios; metrô: parede do túnel com calhas e luminárias. */
    private fun bgMid(p: DayPeriod, kind: Kind, c: Cabin) = Canvas().apply {
        when (kind) {
            Kind.TRAIN -> {
                val hill = if (p == DayPeriod.NIGHT) 0xFF2E3A3A.toInt() else 0xFF8FA36B.toInt()
                val field = if (p == DayPeriod.NIGHT) 0xFF263030.toInt() else 0xFF6E8452.toInt()
                for (x in 0 until W) {
                    val top = 108 + (sin(x / 240.0 * 2 * Math.PI * 2) * 7 + sin(x / 240.0 * 2 * Math.PI * 3 + 1) * 4).roundToInt()
                    for (y in top..H - 1) setWrap(x, y, hill)
                    for (y in 124..H - 1) setWrap(x, y, field)
                    if (x % 9 == 0) for (y in 126..H - 1 step 4) setWrap(x, y, hill)       // sulcos da plantação
                }
                listOf(30, 96, 150, 204).forEach { x ->                                      // árvores e casinhas
                    for (dy in -6..6) for (dx in -6..6) if (dx * dx + dy * dy <= 36) setWrap(x + dx, 112 + dy, field)
                    rectWrap(x + 18, 112, x + 26, 120, c.wall); rectWrap(x + 17, 109, x + 27, 111, c.frame)
                    if (p == DayPeriod.NIGHT || p == DayPeriod.EVENING) setWrap(x + 21, 115, c.lamp)
                }
            }
            Kind.BUS -> {
                var x = 0; var i = 0
                val b1 = if (p == DayPeriod.NIGHT) 0xFF2E2B42.toInt() else 0xFF8A7B84.toInt()
                val win = if (p == DayPeriod.NIGHT || p == DayPeriod.EVENING) c.lamp else 0xFFD9DCD2.toInt()
                while (x < W) {
                    val h = hash(11, i); val w = 20 + Math.floorMod(h, 12); val top = 66 + Math.floorMod(h ushr 5, 40)
                    rectWrap(x, top, x + w - 2, H - 1, b1)
                    for (yy in top..H - 1) setWrap(x + w - 2, yy, OUTLINE)
                    var wy = top + 4
                    while (wy < 136) { var wx = x + 3; while (wx < x + w - 5) { if (hash(wx, wy) and 3 != 0) rectWrap(wx, wy, wx + 1, wy + 2, win); wx += 5 }; wy += 7 }
                    x += w + 3; i++
                }
            }
            Kind.METRO -> {
                rect(0, 0, W - 1, H - 1, 0xFF2E2A33.toInt())
                for (x in 0 until W) { setWrap(x, 82, 0xFF4A4450.toInt()); setWrap(x, 83, 0xFF4A4450.toInt()); setWrap(x, 120, 0xFF4A4450.toInt()) }
                for (x in 0 until W step 3) setWrap(x, 101, 0xFF4A4450.toInt())
                listOf(40, 160).forEach { x -> rectWrap(x, 92, x + 18, 95, c.lamp); rectWrap(x + 2, 96, x + 16, 96, 0xFF4A4450.toInt()) }
            }
        }
    }

    /** Plano rápido: postes de catenária (trem), postes de rua (ônibus), nervuras do túnel (metrô). */
    private fun bgNear(p: DayPeriod, kind: Kind, c: Cabin) = Canvas().apply {
        when (kind) {
            Kind.TRAIN -> listOf(60, 180).forEach { x ->
                rectWrap(x, 40, x + 2, H - 1, 0xFF3F3A3A.toInt()); rectWrap(x - 10, 70, x + 12, 71, 0xFF3F3A3A.toInt())
            }
            Kind.BUS -> listOf(20, 140).forEach { x ->
                rectWrap(x, 70, x + 2, H - 1, 0xFF3F3A3A.toInt()); rectWrap(x - 6, 68, x + 6, 70, 0xFF3F3A3A.toInt())
                rectWrap(x - 7, 71, x - 3, 72, if (p == DayPeriod.NIGHT || p == DayPeriod.EVENING) c.lamp else 0xFFD9DCD2.toInt())
            }
            Kind.METRO -> for (x in 0 until W step 40) rectWrap(x, 0, x + 5, H - 1, 0xFF15121A.toInt())
        }
    }

    // ───────────── Cabine ─────────────

    private fun vehicleBack(kind: Kind, c: Cabin) = Canvas().apply {
        // Teto com painéis e trilho; luminária central.
        rect(0, 0, W - 1, 30, c.ceiling)
        for (x in 0 until W step 40) vline(x, 0, 30, c.wallShade)
        rect(16, 10, 224, 14, c.wallShade); rect(18, 11, 222, 13, c.lamp)
        hline(0, W - 1, 30, c.frame)
        // Parede de cima com detalhe próprio de cada veículo.
        rect(0, 31, W - 1, WIN_TOP - 1, c.wall)
        for (x in 0 until W step 6) set(x, 33, c.wallShade)
        // Costuras da parede e cartazes dos dois lados (trem/metrô).
        for (x in 0 until W step 40) vline(x, 31, WIN_TOP - 4, c.wallShade)
        if (kind != Kind.BUS) listOf(10, 186).forEach { x ->
            rect(x, 38, x + 44, 52, c.ceiling); hline(x, x + 44, 37, c.frame); hline(x, x + 44, 53, c.frame)
            vline(x - 1, 38, 52, c.frame); vline(x + 45, 38, 52, c.frame)
            rect(x + 3, 41, x + 14, 49, c.seatPattern)
            for (k in 0..2) hline(x + 18, x + 18 + 22 - k * 6, 41 + k * 4, c.frameLight)
        }
        when (kind) {
            Kind.TRAIN, Kind.METRO -> {
                // Mapa da linha: faixa de estações (o trem vai da esquerda para a direita).
                rect(70, 38, 170, 52, c.ceiling); hline(70, 170, 37, c.frame); hline(70, 170, 53, c.frame); vline(69, 38, 52, c.frame); vline(171, 38, 52, c.frame)
                hline(78, 162, 45, c.accent); hline(78, 162, 46, c.accent)
                listOf(80, 100, 120, 140, 160).forEachIndexed { i, x -> rect(x - 1, 44, x + 1, 47, if (i == 2) c.seatDark else c.frame) }
            }
            Kind.BUS -> {
                // Painel "PARE" e letreiro do itinerário.
                rect(14, 38, 50, 50, c.frame); rect(16, 40, 48, 48, c.accent)
                rect(150, 38, 226, 52, c.frame); for (x in 154..222 step 4) rect(x, 43, x + 2, 47, c.lamp)
                // Cartaz no meio (propaganda), como no trem.
                rect(62, 38, 136, 52, c.ceiling); hline(62, 136, 37, c.frame); hline(62, 136, 53, c.frame); vline(61, 38, 52, c.frame); vline(137, 38, 52, c.frame)
                rect(66, 41, 80, 49, c.seatPattern)
                for (k in 0..2) hline(86, 86 + 44 - k * 12, 41 + k * 4, c.frameLight)
            }
        }
        // Janela panorâmica: moldura escura com realce em cima (luz da esquerda/topo); o vão fica transparente.
        rect(0, WIN_TOP - 3, W - 1, WIN_TOP - 1, c.frame); hline(0, W - 1, WIN_TOP - 3, c.frameLight)
        rect(0, WIN_BOT + 1, W - 1, WIN_BOT + 4, c.frame); hline(0, W - 1, WIN_BOT + 1, c.frameLight)
        MULLIONS.forEach { x -> rect(x, WIN_TOP, x + 3, WIN_BOT, c.frame); vline(x, WIN_TOP, WIN_BOT, c.frameLight) }
        // Parede de baixo e trilho de bagagem.
        rect(0, WIN_BOT + 5, W - 1, BACK_TOP - 1, c.wall)
        for (x in 3 until W step 12) set(x, WIN_BOT + 8, c.wallShade)
        for (x in 9 until W step 12) set(x, WIN_BOT + 11, c.wallShade)
        hline(0, W - 1, BACK_TOP - 1, c.wallShade)
        // Parede lateral até o chão (o ônibus mostra a parede dos dois lados do par de bancos).
        rect(0, BACK_TOP, W - 1, FLOOR_Y - 1, c.wall)
        for (y in BACK_TOP + 8 until FLOOR_Y step 14) hline(0, W - 1, y, c.wallShade)
        for (x in 0 until W step 24) vline(x, BACK_TOP, FLOOR_Y - 1, c.wallShade)
        // Encosto(s) com trama quente — o fundo direto do Hoodie.
        when (kind) {
            Kind.BUS -> listOf(64 to 144, 144 to 224).forEach { (x0, x1) ->
                moquette(x0, BACK_TOP - 6, x1, SEAT_Y - 2, c)
                rect(x0 + 6, BACK_TOP - 12, x1 - 6, BACK_TOP - 2, c.ceiling)                   // capa do encosto de cabeça
                for (x in x0 + 6..x1 - 6 step 3) set(x, BACK_TOP - 3, c.wallShade)
                for (x in x0 + 6..x1 - 6) if (x % 2 == 0) set(x, BACK_TOP - 7, c.seatPattern)   // faixa estampada da capa
                rect(x0, BACK_TOP - 14, x1, BACK_TOP - 13, c.metal); vline(x0, BACK_TOP - 14, SEAT_Y, c.frame); vline(x1, BACK_TOP - 14, SEAT_Y, c.frame)
            }
            else -> {
                moquette(0, BACK_TOP, W - 1, SEAT_Y - 2, c)
                hline(0, W - 1, BACK_TOP, c.seatDark)
                listOf(90, 150).forEach { x -> rect(x - 1, BACK_TOP + 20, x + 1, SEAT_Y + 6, c.metal); vline(x - 1, BACK_TOP + 20, SEAT_Y + 6, c.metalLight) }
            }
        }
        // Assento (almofada mais clara na borda), saia e chão com grip.
        val (sx0, sx1) = if (kind == Kind.BUS) 64 to 224 else 0 to W - 1
        rect(sx0, SEAT_Y - 1, sx1, SEAT_Y + 6, c.seatPattern); hline(sx0, sx1, SEAT_Y - 1, c.seatDark)
        hline(sx0, sx1, SEAT_Y + 6, c.seatDark)
        for (x in sx0..sx1) if (x % 3 == 0) set(x, SEAT_Y + 3, c.seat)          // costura da almofada
        rect(sx0, SEAT_Y + 7, sx1, FLOOR_Y - 1, c.seatDark)
        for (x in sx0 + 4..sx1 step 10) vline(x, SEAT_Y + 9, FLOOR_Y - 3, c.frame)
        rect(0, FLOOR_Y, W - 1, H - 1, c.floor)
        for (y in FLOOR_Y + 2 until H step 4) for (x in Math.floorMod(y, 8) / 2 until W step 6) set(x, y, c.floorDot)
        hline(0, W - 1, FLOOR_Y, c.frame)
        if (kind != Kind.BUS) { rect(0, FLOOR_Y + 14, W - 1, FLOOR_Y + 16, c.accent) }   // faixa de segurança
        // Trilho do teto com alças (trem/metrô).
        if (kind != Kind.BUS) {
            hline(0, W - 1, 34, c.metal); hline(0, W - 1, 35, c.metalLight)
            listOf(46, 102, 182).forEach { x ->
                vline(x, 36, 46, c.frame)
                for (dy in -5..5) for (dx in -5..5) { val d = dx * dx + dy * dy; if (d in 13..26) set(x + dx, 52 + dy, c.metal) }
            }
        }
    }

    /** Oclusores do primeiro plano (balançam com o veículo). */
    private fun vehicleFront(kind: Kind, c: Cabin) = Canvas().apply {
        when (kind) {
            Kind.TRAIN -> {
                rect(30, 0, 33, H - 1, c.metal); vline(30, 0, H - 1, c.metalLight)
                for (y in 12 until H step 26) hline(30, 33, y, c.metalLight)           // reflexos ao longo da barra
                passenger(10, 318, c, gripX = 31)
            }
            Kind.METRO -> passenger(226, 318, c, gripX = 214, strap = true)
            Kind.BUS -> {
                rect(18, 0, 21, H - 1, c.accent); vline(18, 0, H - 1, c.ceiling)              // barra amarela à esquerda
                for (y in 12 until H step 26) hline(18, 21, y, c.ceiling)
                // Encosto da fileira da frente: corta o Hoodie no colo.
                moquette(56, 266, 232, H - 1, c)
                rect(56, 262, 232, 268, c.ceiling); for (x in 58..230 step 3) set(x, 267, c.wallShade)
                rect(52, 260, 236, 261, c.metal); hline(52, 236, 259, c.metalLight)
                rect(56, 262, 57, H - 1, c.frame); rect(231, 262, 232, H - 1, c.frame); rect(143, 262, 145, H - 1, c.frame)
            }
        }
    }

    /** Passageiro em pé, silhueta escura cortada pela borda (segura a barra ou a alça). */
    private fun Canvas.passenger(feetX: Int, feetY: Int, c: Cabin, gripX: Int, strap: Boolean = false) {
        val s = c.silhouette
        disc(feetX, feetY - 64, 11, s)
        set(feetX - 7, feetY - 77, s); set(feetX - 6, feetY - 78, s); set(feetX + 7, feetY - 77, s); set(feetX + 6, feetY - 78, s)
        poly(s, feetX - 15 to feetY - 52, feetX + 15 to feetY - 52, feetX + 13 to feetY - 20, feetX - 13 to feetY - 20)
        rect(feetX - 12, feetY - 20, feetX - 3, feetY, s); rect(feetX + 3, feetY - 20, feetX + 12, feetY, s)
        // Braço erguido até a barra/alça.
        val handY = if (strap) 58 else feetY - 104
        line(feetX + (if (gripX > feetX) 10 else -10), feetY - 48, gripX, handY + 4, s)
        line(feetX + (if (gripX > feetX) 11 else -11), feetY - 48, gripX + 1, handY + 4, s)
        disc(gripX, handY, 3, s)
        // Contorno de luz (da esquerda) só na borda voltada para a cena.
        for (y in feetY - 75..feetY) {
            val edge = if (gripX > feetX) (feetX + 15) else (feetX - 15)
            if (y % 2 == 0) set(edge, y, c.frameLight)
        }
    }

    private fun emissive(p: DayPeriod, c: Cabin) = Canvas().apply {
        if (p == DayPeriod.EVENING || p == DayPeriod.NIGHT) rect(18, 11, 222, 13, c.lamp)
    }

    private fun masks() = Canvas().apply {
        rect(0, WIN_TOP, W - 1, WIN_BOT, 0xFFFFFFFF.toInt())
        MULLIONS.forEach { x -> rect(x, WIN_TOP, x + 3, WIN_BOT, 0) }
    }

    private fun slots(kind: Kind) = Canvas().apply {
        when (kind) {
            Kind.BUS -> { set(104, SEAT_Y, SceneSlots.SEAT_HIP); set(184, SEAT_Y, SceneSlots.NPC_SEAT_BASE) }
            Kind.TRAIN -> { set(120, SEAT_Y, SceneSlots.SEAT_HIP); set(60, SEAT_Y, SceneSlots.NPC_SEAT_BASE) }
            Kind.METRO -> { set(120, SEAT_Y, SceneSlots.SEAT_HIP); set(60, SEAT_Y, SceneSlots.NPC_SEAT_BASE) }
        }
    }

    val CABINS = mapOf(
        Kind.TRAIN to Cabin(
            ceiling = 0xFFF2EBDB.toInt(), wall = 0xFFE3D5BA.toInt(), wallShade = 0xFFC7B697.toInt(),
            frame = 0xFF4A3B3A.toInt(), frameLight = 0xFF7A6458.toInt(),
            seat = 0xFF8E2F3E.toInt(), seatPattern = 0xFFB5474F.toInt(), seatDark = 0xFF5E2231.toInt(),
            metal = 0xFFA39A8F.toInt(), metalLight = 0xFFD8D0C2.toInt(), floor = 0xFF5A4E4A.toInt(), floorDot = 0xFF6E615B.toInt(),
            accent = 0xFFE2B84A.toInt(), lamp = 0xFFFFF0C2.toInt(), silhouette = 0xFF2A2230.toInt(),
        ),
        Kind.METRO to Cabin(
            ceiling = 0xFFEDE7DA.toInt(), wall = 0xFFD8D0BF.toInt(), wallShade = 0xFFB8AE99.toInt(),
            frame = 0xFF3A3340.toInt(), frameLight = 0xFF655A63.toInt(),
            seat = 0xFFD9772E.toInt(), seatPattern = 0xFFEF9A4A.toInt(), seatDark = 0xFFA4521F.toInt(),
            metal = 0xFF9C958C.toInt(), metalLight = 0xFFD4CDC0.toInt(), floor = 0xFF4E4648.toInt(), floorDot = 0xFF62585A.toInt(),
            accent = 0xFFE8C440.toInt(), lamp = 0xFFFFE9A8.toInt(), silhouette = 0xFF231E28.toInt(),
        ),
        Kind.BUS to Cabin(
            ceiling = 0xFFF0E8D6.toInt(), wall = 0xFFE6D9BE.toInt(), wallShade = 0xFFC9B997.toInt(),
            frame = 0xFF4A3B3A.toInt(), frameLight = 0xFF7A6458.toInt(),
            seat = 0xFFB98A2A.toInt(), seatPattern = 0xFFD7A73F.toInt(), seatDark = 0xFF7C5A1A.toInt(),
            metal = 0xFFA39A8F.toInt(), metalLight = 0xFFD8D0C2.toInt(), floor = 0xFF4E4A4E.toInt(), floorDot = 0xFF625D60.toInt(),
            accent = 0xFFE8C440.toInt(), lamp = 0xFFFFF0C2.toInt(), silhouette = 0xFF2A2230.toInt(),
        ),
    )

    fun build(kind: Kind): AsepriteFile.Document {
        val c = CABINS.getValue(kind)
        val periods = listOf(DayPeriod.MORNING, DayPeriod.DAY, DayPeriod.EVENING, DayPeriod.NIGHT)
        val back = vehicleBack(kind, c).b
        val front = vehicleFront(kind, c).b
        val mask = masks().b
        val slot = slots(kind).b
        fun idx(n: String) = SceneArt.LAYERS.indexOf(n)
        val frames = periods.map { p ->
            AsepriteFile.Frame(1000, listOf(
                AsepriteFile.Cel(idx("bg_far"), 0, 0, skyFar(p, kind).b),
                AsepriteFile.Cel(idx("bg_mid"), 0, 0, bgMid(p, kind, c).b),
                AsepriteFile.Cel(idx("bg_near"), 0, 0, bgNear(p, kind, c).b),
                AsepriteFile.Cel(idx("vehicle_back"), 0, 0, back),
                AsepriteFile.Cel(idx("vehicle_front"), 0, 0, front),
                AsepriteFile.Cel(idx("emissive"), 0, 0, emissive(p, c).b),
                AsepriteFile.Cel(idx("masks"), 0, 0, mask),
                AsepriteFile.Cel(idx("slots"), 0, 0, slot),
            ))
        }
        val tags = periods.mapIndexed { i, p -> AsepriteFile.Tag(SceneArt.PERIOD_TAGS.entries.first { it.value == p }.key, i, i) }
        val palette = frames.flatMap { f -> f.cels.filter { it.layer != idx("masks") && it.layer != idx("slots") }.flatMap { it.image.pixels.filter { px -> px ushr 24 != 0 }.toList() } }.distinct()
        return AsepriteFile.Document(W, H, SceneArt.LAYERS.map { AsepriteFile.Layer(it) }, frames, tags, palette)
    }
}
