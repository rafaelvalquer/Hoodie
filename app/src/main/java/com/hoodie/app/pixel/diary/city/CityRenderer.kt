package com.hoodie.app.pixel.diary.city

import com.hoodie.app.engine.diary.city.CityHoodieState
import com.hoodie.app.engine.diary.city.CityTown
import com.hoodie.app.engine.diary.city.CityTown.TS
import com.hoodie.app.engine.diary.city.CityTrip
import com.hoodie.app.pixel.diary.city.CityDraw.INK
import com.hoodie.app.pixel.diary.city.CityDraw.box
import com.hoodie.app.pixel.diary.city.CityDraw.c
import com.hoodie.app.pixel.diary.city.CityDraw.text3
import com.hoodie.app.pixel.renderer.PixelBuffer
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Um quadro da cidade em [out] (resolução lógica da tela): recorta o chão na câmera, desenha
 * prédios, objetos, trânsito e personagens por ordem de profundidade (linha do chão), aplica a
 * luz do horário e por cima os balões. A visão geral desenha a cidade inteira reduzida + a rota.
 */
internal class CityRenderer {
    private class Item(var base: Int, var order: Int, var draw: () -> Unit)
    private val items = ArrayList<Item>(256)
    private val tmp = FloatArray(2)

    /** [left]/[top] = canto da câmera em px de mundo; [minuteOfDay] = relógio do replay (luz). */
    fun render(
        out: PixelBuffer, scene: CityScene, ambient: CityAmbient, hoodie: CityHoodieState,
        left: Int, top: Int, seconds: Float, minuteOfDay: Int, selectedSlot: String?, reduceMotion: Boolean,
    ) {
        val w = out.width; val h = out.height
        // chão: cópia direta das linhas visíveis
        out.fill(c("#2a2f55"))
        val g = scene.ground
        for (y in 0 until h) {
            val wy = top + y; if (wy < 0 || wy >= g.height) continue
            val x0 = maxOf(0, -left); val x1 = minOf(w, g.width - left); if (x0 >= x1) continue
            System.arraycopy(g.pixels, wy * g.width + left + x0, out.pixels, y * w + x0, x1 - x0)
        }
        fun inView(x: Int, y: Int, iw: Int, ih: Int) = x + iw > left - 40 && x < left + w + 40 && y + ih > top - 40 && y < top + h + 40
        fun blit(img: PixelBuffer, x: Int, y: Int) = out.blit(img, x - left, y - top)
        fun rect(x: Int, y: Int, rw: Int, rh: Int, color: Int) = out.rect(x - left, y - top, rw, rh, color)

        // patos e chafariz (sobre a água, abaixo de tudo)
        for (k in 0 until 3) {
            val a = k * 2.1 + seconds * (0.25 + k * 0.07); val r = 18 + k * 6
            val x = (8.5 * TS + kotlin.math.cos(a) * r * 1.6).roundToInt(); val y = (33.0 * TS + sin(a) * r * 0.8).roundToInt()
            if (!inView(x, y, 8, 6)) continue
            val right = -sin(a) > 0
            rect(x - 4, y + 2, 9, 1, c("#8cc8ee")); box(out, x - 3 - left, y - 2 - top, 7, 4, c("#fbf7ec"))
            rect(if (right) x + 2 else x - 3, y - 4, 3, 3, c("#fbf7ec")); rect(if (right) x + 5 else x - 5, y - 3, 2, 1, c("#f2a03a")); rect(if (right) x + 3 else x - 2, y - 3, 1, 1, INK)
        }
        if (inView(56 * TS, 29 * TS, 40, 30)) { val k = (seconds * 4).toInt() % 3; rect(56 * TS + 26, 29 * TS - 14 - k, 2, 2, c("#d6efff")); rect(56 * TS + 22 + k, 29 * TS - 10, 1, 1, c("#d6efff")); rect(56 * TS + 30 - k, 29 * TS - 10, 1, 1, c("#d6efff")) }

        items.clear(); var order = 0
        fun add(base: Int, draw: () -> Unit) { items += Item(base, order++, draw) }
        scene.buildings.forEach { b -> val s = b.slot; if (inView(s.px, s.py, s.pw, s.ph)) add(s.py + s.ph) { blit(b.image, s.px, s.py); drawAnims(out, b, left, top, seconds) } }
        scene.props.forEach { p -> if (inView(p.x, p.y, p.image.width, p.image.height)) add(p.base) { blit(p.image, p.x, p.y) } }
        CityTown.signals.forEach { s -> if (inView(s.tx * TS, s.ty * TS - 20, 16, 40)) add(s.ty * TS + TS) { drawSignal(out, s, left, top, seconds) } }
        ambient.vehicles.forEach { v ->
            val x = ambient.vehicleX(v).roundToInt(); val y = ambient.vehicleY(v).roundToInt()
            if (inView(x, y, v.image.width, v.image.height)) add(y + v.image.height) { rect(x + 2, y + v.image.height - 2, v.image.width - 3, 2, 0x401E2440); blit(v.image, x, y) }
        }
        ambient.pigeons.forEach { p ->
            if (p.gone > 0 || !inView(p.x.toInt() - 4, p.y.toInt() - 6, 8, 8)) return@forEach
            add(p.y.toInt()) {
                val x = p.x.roundToInt(); val y = p.y.roundToInt()
                val peck = if (p.fly == 0f && ((seconds + p.hx) * 2).toInt() % 3 == 0) 1 else 0
                rect(x - 2, y - 3, 5, 3, c("#8d91a1")); rect(x - 2, y - 3, 5, 1, c("#a8acbc"))
                rect(if (p.face > 0) x + 2 else x - 3, y - 4 + peck, 2, 2, c("#6a6e80")); rect(if (p.face > 0) x + 4 else x - 4, y - 3 + peck, 1, 1, c("#f2a03a"))
                if (p.fly > 0) rect(x - 3, y - 5 - (seconds * 12).toInt() % 2, 7, 1, c("#c9ccd6")) else { rect(x - 1, y, 1, 1, c("#f2a03a")); rect(x + 1, y, 1, 1, c("#f2a03a")) }
            }
        }
        fun person(p: CityCharacters.Palette, x: Float, y: Float, dir: CityTown.Dir, walking: Boolean, distance: Float, alpha: Float, pack: Boolean, phase: Float, base: Int? = null, sitting: Boolean = false) {
            val ix = x.roundToInt(); val iy = y.roundToInt()
            if (!inView(ix - 9, iy - 28, 18, 28) || alpha <= 0.02f) return
            val frame = if (walking) (distance / 4).toInt().mod(4) else 0
            val blink = !walking && (phase % 3.2f) < 0.14f
            val img = CityCharacters.sprite(p, dir, frame, blink, pack)
            val breathe = !walking && !reduceMotion && (phase * 1.2f).toInt() % 2 == 0
            add(base ?: iy) {
                rect(ix - 6, iy - 1, 12, 2, 0x4D1E2440); rect(ix - 4, iy, 8, 1, 0x4D1E2440)
                val a = (alpha * 255).toInt()
                if (a >= 250) when {
                    sitting -> out.blitRegion(img, 0, 0, 18, 21, ix - 9 - left, iy - 21 - top)
                    breathe -> { out.blitRegion(img, 0, 0, 18, 15, ix - 9 - left, iy - 23 - top); out.blitRegion(img, 0, 15, 18, 13, ix - 9 - left, iy - 9 - top) }
                    else -> blit(img, ix - 9, iy - 24)
                } else blitAlpha(out, img, ix - 9 - left, iy - 24 - top, a)
            }
        }
        ambient.residents.forEach { r -> if (r.hidden <= 0) person(r.palette, r.x, r.y, r.dir, r.walking, r.d, 1f, false, seconds + r.blink) }
        ambient.talkers.forEachIndexed { i, t -> person(t.palette, t.x, t.y, t.dir, false, 0f, 1f, false, seconds + i * 1.3f, if (t.sitting) t.y.toInt() + 4 else null, t.sitting) }
        hoodie.trip?.takeIf { it.vehicle != CityTrip.Vehicle.WALK }?.let { trip -> drawRide(trip, hoodie, ::add, ::blit) }
            ?: if (hoodie.inside == null) person(CityCharacters.HOODIE, hoodie.x, hoodie.y, hoodie.dir, hoodie.walking, hoodie.distance, hoodie.alpha, true, seconds, hoodie.baseOverride) else Unit
        items.sortWith(compareBy<Item> { it.base }.thenBy { it.order })
        items.forEach { it.draw() }

        // fumaça e borboletas acima dos telhados
        ambient.smoke.forEach { s -> val x = s.x.roundToInt(); val y = s.y.roundToInt(); if (inView(x, y, 3, 3)) rect(x, y, if (s.life < 1) 2 else 3, if (s.life < 1) 2 else 3, CityDraw.ca("eef0f4", (180 * (1 - s.life / s.max)).toInt())) }
        listOf(Triple(4 * TS + 8, 29 * TS, c("#f2c45a")), Triple(14 * TS + 8, 33 * TS, c("#efa3c8")), Triple(8 * TS + 8, 41 * TS, 0xFFFFFFFF.toInt()), Triple(2 * TS + 8, 9 * TS, c("#c9b6ef")), Triple(10 * TS + 8, 18 * TS, c("#f2c45a")))
            .forEachIndexed { i, (cx, cy, color) ->
                val ph = i * 1.7f; val x = (cx + sin(seconds * 0.7 + ph) * 22).roundToInt(); val y = (cy + kotlin.math.cos(seconds * 0.9 + ph * 2) * 10).roundToInt()
                if (!inView(x, y, 4, 4)) return@forEachIndexed
                val open = (seconds * 8 + ph).toInt() % 2 == 1
                rect(x - if (open) 2 else 1, y, if (open) 2 else 1, 2, color); rect(x + 1, y, if (open) 2 else 1, 2, color); rect(x, y, 1, 2, INK)
            }

        // luz do horário
        val (tr, tg, tb, ta) = tint(minuteOfDay)
        if (ta > 0.005f) tintBuffer(out, tr, tg, tb, ta)
        val night = ((minuteOfDay - (18 * 60 + 15)) / 60f).coerceIn(0f, 1f)
        if (night > 0f) {
            val lit = CityDraw.ca("ffd36a", (140 * night).toInt())
            scene.buildings.forEach { b -> val s = b.slot; if (inView(s.px, s.py, s.pw, s.ph)) b.windows.forEachIndexed { k, wr -> if (CityDraw.hash(k, s.x, 3) > 0.25f) rect(s.px + wr[0], s.py + wr[1], wr[2], wr[3], lit) } }
            CityTown.lampPoints.forEach { (x, y) -> if (inView(x - 34, y - 34, 68, 80)) glow(out, x - left, y - top, 34, 0.45f * night) }
            scene.buildings.forEach { b -> b.lamps.forEach { (lx, ly) -> val x = b.slot.px + lx; val y = b.slot.py + ly; if (inView(x - 16, y - 16, 32, 32)) glow(out, x - left, y - top, 16, 0.5f * night) } }
            ambient.vehicles.forEach { v ->
                val x = ambient.vehicleX(v).roundToInt(); val y = ambient.vehicleY(v).roundToInt()
                if (!inView(x - 20, y - 20, 80, 60)) return@forEach
                if (v.lane.horizontal) glow(out, (if (v.lane.dir > 0) x + v.image.width + 6 else x - 6) - left, y + v.image.height - 9 - top, 16, 0.4f * night)
                else glow(out, x + 9 - left, (if (v.lane.dir > 0) y + v.image.height + 6 else y - 6) - top, 14, 0.35f * night)
            }
        }
        scene.buildingBySlot["pharmacy"]?.let { ph -> ph.cross?.let { cr -> if ((seconds * 2).toInt() % 2 == 0) rect(ph.slot.px + cr[0] - 1, ph.slot.py + cr[1] - 1, cr[2] + 2, cr[3] + 2, 0x597FFFAA) } }
        // pássaros e balões, sempre legíveis
        if (ambient.birdsWait <= 0) for (k in 0 until 3) {
            val bx = ambient.birdsX.roundToInt() - k * 14; val by = ambient.birdsY.roundToInt() + (k % 2) * 8 - k * 3; val wing = (seconds * 8 + k).toInt() % 2
            rect(bx, by + 40, 5, 1, 0x2E1E2440); rect(bx, by + wing, 2, 1, INK); rect(bx + 2, by + 1 - wing, 1, 1, INK); rect(bx + 3, by + wing, 2, 1, INK)
        }
        val bob = if (reduceMotion) 0 else sin(seconds * 6.0).roundToInt()
        hoodie.reaction?.let { CityDraw.bubble(out, hoodie.x.roundToInt() - left, hoodie.y.roundToInt() - 30 - bob - top, it) }
        hoodie.inside?.let { stay ->
            val s = CityTown.slotById.getValue(stay.slotId)
            rect(s.doorX * TS + 3, s.doorY * TS + 1, 10, 14, 0x8CFFE28C.toInt())
            val icon = if (stay.index == 0 && hoodie.trip == null && minuteOfDay < 6 * 60 + 60 && stay.placeType == com.hoodie.app.core.model.PlaceType.HOME) "sun" else stay.icon
            CityDraw.bubble(out, s.doorX * TS + 8 - left, s.doorY * TS - 4 - top - if (reduceMotion) 0 else (sin(seconds * 2.4) * 1.5).roundToInt(), icon)
        }
        ambient.talkers.forEachIndexed { i, t ->
            if (i < 2 && (seconds / 1.8f).toInt() % 2 == i) CityDraw.bubble(out, t.x.roundToInt() - left, t.y.roundToInt() - 30 - top, "dots")
            if (t.sitting && (seconds / 2.6f).toInt() % 3 == 0) CityDraw.bubble(out, t.x.roundToInt() - left, t.y.roundToInt() - 28 - top, "note")
        }
        selectedSlot?.let { id -> CityTown.slotById[id]?.let { s -> dashed(out, s.px - 3 - left, s.py - 3 - top, s.pw + 6, s.ph + 6, seconds) } }
    }

    /** Carro ou ônibus no lugar do Hoodie (trajetos que não são a pé). */
    private fun drawRide(trip: CityTrip, hoodie: CityHoodieState, add: (Int, () -> Unit) -> Unit, blit: (PixelBuffer, Int, Int) -> Unit) {
        val x = hoodie.x.roundToInt(); val y = hoodie.y.roundToInt()
        val img = when (hoodie.dir) {
            CityTown.Dir.LEFT, CityTown.Dir.RIGHT -> { val base = if (trip.vehicle == CityTrip.Vehicle.BUS) CityArt.bus else CityArt.sideCars[1]; if (hoodie.dir == CityTown.Dir.LEFT) CityArt.flipped(base) else base }
            CityTown.Dir.DOWN -> CityArt.topCars[1].first
            CityTown.Dir.UP -> CityArt.topCars[1].second
        }
        add(y + 2) { blit(img, x - img.width / 2, y + 2 - img.height) }
    }

    private fun drawSignal(out: PixelBuffer, s: CityTown.Signal, left: Int, top: Int, seconds: Float) {
        val x = s.tx * TS + 6 - left; val y = s.ty * TS + TS - 30 - top; val st = CityTown.light(s.horizontalAxis, seconds)
        out.rect(x + 2, y + 10, 2, 20, c("#3e4258")); box(out, x, y, 6, 13, c("#2a2d3e"))
        out.rect(x + 1, y + 1, 4, 3, if (st == 'r') c("#ff5a4a") else c("#5a2a2a"))
        out.rect(x + 1, y + 5, 4, 3, if (st == 'y') c("#ffd04a") else c("#5a502a"))
        out.rect(x + 1, y + 9, 4, 3, if (st == 'g') c("#5aef7a") else c("#2a5a32"))
    }

    private fun drawAnims(out: PixelBuffer, b: PaintedBuilding, left: Int, top: Int, seconds: Float) {
        b.anims.forEach { a ->
            val x = b.slot.px + a.x - left; val y = b.slot.py + a.y - top
            when (a.type) {
                "flag" -> { val wave = (seconds * 3).toInt() % 2; out.rect(x, y, 9, 6, INK); out.rect(x, y + 1, 8 - wave, 4, c("#3368a8")); out.rect(x + 2, y + 2, 3, 2, c("#f2c45a")) }
                "blink" -> if ((seconds * 1.5f).toInt() % 2 == 0) out.rect(x, y, 3, 2, c("#ff5a4a"))
                "open" -> { box(out, x, y, 16, 7, INK); if ((seconds * 1.2f).toInt() % 3 != 2) text3(out, "ABE", x + 2, y + 1, c("#7fd6a4")) }
                "runners" -> for (k in 0 until 4) { val rx = x + 6 + k * 24; val bob = (seconds * 6 + k).toInt() % 2; out.rect(rx, y + 2 + bob, 4, 4, c("#1a2a44")); out.rect(rx + 1, y + 6 + bob, 2, 5, c("#1a2a44")); out.rect(rx - 2, y + 13, 8, 2, c("#4a5a78")) }
                "bulbs" -> { var k = 0; while (k < a.w) { val on = ((seconds * 2).toInt() + k / 6) % 2 == 0; out.set(x + k, y + if (k % 12 != 0) 1 else 0, if (on) c("#fff4b0") else c("#c8a86a")); k += 6 } }
            }
        }
    }

    private fun dashed(out: PixelBuffer, x: Int, y: Int, w: Int, h: Int, seconds: Float) {
        val shift = (seconds * 6).toInt() % 6
        for (i in 0 until w) if ((i + shift) % 6 < 3) { out.set(x + i, y, -1); out.set(x + i, y + h - 1, -1) }
        for (i in 0 until h) if ((i + shift) % 6 < 3) { out.set(x, y + i, -1); out.set(x + w - 1, y + i, -1) }
    }

    private fun blitAlpha(out: PixelBuffer, img: PixelBuffer, dx: Int, dy: Int, alpha: Int) {
        for (y in 0 until img.height) for (x in 0 until img.width) {
            val p = img.pixels[y * img.width + x]; if (p ushr 24 == 0) continue
            out.set(dx + x, dy + y, (((p ushr 24) * alpha / 255) shl 24) or (p and 0xFFFFFF))
        }
    }

    private fun glow(out: PixelBuffer, cx: Int, cy: Int, r: Int, strength: Float) {
        val r2 = r * r
        for (y in -r..(r * 1.2f).toInt()) {
            val py = cy + y; if (py < 0 || py >= out.height) continue
            for (x in -r..r) {
                val px = cx + x; if (px < 0 || px >= out.width) continue
                val d2 = x * x + y * y; if (d2 >= r2) continue
                val k = strength * (1f - d2.toFloat() / r2)
                val i = py * out.width + px; val p = out.pixels[i]
                val rr = minOf(255, ((p shr 16) and 0xFF) + (255 * k).toInt()); val gg = minOf(255, ((p shr 8) and 0xFF) + (214 * k).toInt()); val bb = minOf(255, (p and 0xFF) + (120 * k).toInt())
                out.pixels[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
    }

    private fun tintBuffer(out: PixelBuffer, r: Float, g: Float, b: Float, a: Float) {
        val inv = 1f - a; val ar = r * a; val ag = g * a; val ab = b * a
        val px = out.pixels
        for (i in px.indices) {
            val p = px[i]
            val rr = (((p shr 16) and 0xFF) * inv + ar).toInt(); val gg = (((p shr 8) and 0xFF) * inv + ag).toInt(); val bb = ((p and 0xFF) * inv + ab).toInt()
            px[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
    }

    data class Tint(val r: Float, val g: Float, val b: Float, val a: Float)

    /** Manhã quente → dia neutro → fim de tarde dourado → noite azul. */
    fun tint(minute: Int): Tint {
        val keys = listOf(0 to Tint(20f, 28f, 80f, .46f), 360 to Tint(60f, 60f, 120f, .34f), 420 to Tint(255f, 196f, 140f, .14f), 510 to Tint(255f, 230f, 200f, .04f),
            960 to Tint(255f, 220f, 170f, .04f), 1080 to Tint(255f, 150f, 90f, .16f), 1140 to Tint(120f, 90f, 160f, .26f), 1200 to Tint(30f, 40f, 100f, .40f), 1440 to Tint(20f, 28f, 80f, .46f))
        val m = minute.coerceIn(0, 1440)
        for (i in 1 until keys.size) if (m <= keys[i].first) {
            val (m0, a) = keys[i - 1]; val (m1, b) = keys[i]; val f = (m - m0).toFloat() / (m1 - m0)
            return Tint(a.r + (b.r - a.r) * f, a.g + (b.g - a.g) * f, a.b + (b.b - a.b) * f, a.a + (b.a - a.a) * f)
        }
        return keys.last().second
    }

    /** Visão geral: a cidade inteira reduzida, a rota do dia pontilhada (feito/por fazer) e onde o Hoodie está. */
    fun renderOverview(out: PixelBuffer, scene: CityScene, hoodie: CityHoodieState, progressAt: Long, seconds: Float) {
        val o = scene.overview
        val scale = minOf(out.width.toFloat() / o.width, out.height.toFloat() / o.height)
        val ox = ((out.width - o.width * scale) / 2).toInt(); val oy = ((out.height - o.height * scale) / 2).toInt()
        out.fill(c("#2a2f55"))
        for (y in 0 until out.height) {
            val sy = ((y - oy) / scale).toInt(); if (sy < 0 || sy >= o.height) continue
            for (x in 0 until out.width) { val sx = ((x - ox) / scale).toInt(); if (sx < 0 || sx >= o.width) continue; out.pixels[y * out.width + x] = o.pixels[sy * o.width + sx] }
        }
        fun map(wx: Float, wy: Float) = intArrayOf((ox + wx * scale).roundToInt(), (oy + wy * scale).roundToInt())
        scene.plan.trips.forEach { trip ->
            val done = when { progressAt >= trip.endAt -> trip.length.toFloat(); progressAt <= trip.startAt -> 0f
                else -> com.hoodie.app.engine.diary.city.CityPlan.ease((progressAt - trip.startAt).toFloat() / (trip.endAt - trip.startAt)) * trip.length }
            var d = 0f
            while (d < trip.length) {
                CityTown.pointAt(trip.path, d, tmp); val p = map(tmp[0], tmp[1])
                out.rect(p[0] - 1, p[1] - 1, 2, 2, if (d <= done) c("#fff4c8") else CityDraw.ca("fff4c8", 90)); d += 10f
            }
        }
        scene.plan.stays.map { it.slotId }.distinct().forEach { id ->
            val b = scene.buildingBySlot.getValue(id); val s = b.slot
            val p = map(s.px + s.pw / 2f, s.py.toFloat())
            val w = CityDraw.textW(b.label) + 6
            box(out, p[0] - w / 2, p[1] - 11, w, 9, INK, c("#f2c45a")); text3(out, b.label, p[0] - w / 2 + 3, p[1] - 9, c("#f2c45a"))
        }
        val p = map(hoodie.x, hoodie.y - 8)
        val r = 5 + (sin(seconds * 5.0) * 1.5).roundToInt()
        for (a in 0 until 24) { val ang = a * Math.PI / 12; out.set(p[0] + (kotlin.math.cos(ang) * r).roundToInt(), p[1] + (sin(ang) * r).roundToInt(), c("#f2c45a")) }
        out.rect(p[0] - 1, p[1] - 1, 3, 3, c("#86a9e8"))
    }
}
