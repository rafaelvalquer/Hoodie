package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer
import kotlin.math.cos
import kotlin.math.sin

/** Paleta dos cenários: cores reduzidas, mesmo contorno escuro do personagem. */
object P {
    const val OUTLINE = 0xFF1A1C33.toInt()
    const val WHITE = 0xFFF6F3EA.toInt()
    const val WOOD = 0xFFB98055.toInt()
    const val WOOD_DARK = 0xFF8F5E3A.toInt()
    const val WOOD_LIGHT = 0xFFD39B6A.toInt()
    const val FURNITURE = 0xFF7A4A33.toInt()
    const val FURNITURE_LIGHT = 0xFF9C6445.toInt()
    const val LEAF = 0xFF4FA36A.toInt()
    const val LEAF_DARK = 0xFF2F7650.toInt()
    const val POT = 0xFFC9683F.toInt()
    const val METAL = 0xFF8A93A8.toInt()
    const val METAL_DARK = 0xFF596278.toInt()
    const val SCREEN_OFF = 0xFF23263A.toInt()
    const val SCREEN = 0xFF2E3A66.toInt()
    const val CODE_1 = 0xFF7FE0C2.toInt()
    const val CODE_2 = 0xFFF2C76B.toInt()
    const val CODE_3 = 0xFFE58AAE.toInt()
    const val LAMP_LIGHT = 0xFFFFE9A8.toInt()
    const val RED = 0xFFC9544F.toInt()
    const val YELLOW = 0xFFF2CF5B.toInt()
    const val CREAM = 0xFFF1E3C3.toInt()
}

object SceneArt {

    data class SkyColors(val top: Int, val bottom: Int, val city: Int, val cityWindow: Int?)

    fun sky(period: DayPeriod): SkyColors = when (period) {
        DayPeriod.MORNING -> SkyColors(0xFFF5C9A6.toInt(), 0xFFBFE3F2.toInt(), 0xFF8FA0C6.toInt(), null)
        DayPeriod.DAY -> SkyColors(0xFF6FC0EE.toInt(), 0xFFB9E5F7.toInt(), 0xFF7F93BF.toInt(), null)
        DayPeriod.EVENING -> SkyColors(0xFF5B4A8C.toInt(), 0xFFF0905E.toInt(), 0xFF3D3563.toInt(), 0xFFFFD27A.toInt())
        DayPeriod.NIGHT -> SkyColors(0xFF0E1330.toInt(), 0xFF27306A.toInt(), 0xFF0B0E22.toInt(), 0xFFFFD86B.toInt())
    }

    /** Céu em faixas (sem degradê suave, para manter o pixel art). */
    fun skyBands(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, period: DayPeriod) {
        val s = sky(period)
        val h = (y1 - y0 + 1).coerceAtLeast(1)
        val bands = 6
        for (y in y0..y1) {
            val band = ((y - y0) * bands / h).coerceAtMost(bands - 1)
            val color = PixelBuffer.mix(s.top, s.bottom, band / (bands - 1f))
            b.hline(x0, x1, y, color)
            // Transição pontilhada entre faixas.
            if ((y - y0) * bands % h < bands && band > 0) {
                b.dither(x0, y, x1, y, PixelBuffer.mix(s.top, s.bottom, (band - 1) / (bands - 1f)), y)
            }
        }
        when (period) {
            DayPeriod.NIGHT -> {
                val stars = intArrayOf(7, 3, 23, 9, 41, 5, 58, 14, 77, 6, 96, 11, 113, 4, 131, 13, 149, 7, 171, 10, 188, 3, 207, 12, 226, 6)
                for (i in stars.indices step 2) {
                    val sx = x0 + stars[i] % (x1 - x0 + 1); val sy = y0 + stars[i + 1] % ((y1 - y0) / 2 + 1)
                    b.set(sx, sy, 0xFFF4F1D0.toInt())
                }
                val mx = x0 + (x1 - x0) * 3 / 4; val my = y0 + 8
                b.disc(mx, my, 4, 0xFFF4EFC8.toInt()); b.disc(mx + 2, my - 1, 3, s.top)
            }
            DayPeriod.DAY, DayPeriod.MORNING -> {
                cloud(b, x0 + (x1 - x0) / 5, y0 + 8)
                if (x1 - x0 > 80) cloud(b, x0 + (x1 - x0) * 2 / 3, y0 + 16)
            }
            DayPeriod.EVENING -> {
                val sx = x0 + (x1 - x0) / 3; val sy = y1 - (y1 - y0) / 4
                b.disc(sx, sy, 6, 0xFFFFC56B.toInt())
            }
        }
    }

    fun cloud(b: PixelBuffer, x: Int, y: Int) {
        val c = 0xFFF4F7FF.toInt()
        b.box(x, y + 2, x + 16, y + 5, c); b.box(x + 3, y, x + 9, y + 2, c); b.box(x + 9, y + 1, x + 13, y + 2, c)
    }

    /** Silhuetas de prédios com janelas que acendem à noite. */
    fun city(b: PixelBuffer, x0: Int, x1: Int, baseY: Int, period: DayPeriod, offset: Int = 0, seed: Int = 1) {
        val s = sky(period)
        var x = x0 - ((offset % 97) + 97) % 97
        var i = seed
        while (x <= x1) {
            val w = 10 + (i * 7) % 12
            val h = 12 + (i * 13) % 26
            val bx0 = maxOf(x, x0); val bx1 = minOf(x + w, x1)
            if (bx0 <= bx1) {
                b.box(bx0, baseY - h, bx1, baseY, s.city)
                s.cityWindow?.let { lit ->
                    for (wy in baseY - h + 3 until baseY - 1 step 4) for (wx in x + 2 until x + w - 1 step 3) {
                        if (wx in x0..x1 && (wx * 31 + wy * 17 + i) % 3 != 0) b.set(wx, wy, lit)
                    }
                }
            }
            x += w + 2; i++
        }
    }

    /** Janela com moldura, céu do período e cortinas. */
    fun window(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, period: DayPeriod, curtains: Int? = null) {
        b.box(x0 - 2, y0 - 2, x1 + 2, y1 + 2, P.OUTLINE)
        b.box(x0 - 1, y0 - 1, x1 + 1, y1 + 1, P.WHITE)
        skyBands(b, x0 + 2, y0 + 2, x1 - 2, y1 - 2, period)
        city(b, x0 + 2, x1 - 2, y1 - 2, period, seed = x0)
        val mx = (x0 + x1) / 2
        b.box(mx - 1, y0, mx + 1, y1, P.WHITE); b.vline(mx, y0, y1, 0xFFD9D3C4.toInt())
        b.box(x0, (y0 + y1) / 2 - 1, x1, (y0 + y1) / 2, P.WHITE)
        b.box(x0 - 4, y1 + 2, x1 + 4, y1 + 5, P.WHITE); b.hline(x0 - 4, x1 + 4, y1 + 6, P.OUTLINE)
        curtains?.let { c ->
            val dark = PixelBuffer.mix(c, P.OUTLINE, 0.3f)
            for ((cx0, cx1) in listOf((x0 - 8) to (x0 + 3), (x1 - 3) to (x1 + 8))) {
                b.outlined(cx0, y0 - 6, cx1, y1 + 4, c, P.OUTLINE)
                for (x in cx0 + 2 until cx1 step 3) b.vline(x, y0 - 4, y1 + 2, dark)
            }
            b.box(x0 - 10, y0 - 8, x1 + 10, y0 - 6, P.FURNITURE)
        }
    }

    fun wallStripes(b: PixelBuffer, y0: Int, y1: Int, base: Int, stripe: Int, spacing: Int = 16) {
        b.box(0, y0, SCENE_W_MAX, y1, base)
        for (x in 0 until SCENE_W_MAX step spacing) b.box(x, y0, x + 1, y1, stripe)
    }

    fun woodFloor(b: PixelBuffer, y0: Int, y1: Int, base: Int = P.WOOD, line: Int = P.WOOD_DARK) {
        b.box(0, y0, SCENE_W_MAX, y1, base)
        var row = 0
        for (y in y0 until y1 step 12) {
            b.hline(0, SCENE_W_MAX, y, line)
            val shift = if (row % 2 == 0) 0 else 24
            for (x in shift until SCENE_W_MAX step 48) b.vline(x, y, minOf(y + 11, y1), line)
            b.hline(0, SCENE_W_MAX, y + 1, PixelBuffer.mix(base, P.WHITE, 0.15f))
            row++
        }
        b.box(0, y0, SCENE_W_MAX, y0 + 2, PixelBuffer.mix(base, P.OUTLINE, 0.35f))
    }

    fun baseboard(b: PixelBuffer, y: Int, color: Int = P.FURNITURE) {
        b.box(0, y - 4, SCENE_W_MAX, y, color)
        b.hline(0, SCENE_W_MAX, y - 4, PixelBuffer.mix(color, P.WHITE, 0.3f))
        b.hline(0, SCENE_W_MAX, y + 1, P.OUTLINE)
    }

    /**
     * Porta com 4 estados: 0 fechada, 1–2 abrindo, 3 aberta (vemos o vão escuro e a
     * folha da porta estreitando, girando na dobradiça da esquerda).
     */
    fun door(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, color: Int = 0xFF8E5B3A.toInt(), frame: Int = 0) {
        b.outlined(x0 - 2, y0 - 2, x1 + 2, y1, P.WHITE, P.OUTLINE)
        val dark = PixelBuffer.mix(color, P.OUTLINE, 0.3f)
        if (frame <= 0) {
            b.outlined(x0, y0, x1, y1, color, P.OUTLINE)
            b.box(x0 + 4, y0 + 5, x1 - 4, (y0 + y1) / 2 - 3, dark)
            b.box(x0 + 4, (y0 + y1) / 2 + 3, x1 - 4, y1 - 6, dark)
            b.disc(x1 - 5, (y0 + y1) / 2, 1, P.YELLOW)
            return
        }
        // Vão da porta (corredor escuro com um pouco de luz no chão).
        b.box(x0, y0, x1, y1, 0xFF2A2440.toInt())
        b.box(x0 + 1, y1 - 6, x1 - 1, y1 - 1, 0xFF3D3560.toInt())
        // Folha da porta vista de lado, cada vez mais estreita.
        val leaf = when (frame) { 1 -> (x1 - x0) * 2 / 3; 2 -> (x1 - x0) / 3; else -> 4 }
        b.outlined(x0, y0, x0 + leaf, y1, color, P.OUTLINE)
        if (leaf > 8) b.box(x0 + 3, y0 + 5, x0 + leaf - 3, y1 - 6, dark)
        if (leaf > 6) b.disc(x0 + leaf - 3, (y0 + y1) / 2, 1, P.YELLOW)
    }

    /** Cadeira de escritório: ocupada mostra só o encosto; vazia mostra assento e base. */
    fun deskChair(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, color: Int, light: Int, occupied: Boolean) {
        b.outlined(x0, y0, x1, y1, color, P.OUTLINE)
        b.box(x0 + 3, y0 + 3, x1 - 3, y0 + 6, light)
        if (!occupied) {
            // Assento e braços à vista, levemente virada (vazia).
            b.outlined(x0 - 3, y1 - 6, x1 + 3, y1 + 2, color, P.OUTLINE)
            b.hline(x0 - 2, x1 + 2, y1 - 5, light)
            b.box(x0 - 3, y1 - 12, x0 - 1, y1 - 6, P.OUTLINE); b.box(x1 + 1, y1 - 12, x1 + 3, y1 - 6, P.OUTLINE)
        }
    }

    fun plant(b: PixelBuffer, x: Int, baseY: Int, big: Boolean = false) {
        val h = if (big) 30 else 18
        b.outlined(x - 6, baseY - 10, x + 6, baseY, P.POT, P.OUTLINE)
        b.hline(x - 5, x + 5, baseY - 8, PixelBuffer.mix(P.POT, P.OUTLINE, 0.25f))
        val leaves = listOf(-9 to -h, -4 to -h - 6, 2 to -h - 3, 7 to -h + 2, -1 to -h + 6, -8 to -h + 8, 5 to -h + 10)
        leaves.forEach { (dx, dy) ->
            b.disc(x + dx, baseY - 10 + dy / 2 + 2, if (big) 6 else 4, P.OUTLINE)
        }
        leaves.forEach { (dx, dy) ->
            b.disc(x + dx, baseY - 10 + dy / 2 + 2, if (big) 5 else 3, P.LEAF)
            b.set(x + dx - 1, baseY - 10 + dy / 2 + 1, 0xFF7CC98A.toInt())
        }
        b.vline(x, baseY - 14, baseY - 10, P.LEAF_DARK)
    }

    /** Relógio de parede que mostra a hora real. */
    fun clock(b: PixelBuffer, cx: Int, cy: Int, r: Int, minuteOfDay: Int) {
        b.disc(cx, cy, r + 1, P.OUTLINE)
        b.disc(cx, cy, r, P.WHITE)
        for (k in 0 until 12 step 3) {
            val a = Math.toRadians(k * 30.0 - 90)
            b.set(cx + ((r - 1) * cos(a)).toInt(), cy + ((r - 1) * sin(a)).toInt(), P.OUTLINE)
        }
        val hourA = Math.toRadians((minuteOfDay % 720) / 2.0 - 90)
        val minA = Math.toRadians((minuteOfDay % 60) * 6.0 - 90)
        b.line(cx, cy, cx + ((r - 4) * cos(hourA)).toInt(), cy + ((r - 4) * sin(hourA)).toInt(), P.OUTLINE)
        b.line(cx, cy, cx + ((r - 2) * cos(minA)).toInt(), cy + ((r - 2) * sin(minA)).toInt(), P.RED)
    }

    fun floorLamp(b: PixelBuffer, x: Int, baseY: Int, on: Boolean) {
        b.box(x - 1, baseY - 44, x + 1, baseY, P.METAL_DARK)
        b.outlined(x - 5, baseY - 2, x + 5, baseY, P.METAL_DARK, P.OUTLINE)
        b.outlined(x - 8, baseY - 56, x + 8, baseY - 44, if (on) P.LAMP_LIGHT else P.CREAM, P.OUTLINE)
    }

    fun mug(b: PixelBuffer, x: Int, y: Int) {
        b.outlined(x, y, x + 6, y + 6, P.WHITE, P.OUTLINE)
        b.hline(x + 1, x + 5, y + 1, 0xFF6B3E26.toInt())
        b.vline(x + 7, y + 2, y + 4, P.OUTLINE)
    }

    /** Monitor com tela animada (código, documento ou gráfico — nada real). */
    fun monitor(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, t: Long, on: Boolean, mode: Int = 0) {
        b.outlined(x0, y0, x1, y1, 0xFF3A3F55.toInt(), P.OUTLINE)
        val sx0 = x0 + 2; val sy0 = y0 + 2; val sx1 = x1 - 2; val sy1 = y1 - 3
        b.box(sx0, sy0, sx1, sy1, if (on) P.SCREEN else P.SCREEN_OFF)
        if (on) {
            val step = (t / 350).toInt()
            when (mode % 3) {
                0 -> { // código rolando
                    var line = 0
                    for (y in sy0 + 2 until sy1 - 1 step 3) {
                        val k = line + step
                        val indent = (k * 5 % 4) * 2
                        val len = 6 + (k * 7) % (sx1 - sx0 - 10).coerceAtLeast(1)
                        val color = when (k % 4) { 0 -> P.CODE_1; 1 -> P.CODE_2; 2 -> P.CODE_3; else -> 0xFFB8C4E8.toInt() }
                        b.hline(sx0 + 2 + indent, minOf(sx0 + 2 + indent + len, sx1 - 2), y, color)
                        line++
                    }
                    if ((t / 400) % 2 == 0L) b.box(sx0 + 3, sy1 - 2, sx0 + 4, sy1 - 1, P.WHITE)
                }
                1 -> { // documento
                    b.box(sx0 + 3, sy0 + 2, sx1 - 3, sy1 - 1, 0xFFE9EDF7.toInt())
                    for (y in sy0 + 4 until sy1 - 2 step 3) b.hline(sx0 + 5, sx1 - 5 - (y * 3 % 7), y, 0xFF8A93A8.toInt())
                }
                else -> { // gráfico
                    for (i in 0 until (sx1 - sx0 - 4) / 4) {
                        val h = 3 + ((i * 7 + step) % 9)
                        b.box(sx0 + 3 + i * 4, sy1 - 1 - h, sx0 + 5 + i * 4, sy1 - 1, if (i % 2 == 0) P.CODE_1 else P.CODE_2)
                    }
                }
            }
        }
        b.box((x0 + x1) / 2 - 2, y1 + 1, (x0 + x1) / 2 + 2, y1 + 4, P.METAL_DARK)
        b.box((x0 + x1) / 2 - 6, y1 + 4, (x0 + x1) / 2 + 6, y1 + 5, P.OUTLINE)
    }

    fun rug(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, color: Int, accent: Int) {
        b.outlined(x0, y0, x1, y1, color, P.OUTLINE)
        b.box(x0 + 3, y0 + 3, x1 - 3, y0 + 3, accent); b.box(x0 + 3, y1 - 3, x1 - 3, y1 - 3, accent)
        for (x in x0 + 6 until x1 - 4 step 8) b.dither(x, (y0 + y1) / 2 - 1, x + 3, (y0 + y1) / 2 + 1, accent)
    }

    fun poster(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int) {
        b.outlined(x0, y0, x1, y1, 0xFF2B3566.toInt(), P.OUTLINE)
        // Pequeno "Hoodie" no pôster.
        val cx = (x0 + x1) / 2
        b.box(cx - 5, y0 + 9, cx + 5, y0 + 17, 0xFF86A9E8.toInt())
        b.box(cx - 5, y0 + 6, cx - 3, y0 + 8, 0xFF86A9E8.toInt()); b.box(cx + 3, y0 + 6, cx + 5, y0 + 8, 0xFF86A9E8.toInt())
        b.set(cx - 2, y0 + 12, P.OUTLINE); b.set(cx + 2, y0 + 12, P.OUTLINE)
        b.box(cx - 6, y0 + 18, cx + 6, y1 - 6, 0xFFB9CBEF.toInt())
        b.hline(x0 + 3, x1 - 3, y1 - 3, P.YELLOW)
    }

    /** Largura máxima desenhável (box faz o clamp). */
    private const val SCENE_W_MAX = 239
}
