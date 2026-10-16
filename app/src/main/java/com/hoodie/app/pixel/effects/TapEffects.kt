package com.hoodie.app.pixel.effects

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Opções de efeito para o toque na tela (escolha em teste no Pixel Lab → aba Toque). */
enum class TapEffectStyle(val label: String, val durationMs: Long) {
    SPARKLE("Estrelinha", 380),
    BURST("Explosãozinha", 440),
    RING("Onda", 400),
    HEARTS("Coraçõezinhos", 620),
    PAW("Patinha", 560),
}

/** Um "pixel" do efeito: deslocamento em pixels lógicos a partir do ponto tocado, cor ARGB e opacidade. */
data class TapCell(val dx: Int, val dy: Int, val color: Int, val alpha: Float = 1f)

/**
 * Efeitos de toque em pixel art, função pura do tempo (mesma idade = mesmo quadro). Cada efeito
 * cabe em ~20×20 pixels lógicos, dura menos de 0,7 s e termina vazio (nada fica na tela).
 */
object TapEffects {
    private const val WHITE = 0xFFFFFDF2.toInt()
    private const val YELLOW = 0xFFFFE07A.toInt()
    private const val GOLD = 0xFFF2CF5B.toInt()
    private const val ORANGE = 0xFFF09A3E.toInt()
    private const val SKY = 0xFF9CC6EF.toInt()
    private const val PINK = 0xFFF27AA6.toInt()
    private const val PINK_DARK = 0xFFC9547F.toInt()
    private const val PAD = 0xFFF6B8CC.toInt()

    fun cells(style: TapEffectStyle, ageMs: Long, seed: Int = 0): List<TapCell> {
        if (ageMs < 0 || ageMs >= style.durationMs) return emptyList()
        val t = ageMs.toFloat() / style.durationMs
        return when (style) {
            TapEffectStyle.SPARKLE -> sparkle(t)
            TapEffectStyle.BURST -> burst(t, seed)
            TapEffectStyle.RING -> ring(t)
            TapEffectStyle.HEARTS -> hearts(t, seed)
            TapEffectStyle.PAW -> paw(t)
        }
    }

    private fun easeOut(t: Float) = 1 - (1 - t) * (1 - t)

    /** Estrela de quatro pontas que cresce e encolhe, com brilhos nas diagonais. */
    private fun sparkle(t: Float): List<TapCell> = buildList {
        val grow = if (t < 0.4f) t / 0.4f else 1 - (t - 0.4f) / 0.6f
        val arm = (1 + grow * 5).roundToInt()
        add(TapCell(0, 0, WHITE))
        for (i in 1..arm) {
            val c = if (i <= arm / 2) WHITE else YELLOW
            add(TapCell(i, 0, c)); add(TapCell(-i, 0, c)); add(TapCell(0, i, c)); add(TapCell(0, -i, c))
        }
        if (arm >= 3) listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1).forEach { (x, y) -> add(TapCell(x, y, YELLOW)) }
        if (t in 0.25f..0.85f) {
            val d = 3 + (easeOut((t - 0.25f) / 0.6f) * 3).roundToInt()
            listOf(d to d, d to -d, -d to d, -d to -d).forEach { (x, y) -> add(TapCell(x, y, GOLD)) }
        }
    }

    /** Oito faíscas saindo do ponto, com um clarão curto no centro; esfriam de branco a laranja. */
    private fun burst(t: Float, seed: Int): List<TapCell> = buildList {
        if (t < 0.18f) {
            val r = if (t < 0.09f) 1 else 2
            for (x in -r..r) for (y in -r..r) if (x * x + y * y <= r * r) add(TapCell(x, y, WHITE))
        }
        val dist = 2 + easeOut(t) * 8
        val color = when { t < 0.3f -> WHITE; t < 0.6f -> YELLOW; else -> ORANGE }
        val alpha = if (t < 0.7f) 1f else 1 - (t - 0.7f) / 0.3f
        for (k in 0 until 8) {
            val a = 2 * PI * k / 8 + (seed % 4) * PI / 16
            val x = (cos(a) * dist).roundToInt(); val y = (sin(a) * dist).roundToInt()
            add(TapCell(x, y, color, alpha))
            // Rastro de um pixel atrás de cada faísca no começo.
            if (t < 0.5f) add(TapCell((cos(a) * (dist - 1.5)).roundToInt(), (sin(a) * (dist - 1.5)).roundToInt(), color, alpha * 0.6f))
        }
    }

    /** Anel que se expande a partir do toque e some aos poucos. */
    private fun ring(t: Float): List<TapCell> = buildList {
        if (t < 0.15f) add(TapCell(0, 0, WHITE))
        val r = 2 + easeOut(t) * 8
        val color = if (t < 0.5f) WHITE else SKY
        val alpha = if (t < 0.5f) 1f else 1 - (t - 0.5f) / 0.5f
        val seen = HashSet<Pair<Int, Int>>()
        val steps = 48
        for (k in 0 until steps) {
            val a = 2 * PI * k / steps
            val p = (cos(a) * r).roundToInt() to (sin(a) * r).roundToInt()
            if (seen.add(p)) add(TapCell(p.first, p.second, color, alpha))
        }
    }

    private val HEART = listOf(
        ".##.##.",
        "#######",
        "#######",
        ".#####.",
        "..###..",
        "...#...",
    )

    /** Dois coraçõezinhos que pulam do toque e sobem balançando. */
    private fun hearts(t: Float, seed: Int): List<TapCell> = buildList {
        val alpha = if (t < 0.65f) 1f else 1 - (t - 0.65f) / 0.35f
        val pop = if (t < 0.15f) t / 0.15f else 1f
        val side = if (seed % 2 == 0) 1 else -1
        listOf(Triple(-4 * side, 0f, 1.0f), Triple(4 * side, 0.12f, 0.8f)).forEach { (baseX, delay, size) ->
            val local = ((t - delay) / (1 - delay)).coerceIn(0f, 1f)
            if (t < delay) return@forEach
            val rise = (easeOut(local) * 12).roundToInt()
            val sway = (sin(local * PI * 2) * 1.5).roundToInt()
            val big = pop * size >= 0.9f
            HEART.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch ->
                    if (ch != '#') return@forEachIndexed
                    if (!big && (y == 0 || x == 0 || x == row.lastIndex)) return@forEachIndexed
                    val c = if (y == 1 && x in 1..2) WHITE else if (y >= 3) PINK_DARK else PINK
                    add(TapCell(baseX + sway + x - 3, y - 3 - rise, c, alpha))
                }
            }
        }
    }

    private val PAW_SHAPE = listOf(
        ".#..#.",
        "#....#",
        "..##..",
        ".####.",
        ".####.",
    )

    /** Patinha de gato carimbada: estala maior por um instante, assenta e some em pontilhado. */
    private fun paw(t: Float): List<TapCell> = buildList {
        // Cada pixel do desenho vira um bloco: 3×3 no impacto, 2×2 depois (legível em qualquer tela).
        val scale = if (t < 0.10f) 3 else 2
        val fade = if (t < 0.55f) 0f else (t - 0.55f) / 0.45f
        PAW_SHAPE.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch != '#') return@forEachIndexed
                // Some em pontilhado (sem transparência contínua), em ordem pseudoaleatória fixa.
                val order = ((x * 7 + y * 13) % 5) / 5f
                if (fade > 0 && order < fade) return@forEachIndexed
                val c = if (y >= 2) PAD else PINK
                for (sx in 0 until scale) for (sy in 0 until scale) {
                    add(TapCell((x - 3) * scale + sx, (y - 2) * scale + sy, c))
                }
            }
        }
    }
}
