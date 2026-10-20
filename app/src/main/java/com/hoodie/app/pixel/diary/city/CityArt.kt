package com.hoodie.app.pixel.diary.city

import com.hoodie.app.engine.diary.city.CityTown
import com.hoodie.app.engine.diary.city.CityTown.Kind
import com.hoodie.app.engine.diary.city.CityTown.TS
import com.hoodie.app.pixel.diary.city.CityDraw.INK
import com.hoodie.app.pixel.diary.city.CityDraw.box
import com.hoodie.app.pixel.diary.city.CityDraw.c
import com.hoodie.app.pixel.diary.city.CityDraw.disc
import com.hoodie.app.pixel.diary.city.CityDraw.sprite
import com.hoodie.app.pixel.diary.city.CityDraw.text3
import com.hoodie.app.pixel.diary.city.CityDraw.textW
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Prédio pintado + o que é animado ou acende à noite nele (coordenadas relativas ao prédio). */
class PaintedBuilding(val slot: CityTown.Slot, val image: PixelBuffer, val label: String) {
    val windows = ArrayList<IntArray>()
    val chimneys = ArrayList<IntArray>()
    val lamps = ArrayList<IntArray>()
    val anims = ArrayList<Anim>()
    var cross: IntArray? = null
    data class Anim(val type: String, val x: Int, val y: Int, val w: Int = 0)
}

/** Pintura procedural da cidade (construções, objetos, veículos) no estilo do mockup aprovado. */
internal object CityArt {
    private val ROOFS = listOf(
        intArrayOf(c("#6586ce"), c("#4e69b0"), c("#86a9e8")), intArrayOf(c("#c95b4b"), c("#9a3f35"), c("#e07a66")),
        intArrayOf(c("#5e9a64"), c("#437a4b"), c("#7cbb80")), intArrayOf(c("#9a6b47"), c("#76502f"), c("#b88a62")),
    )
    private val WALLS = listOf(c("#f3ead6"), c("#efe2c6"), c("#f1e6d2"), c("#e9dcc6"))
    private val NUMBERS = mapOf("home" to "12", "house2" to "14", "house3" to "21", "house4" to "23")
    private val GLASS = c("#9dd3f0"); private val FRAME = c("#fbf7ec"); private val WHITE = 0xFFFFFFFF.toInt()

    private fun roofPitched(b: PixelBuffer, w: Int, rh: Int, rm: Int, rd: Int, rl: Int) {
        for (y in 0 until rh) {
            val inset = maxOf(0, 3 - y / 2)
            b.rect(inset, y, w - inset * 2, 1, rm)
            if (y % 4 == 3) b.rect(inset + 1, y, w - inset * 2 - 2, 1, rd)
            if (y % 4 == 2) { var x = inset + if ((y shr 2) % 2 == 1) 4 else 0; while (x < w - inset - 1) { b.set(x, y, rd); b.set(x + 7, y, rd); x += 8 } }
            if (y % 4 == 0 && y > 2) { var x = inset + 2 + if ((y shr 2) % 2 == 1) 4 else 0; while (x < w - inset - 3) { b.rect(x, y, 3, 1, rl); x += 8 } }
            b.set(inset, y, INK); b.set(w - inset - 1, y, INK)
        }
        b.rect(3, 0, w - 6, 1, INK); b.rect(4, 1, w - 8, 1, rl)
        b.rect(0, rh - 3, w, 2, rd); b.rect(0, rh - 1, w, 1, c("#c9ccd6")); b.rect(0, rh, w, 1, INK)
    }
    private fun roofFlat(b: PixelBuffer, w: Int, rh: Int, top: Int, edge: Int) {
        box(b, 0, 0, w, rh + 1, top)
        b.rect(1, 1, w - 2, 2, edge); b.rect(1, rh - 2, w - 2, 2, edge)
        var x = 4; while (x < w - 4) { b.set(x, 4 + x % 3, edge); x += 7 }
        x = 6; while (x < w - 14) { box(b, x, 5, 12, 8, c("#c9ccd6")); b.rect(x + 2, 7, 8, 1, c("#8d91a1")); b.rect(x + 2, 9, 8, 1, c("#8d91a1")); x += 26 }
    }
    private fun wall(b: PixelBuffer, w: Int, h: Int, rh: Int, color: Int, shade: Int) {
        b.rect(0, rh, w, h - rh, INK); b.rect(1, rh + 1, w - 2, h - rh - 2, color)
        b.rect(1, rh + 1, w - 2, 2, shade); b.rect(w - 3, rh + 1, 2, h - rh - 2, shade); b.rect(1, h - 4, w - 2, 3, shade)
    }
    private fun window(p: PaintedBuilding, x: Int, y: Int, w: Int = 10, h: Int = 9, glass: Int = GLASS, frame: Int = FRAME, curtain: Int? = null) {
        val b = p.image
        box(b, x, y, w, h, frame); b.rect(x + 2, y + 2, w - 4, h - 4, glass)
        if (curtain != null) { b.rect(x + 2, y + 2, 2, h - 4, curtain); b.rect(x + w - 4, y + 2, 2, h - 4, curtain) }
        b.rect(x + 2, y + 2, 2, 1, c("#e8f6ff")); b.set(x + 3, y + 3, c("#e8f6ff"))
        b.rect(x + w / 2, y + 2, 1, h - 4, frame); b.rect(x + 1, y + h, w - 2, 1, 0x401A1C33)
        p.windows += intArrayOf(x + 2, y + 2, w - 4, h - 4)
    }
    private fun door(p: PaintedBuilding, x: Int, h: Int, w: Int = 10, color: Int = c("#9a6a48"), glass: Boolean = false) {
        val b = p.image; val y = h - 16
        box(b, x - 1, y - 1, w + 2, 17, c("#5a4030"))
        b.rect(x, y, w, 15, if (glass) c("#3e5f86") else color)
        if (glass) { b.rect(x + w / 2, y, 1, 15, c("#c9d8ea")); b.rect(x + 1, y + 1, 2, 5, c("#a9c9ec")); b.rect(x + w / 2 + 2, y + 1, 1, 3, c("#a9c9ec")); p.windows += intArrayOf(x, y, w, 14) }
        else { b.rect(x + 2, y + 2, w - 4, 4, c("#b8845c")); b.rect(x + 2, y + 8, w - 4, 5, c("#b8845c")); b.rect(x + 3, y + 3, w - 6, 1, c("#c8946c")); b.set(x + w - 3, y + 8, c("#f2c45a")) }
        b.rect(x - 2, h - 1, w + 4, 1, c("#8d8578")); b.rect(x, h - 2, w, 1, c("#c96b5a"))
    }
    /** Placa com o nome; devolve [x, y, largura]. */
    private fun sign(b: PixelBuffer, text: String, cx: Int, y: Int, bg: Int, fg: Int): IntArray {
        val w = textW(text) + 6; val x = cx - w / 2
        box(b, x, y, w, 9, bg); b.rect(x + 1, y + 1, w - 2, 1, 0x2EFFFFFF); text3(b, text, x + 3, y + 2, fg)
        return intArrayOf(x, y, w)
    }
    private fun awning(b: PixelBuffer, x: Int, y: Int, w: Int, c1: Int, c2: Int) {
        var i = 0
        while (i < w) { val col = if ((i / 4) % 2 == 1) c2 else c1; b.rect(x + i, y, minOf(4, w - i), 7, col); b.rect(x + i + 1, y + 7, 2, 1, col); i += 4 }
        b.rect(x, y - 1, w, 1, INK); b.rect(x, y + 2, w, 1, 0x261A1C33)
    }
    private fun plant(b: PixelBuffer, x: Int, y: Int) { box(b, x, y + 4, 6, 5, c("#b8684c")); disc(b, x + 3, y + 2, 3, c("#4f9a58")); b.set(x + 2, y, c("#7cc06a")); b.set(x + 4, y + 2, c("#ef6f8a")) }

    /** [name] = nome do lugar do usuário neste prédio (placa); null = prédio sem visita hoje. */
    fun paint(slot: CityTown.Slot, name: String?): PaintedBuilding {
        val w = slot.pw; val h = slot.ph; val dx = slot.door * TS + 3
        val maxChars = ((w - 14) / 4).coerceAtLeast(3)
        val label = CityDraw.signText(name ?: slot.defaultName, maxChars).ifBlank { CityDraw.signText(slot.defaultName, maxChars) }
        val p = PaintedBuilding(slot, PixelBuffer(w, h), label)
        val b = p.image
        when (slot.kind) {
            Kind.HOUSE -> {
                val home = slot.id == "home"
                val (rm, rd, rl) = ROOFS[slot.roof].let { Triple(it[0], it[1], it[2]) }
                val rh = h / 2
                wall(b, w, h, rh, WALLS[slot.roof], c("#d9cdb2"))
                var y = rh + 4; while (y < h - 4) { b.rect(1, y, w - 4, 1, 0x0A1A1C33); y += 3 }
                for (col in 0 until slot.w) if (col != slot.door) {
                    val x = col * TS + 3
                    b.rect(x - 2, rh + 5, 2, 10, rd); b.rect(x + 10, rh + 5, 2, 10, rd)
                    window(p, x, rh + 5, 10, 10, GLASS, WHITE, if (home) c("#efa3c8") else c("#f6e2a8"))
                    if (home) { b.rect(col * TS + 2, rh + 16, 12, 3, c("#8a5a3a")); listOf("#ef6f8a", "#f2c45a", "#efa3c8", "#ffffff").forEachIndexed { k, f -> b.rect(col * TS + 3 + k * 3, rh + 15, 2, 2, c(f)) } }
                }
                door(p, dx, h)
                roofPitched(b, w, rh, rm, rd, rl)
                if (home) { box(b, w / 2 - 7, 7, 14, 11, WALLS[0]); b.rect(w / 2 - 8, 6, 16, 2, rd); window(p, w / 2 - 4, 9, 8, 7, GLASS, WHITE) }
                val cx = w - 16; box(b, cx, 1, 7, 10, c("#b0654f")); b.rect(cx + 1, 1, 5, 1, c("#7a3f30")); b.rect(cx + 1, 4, 5, 1, c("#9a5040"))
                p.chimneys += intArrayOf(cx + 3, 0)
                box(b, dx + 11, h - 16, 7, 5, FRAME); text3(b, NUMBERS[slot.id] ?: "1", dx + 12, h - 15, c("#3e4258"))
                b.rect(dx - 4, h - 14, 2, 2, c("#ffe9a8")); p.lamps += intArrayOf(dx - 3, h - 13)
                if (home) { box(b, dx + 1, h - 22, 8, 5, FRAME); b.rect(dx + 4, h - 20, 2, 2, c("#6586ce")); b.set(dx + 3, h - 21, c("#6586ce")); b.set(dx + 6, h - 21, c("#6586ce")) }
                else if (name != null) sign(b, CityDraw.signText(name, maxChars), w / 2, rh + 1, c("#fbf7ec"), c("#3e4258"))
            }
            Kind.BAKERY -> {
                val rh = (h * 0.46f).toInt()
                wall(b, w, h, rh, c("#f6e7c8"), c("#e2cfa8"))
                box(b, 4, rh + 12, 38, 20, c("#fff4d8")); b.rect(6, rh + 14, 34, 16, GLASS); p.windows += intArrayOf(6, rh + 14, 34, 16)
                b.rect(6, rh + 22, 34, 1, c("#e9e4dc")); b.rect(6, rh + 28, 34, 2, c("#b0b4c0"))
                for (i in 0 until 5) { b.rect(8 + i * 6, rh + 24, 5, 3, c("#c8843e")); b.rect(9 + i * 6, rh + 23, 3, 1, c("#e0a860")); b.rect(9 + i * 6, rh + 17, 4, 3, if (i % 2 == 1) c("#f6d6b0") else c("#e8a76a")) }
                awning(b, 3, rh + 6, 40, c("#d94b4b"), c("#fbf7ec"))
                door(p, dx, h)
                roofPitched(b, w, rh, c("#c86b4a"), c("#9a4e33"), c("#e08a62"))
                sign(b, label, w / 2, rh, c("#7a4a2e"), c("#ffe6a8"))
                box(b, 10, 2, 7, 10, c("#b0654f")); p.chimneys += intArrayOf(13, 0)
                box(b, 1, h - 13, 9, 11, c("#3e4258")); b.rect(2, h - 12, 7, 7, c("#2a3a2e")); text3(b, "PAO", 1, h - 11, WHITE)
                p.anims += PaintedBuilding.Anim("open", w - 18, rh + 16)
            }
            Kind.GYM -> {
                val rh = 22
                wall(b, w, h, rh, c("#e6e3dd"), c("#cbc6bd"))
                box(b, 4, rh + 14, w - 8, h - rh - 18, c("#2f4a6e"))
                for (x in 5 until w - 5) for (y in rh + 15 until h - 5) if ((x + y) % 19 <= 1) b.set(x, y, c("#5c84b6"))
                var x = 4; while (x < w - 4) { b.rect(x, rh + 14, 1, h - rh - 18, c("#dfe6ef")); x += 16 }
                p.windows += intArrayOf(5, rh + 15, w - 10, h - rh - 20)
                p.anims += PaintedBuilding.Anim("runners", 8, rh + 18, w - 16)
                door(p, dx - 1, h, 12, glass = true)
                roofFlat(b, w, rh, c("#b9bcc8"), c("#d6d8e0"))
                val s = sign(b, label, w / 2 + 6, rh + 3, c("#d84a4a"), WHITE)
                if (s[0] > 16) { box(b, s[0] - 15, s[1], 13, 9, c("#d84a4a")); b.rect(s[0] - 12, s[1] + 4, 7, 1, WHITE); b.rect(s[0] - 13, s[1] + 2, 2, 5, WHITE); b.rect(s[0] - 6, s[1] + 2, 2, 5, WHITE) }
                box(b, w - 22, rh + 3, 17, 9, INK); text3(b, "24H", w - 20, rh + 5, c("#7fd6a4"))
            }
            Kind.MARKET -> {
                val rh = 20
                wall(b, w, h, rh, c("#e7f0dc"), c("#c9d9b8"))
                for (col in 0 until slot.w) if (col != slot.door) { window(p, col * TS + 2, rh + 17, 12, 12); listOf("#e85a4a", "#f2c45a", "#7cc06a").forEachIndexed { k, f -> b.rect(col * TS + 4 + k * 3, rh + 25, 2, 2, c(f)) } }
                awning(b, 2, rh + 10, w - 4, c("#3f9a5a"), c("#fbf7ec"))
                door(p, dx - 1, h, 12, glass = true)
                roofFlat(b, w, rh, c("#c3c8b8"), c("#dfe3d6"))
                sign(b, label, w / 2, rh + 1, c("#2f7a48"), WHITE)
                for (k in 0 until 2) { val x = if (k == 1) w - 16 else 3; box(b, x, h - 8, 12, 6, c("#9a6b47")); listOf("#e85a4a", "#f2c45a", "#7cc06a", "#f08a3a", "#e85a4a").forEachIndexed { i, f -> b.rect(x + 1 + i * 2, h - 9, 2, 2, c(f)) } }
            }
            Kind.RESTAURANT -> {
                val rh = (h * 0.42f).toInt()
                wall(b, w, h, rh, c("#c9785a"), c("#a85e44"))
                var y = rh + 4; while (y < h - 4) { var x = 1 + ((y shr 2) % 2) * 3; while (x < w - 1) { b.rect(x, y, 4, 1, c("#b8684c")); x += 6 }; y += 4 }
                for (col in 0 until slot.w) if (col != slot.door) window(p, col * TS + 3, rh + 16, 10, 11, c("#ffd88a"), c("#f6e2c2"))
                awning(b, 2, rh + 9, w - 4, c("#f08a3a"), c("#fbf2dc"))
                p.anims += PaintedBuilding.Anim("bulbs", 4, rh + 8, w - 8)
                door(p, dx, h)
                roofPitched(b, w, rh, c("#8a3a32"), c("#6a2a24"), c("#a8544a"))
                sign(b, label, w / 2, rh, INK, c("#f2c45a"))
                box(b, dx + 12, h - 14, 9, 12, c("#3e4258")); b.rect(dx + 13, h - 13, 7, 6, c("#2a3a2e")); b.rect(dx + 14, h - 12, 5, 1, WHITE); b.rect(dx + 14, h - 10, 4, 1, WHITE)
                plant(b, 3, h - 11); plant(b, w - 10, h - 11)
            }
            Kind.PHARMACY -> {
                val rh = 20
                wall(b, w, h, rh, c("#f6f8f4"), c("#dfe4dc"))
                for (col in 0 until slot.w) if (col != slot.door) window(p, col * TS + 3, rh + 17, 10, 11)
                door(p, dx - 1, h, 12, glass = true)
                roofFlat(b, w, rh, c("#d6dad2"), c("#eef0ea"))
                sign(b, label, w / 2 + 6, rh + 4, c("#2f9a5a"), WHITE)
                val cx = 7; val cy = rh + 4
                box(b, cx - 1, cy - 1, 11, 11, WHITE); b.rect(cx + 3, cy + 1, 3, 7, c("#2f9a5a")); b.rect(cx + 1, cy + 3, 7, 3, c("#2f9a5a"))
                p.cross = intArrayOf(cx + 1, cy + 1, 7, 7)
                plant(b, 2, h - 11); plant(b, w - 9, h - 11)
            }
            Kind.SHOPS -> {
                val rh = 20; val half = (slot.w / 2) * TS
                wall(b, w, h, rh, c("#efe6f6"), c("#d6cae2"))
                b.rect(half, rh + 1, 1, h - rh - 2, INK)
                awning(b, 2, rh + 10, half - 4, c("#8a5ac8"), c("#f6efff")); awning(b, half + 2, rh + 10, w - half - 4, c("#2fa0a0"), c("#effcfc"))
                window(p, 4, rh + 18, 20, 12, c("#c9b6ef"))
                listOf("#ef6f8a", "#f2c45a", "#6c8ac0").forEachIndexed { k, f -> b.rect(7 + k * 5, rh + 21, 3, 6, c(f)) }
                window(p, w - 22, rh + 18, 18, 12, c("#a8e6e6"))
                door(p, dx, h)
                roofFlat(b, w, rh, c("#c8c2d2"), c("#e0dbe8"))
                val short = CityDraw.signText(label, ((w - 6) / 4).coerceAtLeast(3))
                sign(b, short, w / 2, rh + 1, c("#5a3a8a"), WHITE)
            }
            Kind.TOWER -> {
                val rh = 26
                wall(b, w, h, rh, c("#7a8cb0"), c("#61739a"))
                var y = rh + 6
                while (y < h - 28) { var x = 6; while (x < w - 10) { window(p, x, y, 8, 9, c("#a9d0f0"), c("#e6ecf6")); x += 12 }; b.rect(1, y + 10, w - 4, 1, c("#9aaccc")); y += 13 }
                val signW = textW(label) + 6; val sx = (slot.door * TS + 8 - signW / 2).coerceIn(2, w - signW - 2)
                box(b, sx, h - 26, signW, 7, INK); text3(b, label, sx + 3, h - 25, c("#f2c45a"))
                box(b, slot.door * TS - 12, h - 19, 40, 4, c("#dfe6ef"))
                door(p, dx - 3, h, 16, glass = true)
                plant(b, dx - 12, h - 11); plant(b, dx + 15, h - 11)
                roofFlat(b, w, rh, c("#9aa6c0"), c("#c3cbdc"))
                box(b, w - 34, 7, 22, 13, c("#7a86a0")); text3(b, "H", w - 25, 11, c("#f2c45a"))
                b.rect(12, 0, 1, 12, c("#3e4258")); p.anims += PaintedBuilding.Anim("flag", 13, 1)
                b.rect(w - 8, 0, 1, 8, c("#3e4258")); p.anims += PaintedBuilding.Anim("blink", w - 9, 0)
            }
            Kind.OFFICE_BRICK -> {
                val rh = 22
                wall(b, w, h, rh, c("#b8604a"), c("#9a4c3a"))
                var y = rh + 4; while (y < h - 4) { var x = 1 + ((y shr 2) % 2) * 3; while (x < w - 3) { b.rect(x, y, 4, 1, c("#a8543f")); x += 6 }; y += 4 }
                y = rh + 6; while (y < h - 24) { var x = 6; while (x < w - 10) { window(p, x, y, 9, 9, c("#a9d0f0"), c("#f0e4d6")); b.rect(x - 1, y + 9, 11, 2, c("#8a3a2a")); x += 14 }; y += 12 }
                door(p, dx - 1, h, 12, glass = true)
                roofFlat(b, w, rh, c("#8a7a74"), c("#a8968e"))
                sign(b, label, w / 2, h - 28, INK, WHITE)
            }
            Kind.OFFICE_TEAL -> {
                val rh = 20
                wall(b, w, h, rh, c("#4aa8a0"), c("#38867f"))
                var x = 4; while (x < w - 6) { window(p, x, rh + 8, 12, 14, c("#d6f2ff"), c("#eafaf8")); plant(b, x + 3, rh + 16); x += 15 }
                door(p, dx - 1, h, 12, glass = true)
                roofFlat(b, w, rh, c("#8ec9c2"), c("#b6e0da"))
                x = 8; while (x < w - 8) { b.rect(x, 4, 8, 6, c("#3e5f86")); b.rect(x + 1, 5, 6, 1, c("#7aa6d6")); x += 10 }
                sign(b, label, w / 2, h - 27, INK, c("#7fd6a4"))
            }
        }
        return p
    }

    // ───────────────────────── Objetos ─────────────────────────
    private fun tree(v: Int): PixelBuffer = sprite(26, 34) {
        val leaf = c(listOf("#4f9a58", "#3f8a68", "#5aa060", "#e08a62")[v]); val dark = c(listOf("#3a7a44", "#2f6c52", "#447f48", "#b8643e")[v]); val light = c(listOf("#7cc06a", "#6cb88a", "#8ac870", "#f6b07a")[v])
        rect(11, 22, 5, 10, INK); rect(12, 22, 3, 10, c("#8a5a3a")); rect(12, 23, 1, 8, c("#a8744c")); rect(10, 31, 7, 2, c("#6a4428"))
        val blobs = listOf(intArrayOf(13, 13, 10), intArrayOf(7, 16, 6), intArrayOf(19, 16, 6), intArrayOf(13, 7, 7), intArrayOf(9, 10, 5), intArrayOf(17, 10, 5))
        blobs.forEach { disc(this, it[0], it[1], it[2] + 1, INK) }; blobs.forEach { disc(this, it[0], it[1], it[2], leaf) }
        disc(this, 15, 17, 6, dark); disc(this, 8, 18, 3, dark); disc(this, 10, 8, 3, light); disc(this, 15, 5, 2, light); disc(this, 6, 14, 1, light)
        for (k in 0 until 10) set(5 + (CityDraw.hash(k, v, 1) * 16).toInt(), 4 + (CityDraw.hash(k, v, 2) * 16).toInt(), if (k % 2 == 1) light else dark)
        if (v == 0) listOf(8 to 12, 17 to 8, 14 to 19).forEach { (x, y) -> rect(x, y, 2, 2, c("#e85a4a")) }
    }
    private val PINE = sprite(18, 34) {
        rect(7, 26, 4, 7, INK); rect(8, 26, 2, 7, c("#8a5a3a"))
        for (k in 0 until 4) { val y = 4 + k * 6; val w = 4 + k * 3
            rect(9 - w - 1, y + 4, w * 2 + 2, 3, INK); rect(9 - w, y + 4, w * 2, 2, c("#2f7a52")); rect(9 - w + 1, y + 2, w * 2 - 2, 3, INK); rect(9 - w + 2, y + 2, w * 2 - 4, 3, c("#3f8a62")); rect(9 - w + 2, y + 2, 2, 1, c("#6cb88a")) }
        rect(8, 0, 2, 4, INK); rect(8, 1, 1, 3, c("#3f8a62"))
    }
    private val TREES = (0..3).map(::tree)

    val props: Map<String, PixelBuffer> by lazy { mapOf(
        "pine" to PINE,
        "bush" to sprite(16, 12) { box(this, 1, 3, 14, 9, c("#4f9a58")); disc(this, 5, 5, 3, c("#4f9a58")); disc(this, 11, 5, 3, c("#4f9a58")); rect(3, 4, 3, 1, c("#7cc06a")); rect(9, 6, 3, 1, c("#7cc06a")); rect(5, 8, 2, 1, c("#ef6f8a")); set(11, 4, c("#f2c45a")); rect(2, 10, 12, 1, c("#3a7a44")) },
        "hedge" to sprite(16, 11) { box(this, 0, 1, 16, 10, c("#3f8a58")); var x = 1; while (x < 15) { rect(x, 2 + x % 2, 2, 1, c("#6cb88a")); x += 3 }; rect(1, 9, 14, 1, c("#2f6c42")) },
        "bench" to sprite(20, 12) { box(this, 1, 0, 18, 5, c("#9b6b42")); rect(2, 2, 16, 1, c("#7a5030")); box(this, 0, 5, 20, 4, c("#b8845c")); rect(1, 6, 18, 1, c("#d8a47a")); rect(2, 9, 2, 3, INK); rect(16, 9, 2, 3, INK) },
        "lamp" to sprite(10, 34) { rect(4, 8, 2, 25, c("#3e4258")); rect(5, 8, 1, 25, c("#5a5e78")); box(this, 1, 0, 8, 8, c("#f6efd2")); rect(3, 2, 4, 4, c("#ffe9a8")); rect(0, 7, 10, 2, c("#3e4258")); rect(3, 31, 4, 3, c("#3e4258")) },
        "mailbox" to sprite(8, 14) { box(this, 0, 0, 8, 7, c("#d94b4b")); rect(1, 1, 6, 1, c("#f08a8a")); rect(3, 7, 2, 7, c("#5a4030")); rect(6, 2, 1, 3, c("#f2c45a")) },
        "fence" to sprite(16, 10) { rect(0, 3, 16, 2, c("#f6efe0")); rect(0, 7, 16, 1, c("#f6efe0")); var x = 1; while (x < 16) { box(this, x, 0, 3, 10, c("#fbf7ec")); x += 5 } },
        "flowers" to sprite(16, 8) { box(this, 0, 2, 16, 6, c("#8a5a3a")); listOf("#ef6f8a", "#f2c45a", "#efa3c8", "#ffffff", "#ef6f8a").forEachIndexed { k, f -> rect(1 + k * 3, 0, 2, 2, c(f)); set(2 + k * 3, 2, c("#4f9a58")) } },
        "table" to sprite(22, 26) { rect(10, 6, 2, 16, c("#5a4030")); var i = 0; while (i < 22) { rect(i, 0, 3, 7, if ((i / 3) % 2 == 1) c("#fbf2dc") else c("#f08a3a")); i += 3 }; rect(0, 6, 22, 1, INK); box(this, 4, 16, 14, 4, c("#f6efe0")); rect(7, 16, 3, 1, c("#e85a4a")); rect(3, 20, 3, 4, c("#9b6b42")); rect(16, 20, 3, 4, c("#9b6b42")) },
        "fountain" to sprite(40, 30) { box(this, 0, 10, 40, 20, c("#c9c3b4")); rect(1, 11, 38, 2, c("#e0dacb")); rect(3, 13, 34, 13, c("#6fb3e0")); box(this, 17, 2, 6, 14, c("#d9d3c6")); rect(18, 3, 1, 12, c("#f0ebe0")) },
        "trash" to sprite(8, 12) { box(this, 0, 2, 8, 10, c("#3f7a5a")); rect(0, 0, 8, 3, INK); rect(1, 1, 6, 1, c("#5e9a78")); rect(2, 5, 1, 5, c("#5e9a78")); rect(5, 5, 1, 5, c("#5e9a78")) },
        "busstop" to sprite(28, 30) { rect(1, 6, 2, 24, c("#3e4258")); rect(25, 6, 2, 24, c("#3e4258")); box(this, 0, 2, 28, 5, c("#3368a8")); box(this, 3, 8, 22, 14, c("#cfe6f6")); rect(4, 9, 6, 12, c("#e8f6ff")); box(this, 3, 20, 22, 4, c("#9b6b42")); text3(this, "BUS", 8, 25, c("#3368a8")) },
        "playground" to sprite(34, 28) { rect(3, 6, 2, 22, c("#d94b4b")); rect(29, 6, 2, 22, c("#d94b4b")); rect(3, 4, 28, 3, c("#f2c45a")); rect(12, 7, 1, 12, c("#3e4258")); rect(22, 7, 1, 12, c("#3e4258")); box(this, 9, 18, 7, 3, c("#5aa6dc")); box(this, 19, 18, 7, 3, c("#ef6f8a")); rect(4, 26, 26, 2, c("#d9c08a")) },
        "hydrant" to sprite(8, 12) { box(this, 1, 3, 6, 9, c("#d94b4b")); rect(0, 5, 8, 2, c("#d94b4b")); set(0, 5, INK); set(7, 5, INK); box(this, 2, 0, 4, 4, c("#e85a4a")); rect(2, 8, 1, 3, c("#f08a8a")) },
        "kiosk" to sprite(30, 34) { var i = 0; while (i < 30) { rect(i, 0, 3, 8, if ((i / 3) % 2 == 1) c("#fbf7ec") else c("#3368a8")); i += 3 }; rect(0, 8, 30, 1, INK); box(this, 2, 9, 26, 24, c("#e9dcc6")); rect(4, 11, 22, 9, c("#9dd3f0")); listOf("#ef6f8a", "#f2c45a", "#ffffff", "#7cc06a", "#6c8ac0").forEachIndexed { k, f -> rect(5 + k * 4, 21, 3, 5, c(f)) }; text3(this, "BANCA", 5, 28, c("#3368a8")) },
        "cart" to sprite(26, 26) { var i = 0; while (i < 26) { rect(i, 0, 3, 6, if ((i / 3) % 2 == 1) 0xFFFFFFFF.toInt() else c("#e85a4a")); i += 3 }; rect(12, 6, 1, 6, c("#5a4030")); box(this, 2, 12, 22, 9, c("#f2c45a")); text3(this, "PIPOCA", 1, 14, c("#d84a4a")); rect(3, 21, 3, 3, INK); rect(20, 21, 3, 3, INK) },
        "bikes" to sprite(28, 14) { rect(0, 12, 28, 2, c("#3e4258")); for (k in 0 until 2) { val x = 2 + k * 13; listOf(x + 3, x + 10).forEach { wx -> for (a in 0 until 16) { val ang = a * Math.PI / 8; set(wx + Math.round(Math.cos(ang) * 3).toInt(), 9 + Math.round(Math.sin(ang) * 3).toInt(), INK) } }; rect(x + 3, 6, 7, 1, if (k == 1) c("#e85a4a") else c("#3368a8")); rect(x + 6, 3, 1, 4, if (k == 1) c("#e85a4a") else c("#3368a8")); rect(x + 5, 3, 3, 1, INK) } },
        "signFlores" to sprite(40, 22) { rect(19, 8, 2, 14, c("#3e4258")); box(this, 0, 0, 40, 9, c("#2f7a48")); text3(this, "R. FLORES", 3, 2, 0xFFFFFFFF.toInt()) },
        "signAvenida" to sprite(48, 22) { rect(23, 8, 2, 14, c("#3e4258")); box(this, 0, 0, 48, 9, c("#2f7a48")); text3(this, "AV. CENTRAL", 3, 2, 0xFFFFFFFF.toInt()) },
        "pot" to sprite(10, 12) { box(this, 1, 6, 8, 6, c("#b8684c")); rect(2, 7, 6, 1, c("#d8845c")); disc(this, 5, 4, 4, c("#4f9a58")); rect(3, 2, 2, 2, c("#ef6f8a")); rect(6, 3, 2, 2, c("#f2c45a")) },
        "rock" to sprite(12, 8) { box(this, 0, 2, 12, 6, c("#a8a49a")); rect(2, 3, 4, 1, c("#c9c5ba")) },
        "dock" to sprite(32, 14) { box(this, 0, 2, 32, 8, c("#b8845c")); var x = 4; while (x < 32) { rect(x, 3, 1, 6, c("#8a5a3a")); x += 5 }; rect(2, 10, 2, 4, c("#6a4428")); rect(28, 10, 2, 4, c("#6a4428")) },
    ) }
    fun prop(kind: String, variant: Int): PixelBuffer = when (kind) {
        "tree" -> TREES[variant % TREES.size]
        "parkedCar", "curbCar" -> sideCars[variant % sideCars.size]
        else -> props.getValue(kind)
    }
    fun isTree(kind: String) = kind == "tree" || kind == "pine"

    // ───────────────────────── Veículos ─────────────────────────
    private fun carSide(body: Int, dark: Int, kind: String): PixelBuffer {
        val w = when (kind) { "pickup", "van" -> 36; "hatch" -> 28; else -> 32 }; val h = if (kind == "van") 22 else 18
        return sprite(w, h) {
            val top = if (kind == "van") 1 else 5
            if (kind == "van") { box(this, 1, 1, w - 2, h - 6, body); rect(w - 11, 3, 8, 6, GLASS); rect(4, 3, 12, 5, GLASS); rect(2, h - 9, w - 4, 1, dark) }
            else {
                box(this, 1, top + 3, w - 2, h - top - 6, body)
                val rx = when (kind) { "pickup" -> 15; "hatch" -> 5; else -> 7 }; val rw = when (kind) { "pickup" -> 15; "hatch" -> 17; else -> 18 }
                box(this, rx, 1, rw, top + 4, body); rect(rx + 2, 2, rw - 4, top + 2, GLASS); rect(rx + rw / 2, 2, 1, top + 2, body); rect(rx + 2, 2, 2, 1, c("#e8f6ff"))
                if (kind == "pickup") { rect(3, top + 4, 11, 2, dark); rect(2, top + 3, 1, 3, INK) }
                if (kind == "taxi") { box(this, 13, 0, 8, 3, FRAME); text3(this, "T", 15, 0, INK) }
            }
            rect(2, h - 9, w - 4, 1, 0x59FFFFFF); rect(2, h - 6, w - 4, 1, dark)
            box(this, 4, h - 6, 7, 6, c("#2a2d3e")); box(this, w - 11, h - 6, 7, 6, c("#2a2d3e")); rect(6, h - 4, 3, 2, c("#8d91a1")); rect(w - 9, h - 4, 3, 2, c("#8d91a1"))
            rect(w - 3, h - 10, 2, 2, c("#ffe9a8")); rect(1, h - 10, 2, 2, c("#e85a4a"))
        }
    }
    private fun carTop(body: Int, dark: Int, front: Boolean): PixelBuffer = sprite(18, 26) {
        box(this, 1, 1, 16, 24, body); rect(2, 2, 1, 22, 0x4DFFFFFF); rect(15, 2, 1, 22, dark); box(this, 3, 8, 12, 10, body)
        if (front) { rect(4, 15, 10, 3, GLASS); rect(4, 9, 10, 2, c("#6fa6c8")); rect(3, 22, 3, 2, c("#ffe9a8")); rect(12, 22, 3, 2, c("#ffe9a8")); rect(7, 22, 4, 1, c("#c9ccd6")) }
        else { rect(4, 8, 10, 3, c("#6fa6c8")); rect(4, 15, 10, 2, GLASS); rect(3, 22, 3, 2, c("#e85a4a")); rect(12, 22, 3, 2, c("#e85a4a")) }
        rect(0, 11, 1, 2, INK); rect(17, 11, 1, 2, INK)
    }
    private val PAINTS = listOf("#e85a4a" to "#b8423a", "#4f7fc9" to "#3a5f9a", "#5da86e" to "#3e7a4e", "#e9e4dc" to "#b9b3a6", "#3e4258" to "#2a2d3e", "#f08a3a" to "#c06a2a", "#8a5ac8" to "#6a3a9a")
    private val KINDS = listOf("sedan", "hatch", "pickup", "van", "sedan", "hatch")
    val sideCars: List<PixelBuffer> by lazy { PAINTS.mapIndexed { i, (a, b) -> carSide(c(a), c(b), KINDS[i % KINDS.size]) } + carSide(c("#f2c45a"), c("#c89a3a"), "taxi") }
    val topCars: List<Pair<PixelBuffer, PixelBuffer>> by lazy { PAINTS.map { (a, b) -> carTop(c(a), c(b), true) to carTop(c(a), c(b), false) } }
    val bus: PixelBuffer by lazy { sprite(58, 26) {
        box(this, 1, 2, 56, 20, c("#f2b33a")); rect(2, 3, 54, 2, c("#ffd06a"))
        for (i in 0 until 6) { rect(6 + i * 8, 6, 6, 7, GLASS); rect(6 + i * 8, 6, 2, 1, c("#e8f6ff")) }
        rect(51, 6, 4, 13, c("#3e5f86")); rect(2, 15, 54, 2, c("#3368a8")); text3(this, "LINHA 12", 16, 16, 0xFFFFFFFF.toInt())
        box(this, 7, 19, 9, 7, c("#2a2d3e")); box(this, 41, 19, 9, 7, c("#2a2d3e")); rect(55, 11, 2, 3, c("#ffe9a8")); rect(1, 11, 2, 3, c("#e85a4a"))
    } }
    private val flipCache = HashMap<PixelBuffer, PixelBuffer>()
    fun flipped(src: PixelBuffer): PixelBuffer = synchronized(flipCache) { flipCache.getOrPut(src) { CityDraw.flipped(src) } }
}
