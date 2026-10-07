package com.hoodie.app.pixel.art

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.Posture
import com.hoodie.app.pixel.sprite.ProceduralSpriteProvider
import com.hoodie.app.pixel.sprite.SpriteRequest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * BOOTSTRAP dos cenários de transporte V3 (docs/transport-art-bible.md): pinta por código um
 * RASCUNHO em camadas semânticas e grava o `.aseprite` em `assets-source/scenes/transport/`.
 * O arquivo é a fonte da verdade a partir daí — o Rafael repinta no Aseprite; este estúdio só
 * sobrescreve com `-PsceneBootstrap=<cena>` explícito.
 */
object SceneBootstrapStudio {
    const val W = 240
    const val H = 320

    /** Pincel com recorte em todas as primitivas. */
    class Canvas(val b: PixelBuffer = PixelBuffer(W, H)) {
        fun set(x: Int, y: Int, c: Int) { if (x in 0 until b.width && y in 0 until b.height) b.pixels[y * b.width + x] = c }
        /** Com repetição horizontal (tiles de paralaxe sem emenda). */
        fun setWrap(x: Int, y: Int, c: Int) = set(Math.floorMod(x, b.width), y, c)
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
            for (y in maxOf(0, y0)..minOf(b.height - 1, y1)) for (x in maxOf(0, x0)..minOf(b.width - 1, x1)) b.pixels[y * b.width + x] = c
        }
        fun rectWrap(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) { for (y in y0..y1) for (x in x0..x1) setWrap(x, y, c) }
        fun hline(x0: Int, x1: Int, y: Int, c: Int) = rect(minOf(x0, x1), y, maxOf(x0, x1), y, c)
        fun vline(x: Int, y0: Int, y1: Int, c: Int) = rect(x, minOf(y0, y1), x, maxOf(y0, y1), c)
        fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
            val n = maxOf(abs(x1 - x0), abs(y1 - y0)).coerceAtLeast(1)
            for (i in 0..n) set(x0 + (x1 - x0) * i / n, y0 + ((y1 - y0) * i.toFloat() / n).roundToInt(), c)
        }
        fun disc(cx: Int, cy: Int, r: Int, c: Int) { for (dy in -r..r) for (dx in -r..r) if (dx * dx + dy * dy <= r * r + r / 2) set(cx + dx, cy + dy, c) }
        fun ellipse(cx: Int, cy: Int, rx: Int, ry: Int, c: Int, ring: Boolean = false) {
            for (dy in -ry..ry) for (dx in -rx..rx) {
                val d = (dx * dx).toFloat() / (rx * rx) + (dy * dy).toFloat() / (ry * ry)
                if (if (ring) d in 0.62f..1.08f else d <= 1.08f) set(cx + dx, cy + dy, c)
            }
        }
        /** Polígono convexo ou não, por varredura (regra par-ímpar). */
        fun poly(c: Int, vararg p: Pair<Int, Int>) {
            val ys = p.map { it.second }
            for (y in ys.min()..ys.max()) {
                val xs = mutableListOf<Float>()
                for (i in p.indices) {
                    val (x0, y0) = p[i]; val (x1, y1) = p[(i + 1) % p.size]
                    if ((y0 <= y && y < y1) || (y1 <= y && y < y0)) xs += x0 + (y + 0.5f - y0) * (x1 - x0) / (y1 - y0).toFloat()
                }
                xs.sort()
                for (k in 0 until xs.size - 1 step 2) for (x in xs[k].roundToInt() until xs[k + 1].roundToInt()) set(x, y, c)
            }
        }
        fun outlinePoly(c: Int, vararg p: Pair<Int, Int>) { for (i in p.indices) { val (a, b2) = p[i]; val (x1, y1) = p[(i + 1) % p.size]; line(a, b2, x1, y1, c) } }
    }

    // ───────────── Paleta do carro (≤ 24 cores por período) ─────────────
    object CarPalette {
        const val OUTLINE = 0xFF1A1C33.toInt()
        // Carroceria quente: vermelho tijolo, luz da esquerda.
        const val BODY = 0xFFB9473B.toInt()
        const val BODY_LIGHT = 0xFFDE6A4E.toInt()
        const val BODY_DARK = 0xFF7F2E35.toInt()
        const val BODY_TOP = 0xFFEF9467.toInt()
        const val CHROME = 0xFFD9CFC0.toInt()
        const val TIRE = 0xFF2B2630.toInt()
        const val GLASS_SHINE = 0xFFB9C6C4.toInt()
        // Interior quente e escuro: o Hoodie azul-claro salta na frente.
        const val INTERIOR = 0xFF3B2C34.toInt()
        const val SEAT = 0xFF6E4034.toInt()
        const val SEAT_LIGHT = 0xFF93593F.toInt()
        const val HEADLIGHT = 0xFFFFE2A0.toInt()
        const val TAIL = 0xFFFF5B4E.toInt()
        // Rua.
        const val SIDEWALK = 0xFFA79B8C.toInt()
        const val ROAD = 0xFF4B4552.toInt()
        const val ROAD_MARK = 0xFFE6D7B3.toInt()

        /** Céu (3), silhueta distante, prédios do meio e janelas, por período. */
        data class Sky(val top: Int, val mid: Int, val low: Int, val far: Int, val building: Int, val window: Int)
        val SKY = mapOf(
            DayPeriod.MORNING to Sky(0xFFE3A893.toInt(), 0xFFF0C6A4.toInt(), 0xFFF7E1BF.toInt(), 0xFFC9A99A.toInt(), 0xFF8A7180.toInt(), 0xFFF2D7B0.toInt()),
            DayPeriod.DAY to Sky(0xFF86B3D6.toInt(), 0xFFAFCFE2.toInt(), 0xFFD8E6E3.toInt(), 0xFFA7B2BA.toInt(), 0xFF77707F.toInt(), 0xFFCFDADA.toInt()),
            DayPeriod.EVENING to Sky(0xFF5E4C7C.toInt(), 0xFFC77A78.toInt(), 0xFFEFAF7E.toInt(), 0xFF8A5E73.toInt(), 0xFF573F5A.toInt(), 0xFFFFC978.toInt()),
            DayPeriod.NIGHT to Sky(0xFF161C36.toInt(), 0xFF232B4A.toInt(), 0xFF333A5C.toInt(), 0xFF2A3050.toInt(), 0xFF2E2B42.toInt(), 0xFFFFD27A.toInt()),
        )
    }

    /** Geometria do carro B (três quartos, frente à esquerda). */
    private object Car {
        const val ROOF_Y = 152
        const val WIN_TOP = 164
        const val BELT_Y = 210
        const val WIN_FRONT_X = 96
        const val WIN_MID_X = 150
        const val WIN_REAR_X = 196
        const val WHEEL_Y = 282
        const val WHEEL_R = 19
        val WHEELS = listOf(58, 188)
    }

    private fun hash(a: Int, b: Int): Int {
        var x = a * -0x61c88647 + b * 0x45d9f3b
        x = (x xor (x ushr 16)) * 0x45d9f3b
        return x xor (x ushr 16)
    }

    // ───────────── Camadas ─────────────

    private fun carBgFar(p: DayPeriod) = Canvas().apply {
        val s = CarPalette.SKY.getValue(p)
        rect(0, 0, W - 1, 60, s.top); rect(0, 61, W - 1, 110, s.mid); rect(0, 111, W - 1, 214, s.low)
        // Transições em xadrez (2 linhas) entre as faixas do céu.
        // Transições em xadrez (3 linhas, densidade decrescente) entre as faixas do céu.
        for (x in 0 until W) {
            if (x % 2 == 0) { set(x, 60, s.mid); set(x + 1, 110, s.low) }
            if (x % 4 == 0) { set(x, 58, s.mid); set(x + 2, 108, s.low) }
            if (x % 2 == 1) { set(x, 62, s.top); set(x, 112, s.mid) }
        }
        // Silhueta distante contínua (morros + torres), sem emenda em 240.
        for (x in 0 until W) {
            val h = 18 + ((kotlin.math.sin(x / 240.0 * 2 * Math.PI * 2) * 6) + (kotlin.math.sin(x / 240.0 * 2 * Math.PI * 5) * 3)).roundToInt()
            for (y in 150 - h..214) setWrap(x, y, s.far)
        }
        listOf(30 to 34, 104 to 46, 176 to 28).forEach { (x, h) -> rectWrap(x, 150 - h, x + 9, 150, s.far); setWrap(x + 4, 149 - h, s.far) }
        if (p == DayPeriod.NIGHT) {
            listOf(14 to 10, 52 to 26, 88 to 8, 131 to 19, 170 to 6, 214 to 22, 229 to 44, 66 to 48).forEach { (x, y) -> set(x, y, CarPalette.GLASS_SHINE) }
            disc(196, 30, 6, CarPalette.HEADLIGHT); disc(199, 28, 5, s.top)
        }
        if (p == DayPeriod.EVENING) disc(54, 96, 9, CarPalette.HEADLIGHT)
        if (p == DayPeriod.DAY || p == DayPeriod.MORNING) {
            listOf(Triple(28, 34, 22), Triple(150, 52, 30)).forEach { (x, y, w) ->
                rect(x, y, x + w, y + 3, s.low); rect(x + 5, y - 3, x + w - 6, y - 1, s.low)
            }
        }
    }

    /** Prédios do meio com janelas; à noite as janelas acesas vão para `bg_glow`… aqui só a cor. */
    private fun carBgMid(p: DayPeriod) = Canvas().apply {
        val s = CarPalette.SKY.getValue(p)
        var x = 0; var i = 0
        while (x < W) {
            val h = hash(7, i)
            val w = 22 + Math.floorMod(h, 14)
            val top = 104 + Math.floorMod(h ushr 5, 40)
            rectWrap(x, top, x + w - 2, 200, s.building)
            for (yy in top..200) setWrap(x + w - 2, yy, CarPalette.OUTLINE)       // sombra à direita (luz da esquerda)
            for (xx in x until x + w - 1) setWrap(xx, top, CarPalette.OUTLINE)
            var wy = top + 5
            while (wy < 194) {
                var wx = x + 3
                while (wx < x + w - 5) {
                    val lit = p == DayPeriod.NIGHT || p == DayPeriod.EVENING && hash(wx, wy) and 1 == 0
                    if (p != DayPeriod.NIGHT || hash(wx * 3, wy) and 3 != 0) rectWrap(wx, wy, wx + 1, wy + 2, if (lit || p != DayPeriod.EVENING) s.window else CarPalette.OUTLINE)
                    wx += 5
                }
                wy += 8
            }
            x += w + 2 + Math.floorMod(h ushr 11, 6); i++
        }
    }

    /** Calçada, guia, postes e rua — o plano que corre mais rápido. */
    private fun carBgNear(p: DayPeriod) = Canvas().apply {
        val s = CarPalette.SKY.getValue(p)
        rect(0, 200, W - 1, 214, CarPalette.SIDEWALK)
        for (x in 0 until W step 16) vline(x, 201, 214, CarPalette.ROAD)          // juntas da calçada
        hline(0, W - 1, 214, CarPalette.OUTLINE)
        rect(0, 215, W - 1, H - 1, CarPalette.ROAD)
        // Textura do asfalto: pedrinhas determinísticas, sem quebrar a leitura.
        for (y in 216 until H) for (x in 0 until W) if (hash(x, y) and 63 == 0) set(x, y, if (hash(y, x) and 1 == 0) CarPalette.TIRE else CarPalette.SIDEWALK)
        for (x in 0 until W step 60) rect(x + 6, 304, x + 34, 307, CarPalette.ROAD_MARK)
        // Postes de luz (dois por tile) — o oclusor rápido do fundo.
        listOf(40, 160).forEach { px ->
            rect(px, 120, px + 2, 212, CarPalette.TIRE); rect(px - 1, 210, px + 3, 213, CarPalette.OUTLINE)
            rect(px - 8, 116, px + 3, 119, CarPalette.TIRE)
            rect(px - 9, 120, px - 5, 122, if (p == DayPeriod.NIGHT || p == DayPeriod.EVENING) CarPalette.HEADLIGHT else s.low)
        }
    }

    private fun carVehicleBack(hoodieFeet: Pair<Int, Int>, steering: Pair<Int, Int>) = Canvas().apply {
        // Interior visto pelas janelas: escuro e quente.
        rect(Car.WIN_FRONT_X - 26, Car.WIN_TOP - 4, Car.WIN_REAR_X + 16, Car.BELT_Y + 2, CarPalette.INTERIOR)
        // Encosto do motorista (atrás do Hoodie) e banco traseiro.
        rect(hoodieFeet.first + 6, Car.WIN_TOP + 8, hoodieFeet.first + 18, Car.BELT_Y, CarPalette.SEAT)
        vline(hoodieFeet.first + 6, Car.WIN_TOP + 8, Car.BELT_Y, CarPalette.SEAT_LIGHT)
        rect(Car.WIN_MID_X + 10, Car.WIN_TOP + 14, Car.WIN_REAR_X + 4, Car.BELT_Y, CarPalette.SEAT)
        for (x in Car.WIN_MID_X + 14..Car.WIN_REAR_X step 9) vline(x, Car.WIN_TOP + 16, Car.BELT_Y, CarPalette.INTERIOR)
        hline(Car.WIN_MID_X + 10, Car.WIN_REAR_X + 4, Car.WIN_TOP + 14, CarPalette.SEAT_LIGHT)
        // Janela do outro lado: céu recortado, pequeno e distante.
        rect(Car.WIN_MID_X + 12, Car.WIN_TOP + 3, Car.WIN_REAR_X - 6, Car.WIN_TOP + 10, CarPalette.GLASS_SHINE)
        // Forro do teto, retrovisor interno e borda do painel: o interior não fica chapado.
        rect(Car.WIN_FRONT_X - 26, Car.WIN_TOP - 4, Car.WIN_REAR_X + 16, Car.WIN_TOP + 2, CarPalette.SEAT)
        for (x in Car.WIN_FRONT_X - 26..Car.WIN_REAR_X + 16) if (x % 2 == 0) set(x, Car.WIN_TOP + 3, CarPalette.SEAT)
        rect(Car.WIN_FRONT_X + 6, Car.WIN_TOP + 3, Car.WIN_FRONT_X + 16, Car.WIN_TOP + 6, CarPalette.TIRE)
        vline(Car.WIN_FRONT_X + 11, Car.WIN_TOP + 1, Car.WIN_TOP + 3, CarPalette.TIRE)
        hline(Car.WIN_FRONT_X - 24, steering.first - 6, Car.BELT_Y - 9, CarPalette.SEAT_LIGHT)
        // Coluna B por dentro (moldura escura) entre as janelas.
        rect(Car.WIN_MID_X - 1, Car.WIN_TOP, Car.WIN_MID_X + 7, Car.BELT_Y, CarPalette.TIRE)
        // Volante (aro em elipse, atrás das mãos) e painel.
        ellipse(steering.first - 2, steering.second + 3, 5, 10, CarPalette.CHROME, ring = true)
        rect(Car.WIN_FRONT_X - 24, Car.BELT_Y - 8, steering.first - 6, Car.BELT_Y, CarPalette.TIRE)
        set(Car.WIN_FRONT_X - 16, Car.BELT_Y - 6, CarPalette.HEADLIGHT)
    }

    /** Carroceria em três quartos com os vãos das janelas abertos (o interior aparece por baixo). */
    private fun carVehicleFront() = Canvas().apply {
        val o = CarPalette.OUTLINE
        // Silhueta: capô (plano de cima visível), para-brisa inclinado, teto, traseira.
        poly(CarPalette.BODY, 6 to 214, 14 to 200, 70 to 196, 92 to Car.ROOF_Y, 198 to Car.ROOF_Y, 218 to 198, 234 to 204, 234 to 272, 6 to 272)
        poly(CarPalette.BODY_TOP, 10 to 206, 16 to 198, 70 to 195, 74 to 200, 12 to 208)                 // capô (luz)
        poly(CarPalette.BODY_TOP, 92 to Car.ROOF_Y, 198 to Car.ROOF_Y, 196 to Car.ROOF_Y + 4, 94 to Car.ROOF_Y + 4) // teto
        poly(CarPalette.GLASS_SHINE, 74 to 198, 92 to Car.ROOF_Y + 2, 95 to Car.ROOF_Y + 2, 78 to 199)     // para-brisa de lado
        // Calha do teto (sulco escuro com brilho em cima).
        hline(94, 198, Car.ROOF_Y + 8, CarPalette.BODY_DARK)
        for (x in 94..198) if (x % 3 == 0) set(x, Car.ROOF_Y + 7, CarPalette.BODY_LIGHT)
        // Vãos das janelas (transparentes = interior).
        poly(0, Car.WIN_FRONT_X to Car.WIN_TOP, Car.WIN_MID_X to Car.WIN_TOP, Car.WIN_MID_X to Car.BELT_Y - 2, 84 to Car.BELT_Y - 2)
        poly(0, Car.WIN_MID_X + 6 to Car.WIN_TOP, Car.WIN_REAR_X to Car.WIN_TOP, 212 to Car.BELT_Y - 2, Car.WIN_MID_X + 6 to Car.BELT_Y - 2)
        // Reflexo diagonal no vidro traseiro (luz da esquerda).
        for (k in 0..10) { set(Car.WIN_MID_X + 14 + k, Car.WIN_TOP + 26 - k * 2, CarPalette.GLASS_SHINE); set(Car.WIN_MID_X + 15 + k, Car.WIN_TOP + 26 - k * 2, CarPalette.GLASS_SHINE) }
        // Linha de cintura com brilho e vinco inferior.
        hline(8, 233, Car.BELT_Y, CarPalette.BODY_LIGHT); hline(8, 233, Car.BELT_Y + 1, CarPalette.BODY_LIGHT); hline(8, 233, Car.BELT_Y + 2, CarPalette.BODY_DARK)
        hline(8, 233, 236, CarPalette.BODY_DARK); hline(8, 233, 237, CarPalette.BODY_LIGHT)
        // Volume da lataria (luz da esquerda e de cima): faixa clara sob a cintura com borda em xadrez,
        // reflexo escuro da rua no meio e saia escura embaixo — nenhum painel fica chapado.
        rect(8, Car.BELT_Y + 3, 232, Car.BELT_Y + 9, CarPalette.BODY_LIGHT)   // linha BELT+2 fica escura: separa o friso da cintura
        for (x in 8..232) { if (x % 2 == 0) set(x, Car.BELT_Y + 10, CarPalette.BODY_LIGHT); if (x % 4 == 1) set(x, Car.BELT_Y + 11, CarPalette.BODY_LIGHT) }
        for (x in 8..232) {
            // Reflexo da cidade: banda ondulada (silhueta de prédios invertida) sobre a porta.
            val h = 3 + Math.floorMod(hash(x / 9, 3), 4)
            for (y in 240 - h..240) set(x, y, CarPalette.BODY_DARK)
            if (x % 2 == 0) set(x, 240 - h - 1, CarPalette.BODY_DARK)
        }
        // Vinco lateral (linha de caráter) de ponta a ponta: luz em cima, sombra embaixo.
        hline(8, 233, 228, CarPalette.BODY_LIGHT); for (x in 8..233) if (x % 2 == 0) set(x, 229, CarPalette.BODY_DARK)
        // Friso lateral de proteção (borracha escura com reflexo cromado).
        hline(22, 224, 246, CarPalette.TIRE); hline(22, 224, 247, CarPalette.TIRE)
        for (x in 24..222 step 5) set(x, 246, CarPalette.CHROME)
        rect(6, 254, 234, 272, CarPalette.BODY_DARK)
        for (x in 6..234) { if (x % 2 == 0) set(x, 253, CarPalette.BODY_DARK); if (x % 4 == 0) set(x, 252, CarPalette.BODY_DARK) }
        hline(6, 234, 271, CarPalette.OUTLINE)
        // Reflexo da soleira (luz rasante da rua) quebra a saia escura.
        for (x in 8..232) if (x % 2 == 0 || x % 7 == 0) set(x, 262, CarPalette.BODY)
        for (x in 10..230 step 6) set(x, 263, CarPalette.BODY)
        // Luz da esquerda: borda vertical clara na frente de cada painel.
        listOf(Car.WIN_FRONT_X - 11, Car.WIN_MID_X + 3, Car.WIN_REAR_X + 15).forEach { x -> vline(x, Car.BELT_Y + 2, 250, CarPalette.BODY_LIGHT) }
        // Portas, maçanetas, retrovisor.
        listOf(Car.WIN_FRONT_X - 12, Car.WIN_MID_X + 2, Car.WIN_REAR_X + 14).forEach { x -> vline(x, Car.BELT_Y + 2, 268, o) }
        listOf(Car.WIN_MID_X - 14, Car.WIN_REAR_X - 8).forEach { x -> rect(x, 214, x + 9, 216, CarPalette.CHROME); hline(x, x + 9, 217, o) }
        poly(CarPalette.BODY, 78 to 198, 92 to 194, 94 to 205, 80 to 206); outlinePoly(o, 78 to 198, 92 to 194, 94 to 205, 80 to 206)
        // Faróis, para-choques, lanterna.
        rect(4, 228, 20, 262, CarPalette.TIRE); rect(4, 236, 20, 237, CarPalette.CHROME); hline(4, 20, 227, o); vline(21, 228, 262, o)
        rect(6, 213, 16, 224, CarPalette.HEADLIGHT); hline(6, 16, 212, o); vline(17, 213, 224, o)
        rect(226, 212, 234, 230, CarPalette.TAIL); vline(225, 212, 230, o)
        rect(222, 248, 236, 262, CarPalette.TIRE)
        // Caixas de roda (escuras) — as rodas ficam no foreground.
        Car.WHEELS.forEach { cx -> disc(cx, Car.WHEEL_Y, Car.WHEEL_R + 4, CarPalette.TIRE); rect(cx - 30, Car.WHEEL_Y + 1, cx + 30, H - 1, 0) }
        rect(0, 273, W - 1, H - 1, 0)
        // Contorno externo da silhueta.
        outlinePoly(o, 6 to 214, 14 to 200, 70 to 196, 92 to Car.ROOF_Y, 198 to Car.ROOF_Y, 218 to 198, 234 to 204, 234 to 272, 6 to 272)
        outlinePoly(o, Car.WIN_FRONT_X to Car.WIN_TOP, Car.WIN_MID_X to Car.WIN_TOP, Car.WIN_MID_X to Car.BELT_Y - 2, 84 to Car.BELT_Y - 2)
        outlinePoly(o, Car.WIN_MID_X + 6 to Car.WIN_TOP, Car.WIN_REAR_X to Car.WIN_TOP, 212 to Car.BELT_Y - 2, Car.WIN_MID_X + 6 to Car.BELT_Y - 2)
    }

    /** Rodas e sombra: fixas no chão (não balançam com a carroceria). */
    private fun carForeground() = Canvas().apply {
        // Sombra no asfalto: elipse sólida com borda em xadrez.
        for (x in 10..230) for (y in 285..297) {
            val d = ((x - 120) / 112.0).let { it * it } + ((y - 291) / 6.5).let { it * it }
            if (d <= 0.8 || d <= 1.0 && (x + y) % 2 == 0) set(x, y, CarPalette.OUTLINE)
        }
        Car.WHEELS.forEach { cx ->
            disc(cx, Car.WHEEL_Y, Car.WHEEL_R, CarPalette.OUTLINE)
            disc(cx, Car.WHEEL_Y, Car.WHEEL_R - 2, CarPalette.TIRE)
            disc(cx, Car.WHEEL_Y, 10, CarPalette.CHROME)
            disc(cx, Car.WHEEL_Y, 3, CarPalette.OUTLINE)
            for (k in -8..8) set(cx + k, Car.WHEEL_Y - 9 + abs(k) / 3, CarPalette.GLASS_SHINE)
        }
    }

    private fun carEmissive(p: DayPeriod) = Canvas().apply {
        if (p == DayPeriod.EVENING || p == DayPeriod.NIGHT) {
            rect(7, 214, 15, 223, CarPalette.HEADLIGHT)
            rect(227, 213, 233, 229, CarPalette.TAIL)
        }
    }

    // ───────────── Documento ─────────────

    /** Posição do Hoodie sentado (pés) e da mão no volante, a partir do sprite real (CAR_IDLE, esquerda). */
    fun carHoodiePlacement(): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val f = ProceduralSpriteProvider.frame(SpriteRequest(AnimationId.CAR_IDLE, Direction.LEFT, 0, Posture.SITTING))
        var top = f.image.height
        for (y in 0 until f.image.height) if ((0 until f.image.width).any { f.image[it, y] ushr 24 != 0 }) { top = y; break }
        val feetY = Car.WIN_TOP + 5 + (f.anchors.feet.y - top)
        val feetX = (Car.WIN_FRONT_X + Car.WIN_MID_X) / 2 + 2
        val left = feetX - f.anchors.feet.x; val topY = feetY - f.anchors.feet.y
        return (feetX to feetY) to (left + f.anchors.rightHand.x to topY + f.anchors.rightHand.y)
    }

    fun car(): AsepriteFile.Document {
        val (feet, steering) = carHoodiePlacement()
        val periods = listOf(DayPeriod.MORNING, DayPeriod.DAY, DayPeriod.EVENING, DayPeriod.NIGHT)
        val back = carVehicleBack(feet, steering).b
        val front = carVehicleFront().b
        val fg = carForeground().b
        val slots = Canvas().apply {
            set(feet.first, feet.second, SceneSlots.SEAT_FEET)
            set(steering.first, steering.second, SceneSlots.STEERING)
            set(30, 300, SceneSlots.DOOR_FEET)
            Car.WHEELS.forEachIndexed { i, cx -> set(cx, Car.WHEEL_Y, SceneSlots.WHEEL_BASE + i) }
        }.b
        val layers = SceneArt.LAYERS.map { AsepriteFile.Layer(it) }
        fun idx(n: String) = SceneArt.LAYERS.indexOf(n)
        val frames = periods.map { p ->
            AsepriteFile.Frame(1000, listOf(
                AsepriteFile.Cel(idx("bg_far"), 0, 0, carBgFar(p).b),
                AsepriteFile.Cel(idx("bg_mid"), 0, 0, carBgMid(p).b),
                AsepriteFile.Cel(idx("bg_near"), 0, 0, carBgNear(p).b),
                AsepriteFile.Cel(idx("vehicle_back"), 0, 0, back),
                AsepriteFile.Cel(idx("vehicle_front"), 0, 0, front),
                AsepriteFile.Cel(idx("foreground"), 0, 0, fg),
                AsepriteFile.Cel(idx("emissive"), 0, 0, carEmissive(p).b),
                AsepriteFile.Cel(idx("slots"), 0, 0, slots),
            ))
        }
        val tags = periods.mapIndexed { i, p -> AsepriteFile.Tag(SceneArt.PERIOD_TAGS.entries.first { it.value == p }.key, i, i) }
        val palette = frames.flatMap { f -> f.cels.flatMap { it.image.pixels.filter { c -> c ushr 24 != 0 }.toList() } }.distinct()
        return AsepriteFile.Document(W, H, layers, frames, tags, palette)
    }

    val SCENES: Map<String, () -> AsepriteFile.Document> = linkedMapOf(
        "car" to ::car,
        "train" to { SceneBootstrapInteriors.build(SceneBootstrapInteriors.Kind.TRAIN) },
        "metro" to { SceneBootstrapInteriors.build(SceneBootstrapInteriors.Kind.METRO) },
        "bus" to { SceneBootstrapInteriors.build(SceneBootstrapInteriors.Kind.BUS) },
    )
}
