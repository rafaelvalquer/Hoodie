package com.hoodie.app.pixel.diary.city

import com.hoodie.app.engine.diary.city.CityTown.Dir
import com.hoodie.app.pixel.diary.city.CityDraw.INK
import com.hoodie.app.pixel.diary.city.CityDraw.SOFT
import com.hoodie.app.pixel.diary.city.CityDraw.box
import com.hoodie.app.pixel.diary.city.CityDraw.c
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.HoodiePalette

/**
 * Personagens em escala de cidade (18×28): o Hoodie de moletom e mochila e os moradores.
 * Passada de 4 quadros dirigida pela distância andada; piscar; braços balançando.
 */
internal object CityCharacters {
    /** Paleta por letra do molde: o contorno, O linha interna, f pelo, s sombra, l luz, i orelha, e olho, E brilho… */
    class Palette(val colors: Map<Char, Int>, val rabbit: Boolean = false, val dog: Boolean = false, val hat: Int? = null, val glasses: Boolean = false) {
        operator fun get(ch: Char): Int? = colors[ch]
        val key = colors.values.joinToString(",") + rabbit + dog + hat + glasses
    }

    private val BASE = mapOf(
        'o' to INK, 'O' to SOFT, 'f' to HoodiePalette.FUR, 's' to HoodiePalette.FUR_SHADE, 'l' to HoodiePalette.FUR_LIGHT,
        'i' to HoodiePalette.BLUSH, 'h' to HoodiePalette.HOOD, 'H' to HoodiePalette.HOOD_SHADE, 'w' to HoodiePalette.HOOD_LIGHT,
        't' to HoodiePalette.STRING, 'e' to HoodiePalette.EYE, 'E' to 0xFFFFFFFF.toInt(), 'n' to HoodiePalette.NOSE, 'b' to HoodiePalette.BLUSH,
        'k' to HoodiePalette.HOOD_SHADE, 'p' to HoodiePalette.BACKPACK, 'P' to HoodiePalette.BACKPACK_DARK, 'q' to c("#f09a5a"),
    )
    val HOODIE = Palette(BASE)

    private fun npc(f: String, s: String, l: String, i: String, h: String, hs: String, w: String, rabbit: Boolean = false, dog: Boolean = false, hat: String? = null, glasses: Boolean = false) =
        Palette(BASE + mapOf('f' to c(f), 's' to c(s), 'l' to c(l), 'i' to c(i), 'h' to c(h), 'H' to c(hs), 'w' to c(w), 'k' to c(hs)), rabbit, dog, hat?.let(::c), glasses)

    val RESIDENTS = listOf(
        npc("#b9a3cf", "#8a72a6", "#e6dcef", "#f2b0c0", "#6c8ac0", "#46649a", "#8aa6d6", hat = "#e85a4a"),
        npc("#e8a76a", "#c07a42", "#f6d6b0", "#f2b0a0", "#80b898", "#5e9a78", "#a6d6b8", glasses = true),
        npc("#c8c4cc", "#9a96a2", "#f0eef2", "#e8a0b0", "#e07a7a", "#b85a5a", "#f2a8a8", rabbit = true),
        npc("#a0764e", "#7a5634", "#d8b48a", "#c88a6a", "#5a6a96", "#3c4a6e", "#7a8ab6", dog = true, hat = "#3368a8"),
        npc("#efe0c0", "#cdb68a", "#fff6e0", "#d89a8a", "#d86a8a", "#a84a64", "#f0a0b8", glasses = true),
        npc("#9a9aa8", "#74748a", "#d6d6e0", "#e8b0c0", "#f2c45a", "#c89a3a", "#ffe08a", rabbit = true),
        npc("#6a5a4e", "#4e4038", "#a8988a", "#c88a6a", "#3f9a5a", "#2f7a48", "#7cc06a", dog = true),
    )

    private val HEAD_F = listOf("..o..........o..", ".oio........oio.", ".oiiooooooooiio.", "olllffffffffffso", "ollffffffffffsso",
        "offfEeffffEeffso", "offfeeffffeeffso", "ofbflllnnlllfbso", "offflllOOlllfsso", ".osffffffffffso.")
    private val BODY_F = listOf("...owhhhhhhwo...", "..ohhhtwwthhHo..", "..ohhhthhthhHo..", "..ohkkkkkkkkHo..", "..ohkhhhhhhkHo..", "..oHHHHHHHHHHo..")
    private val HEAD_B = listOf("..o..........o..", ".oso........oso.", ".osooooooooooso.", "offffsffsffffsso", "offffsffsffffsso",
        "offfffffffffffso", "offfffffffffffso", "offfffffffffffso", "offfffffffffffso", ".osffffffffffso.")
    private val BODY_B = listOf("...oHHHHHHHHo...", "..oHHwwwwwwHHo..", "..ohHHHHHHHHho..", "..ohhhhhhhhhho..", "..ohhhhhhhhhho..", "..oHHHHHHHHHHo..")
    private val HEAD_S = listOf("......o...o.....", ".....oio.oio....", "....oiioooiio...", "...ollffffffso..", "..ollfffffffsso.",
        "..offfffffEefso.", "..offfffffeefso.", "..osfffffbfllllo", "..ossffffflllln.", "...ossffffOoo...")
    private val BODY_S = listOf("....owhhhhwo....", "...ohhhhhhhHo...", "...ohhhhhthHo...", "...ohhhkkkhHo...", "...ohhhhhhhHo...", "...oHHHHHHHHo...")

    private fun tpl(b: PixelBuffer, rows: List<String>, ox: Int, oy: Int, p: Palette, flip: Boolean, blink: Boolean) {
        rows.forEachIndexed { y, row ->
            for (x in 0 until minOf(16, row.length)) {
                var ch = row[x]; if (ch == '.') continue
                if (blink && (ch == 'e' || ch == 'E')) ch = if (y == 6) 'O' else 'f'
                val color = p[ch] ?: continue
                b.set(if (flip) ox + 15 - x else ox + x, oy + y, color)
            }
        }
    }
    private fun foot(b: PixelBuffer, x: Int, y: Int, p: Palette) { b.rect(x, y, 5, 3, INK); b.rect(x + 1, y, 3, 2, p['f']!!); b.rect(x + 1, y + 1, 3, 1, p['s']!!); b.set(x + 2, y + 1, p['l']!!) }
    private fun arm(b: PixelBuffer, x: Int, y: Int, p: Palette) { b.rect(x, y, 3, 5, INK); b.rect(x + 1, y, 1, 3, p['h']!!); b.set(x + 1, y + 3, p['f']!!) }

    private val cache = HashMap<String, PixelBuffer>()

    /** Linha 23 do sprite é o chão (pés). */
    fun sprite(p: Palette, dir: Dir, frame: Int, blink: Boolean, pack: Boolean): PixelBuffer {
        val key = "${p.key}|$dir|$frame|$blink|$pack"
        synchronized(cache) { cache[key]?.let { return it } }
        val b = PixelBuffer(18, 28)
        val ox = 1; val oy = 5; val lift = if (frame == 1 || frame == 3) -1 else 0
        val flip = dir == Dir.LEFT; val hy = oy + lift; val by = oy + 10 + lift
        fun fx(x: Int) = if (flip) ox + 15 - x else ox + x
        if (p.rabbit) {
            val ears = if (dir == Dir.LEFT || dir == Dir.RIGHT) listOf(6, 10) else listOf(3, 11)
            ears.forEach { x -> val ex = fx(x) - if (flip) 1 else 0; b.rect(ex, hy - 5, 3, 7, INK); b.rect(ex + 1, hy - 4, 1, 5, p['f']!!); b.rect(ex + 1, hy - 3, 1, 3, p['i']!!) }
        }
        if (dir == Dir.UP || dir == Dir.DOWN) {
            foot(b, ox + 3, oy + 16 + if (frame == 1) -1 else 0, p); foot(b, ox + 8, oy + 16 + if (frame == 3) -1 else 0, p)
        } else {
            val pos = listOf(intArrayOf(4, 8), intArrayOf(2, 10), intArrayOf(5, 7), intArrayOf(3, 9))[frame]
            val ys = listOf(intArrayOf(0, 0), intArrayOf(0, -1), intArrayOf(0, 0), intArrayOf(-1, 0))[frame]
            foot(b, if (flip) ox + 11 - pos[0] else ox + pos[0], oy + 16 + ys[0], p); foot(b, if (flip) ox + 11 - pos[1] else ox + pos[1], oy + 16 + ys[1], p)
        }
        val swing = when (frame) { 1 -> 1; 3 -> -1; else -> 0 }
        when (dir) {
            Dir.DOWN -> {
                arm(b, ox, by + 1 + if (swing > 0) -1 else 0, p); arm(b, ox + 13, by + 1 + if (swing < 0) -1 else 0, p)
                tpl(b, BODY_F, ox, by, p, false, false); tpl(b, HEAD_F, ox, hy, p, false, blink)
                if (pack) { b.rect(ox + 3, by + 1, 1, 4, p['P']!!); b.rect(ox + 12, by + 1, 1, 4, p['P']!!) }
            }
            Dir.UP -> {
                arm(b, ox, by + 1 + if (swing < 0) -1 else 0, p); arm(b, ox + 13, by + 1 + if (swing > 0) -1 else 0, p)
                tpl(b, BODY_B, ox, by, p, false, false); tpl(b, HEAD_B, ox, hy, p, false, false)
                if (!p.rabbit) { b.rect(ox + 14, by + 2, 3, 1, INK); b.rect(ox + 15, by, 1, 2, p['f']!!); b.set(ox + 15, by - 1, p['s']!!) }
                if (pack) { box(b, ox + 4, by + 1, 8, 7, p['p']!!); b.rect(ox + 5, by + 2, 6, 1, p['q']!!); box(b, ox + 5, by + 4, 6, 3, p['P']!!); b.rect(ox + 7, by + 4, 2, 1, c("#f2c45a")) }
            }
            else -> {
                listOf(2 to 13, 1 to 12, 1 to 11, 2 to 10, 3 to 9).forEachIndexed { k, (x, y) -> b.set(fx(x), oy + lift + y, if (k == 4) p['s']!! else p['f']!!); b.set(fx(x - 1), oy + lift + y, INK) }
                if (pack) { box(b, if (flip) ox + 11 else ox + 1, by - 1, 5, 7, p['p']!!); b.rect(if (flip) ox + 12 else ox + 2, by, 3, 1, p['q']!!) }
                tpl(b, BODY_S, ox, by, p, flip, false); tpl(b, HEAD_S, ox, hy, p, flip, blink)
                arm(b, fx(7 + swing) - if (flip) 2 else 0, by + 1, p)
                if (pack) b.rect(fx(7), by + 1, 1, 3, p['P']!!)
            }
        }
        p.hat?.let { hat ->
            if (dir == Dir.UP) { b.rect(ox + 2, hy + 2, 12, 3, hat); b.rect(ox + 2, hy + 2, 12, 1, INK) }
            else { b.rect(ox + 2, hy + 2, 12, 2, hat); b.rect(ox + 2, hy + 2, 12, 1, INK); if (dir == Dir.DOWN) b.rect(ox + 4, hy + 4, 8, 1, hat) else b.rect(if (flip) ox + 1 else ox + 11, hy + 4, 4, 1, hat) }
        }
        if (p.glasses && dir == Dir.DOWN) { b.rect(ox + 3, hy + 5, 4, 1, INK); b.rect(ox + 9, hy + 5, 4, 1, INK); b.rect(ox + 7, hy + 5, 2, 1, INK) }
        if (p.glasses && (dir == Dir.LEFT || dir == Dir.RIGHT)) b.rect(if (flip) fx(12) else fx(9), hy + 5, 4, 1, INK)
        if (p.dog) {
            if (dir == Dir.DOWN || dir == Dir.UP) { b.rect(ox, hy + 2, 3, 5, INK); b.rect(ox + 1, hy + 3, 1, 3, p['s']!!); b.rect(ox + 13, hy + 2, 3, 5, INK); b.rect(ox + 14, hy + 3, 1, 3, p['s']!!) }
            else { b.rect(fx(5) - if (flip) 2 else 0, hy + 2, 3, 6, INK); b.rect(fx(6), hy + 3, 1, 4, p['s']!!) }
        }
        synchronized(cache) { cache[key] = b }
        return b
    }
}
