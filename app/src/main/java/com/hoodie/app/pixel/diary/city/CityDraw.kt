package com.hoodie.app.pixel.diary.city

import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Primitivas da Jornada em cidade (mesma linguagem do mockup aprovado): retângulos com coordenadas
 * de canto + largura/altura, caixas com contorno, fonte 3×5 das placas e ruído determinístico.
 */
internal object CityDraw {
    const val INK = 0xFF1A1C33.toInt()
    const val SOFT = 0xFF3A3F6A.toInt()

    fun c(hex: String): Int = (0xFF000000L or hex.removePrefix("#").toLong(16)).toInt()
    fun ca(hex: String, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (hex.removePrefix("#").toLong(16).toInt() and 0xFFFFFF)

    fun hash(x: Int, y: Int, s: Int): Float {
        var n = x * 374761393 + y * 668265263 + s * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        return ((n xor (n ushr 16)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967295f
    }

    private val GLYPH = mapOf(
        'A' to "010101111101101", 'B' to "110101110101110", 'C' to "011100100100011", 'D' to "110101101101110",
        'E' to "111100110100111", 'F' to "111100110100100", 'G' to "011100101101011", 'H' to "101101111101101",
        'I' to "111010010010111", 'J' to "001001001101010", 'K' to "101101110101101", 'L' to "100100100100111",
        'M' to "101111111101101", 'N' to "110101101101101", 'O' to "010101101101010", 'P' to "110101110100100",
        'Q' to "010101101110011", 'R' to "110101110101101", 'S' to "011100010001110", 'T' to "111010010010010",
        'U' to "101101101101111", 'V' to "101101101101010", 'W' to "101101111111101", 'X' to "101101010101101",
        'Y' to "101101010010010", 'Z' to "111001010100111", '0' to "111101101101111", '1' to "010110010010111",
        '2' to "110001010100111", '3' to "110001010001110", '4' to "101101111001001", '5' to "111100110001110",
        '6' to "011100110101010", '7' to "111001010010010", '8' to "010101010101010", '9' to "010101011001110",
        ' ' to "000000000000000", '.' to "000000000000010", ':' to "000010000010000", '-' to "000000111000000",
        '/' to "001001010100100", '&' to "010101010101011",
    )

    /** Texto de placa: maiúsculo, sem acento, só o que a fonte 3×5 desenha. */
    fun signText(name: String, maxChars: Int): String {
        val plain = java.text.Normalizer.normalize(name.uppercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        val kept = plain.filter { it in GLYPH }.trim().replace(Regex(" +"), " ")
        return if (kept.length <= maxChars) kept else kept.take(maxChars).trimEnd()
    }

    fun textW(s: String) = s.length * 4 - 1

    fun text3(b: PixelBuffer, s: String, x: Int, y: Int, color: Int) {
        s.forEachIndexed { i, ch ->
            val bits = GLYPH[ch] ?: GLYPH.getValue(' ')
            for (k in 0 until 15) if (bits[k] == '1') b.set(x + i * 4 + k % 3, y + k / 3, color)
        }
    }

    /** Caixa com contorno: [line] por fora, [fill] por dentro (x, y, largura, altura). */
    fun box(b: PixelBuffer, x: Int, y: Int, w: Int, h: Int, fill: Int, line: Int = INK) {
        b.rect(x, y, w, h, line); b.rect(x + 1, y + 1, w - 2, h - 2, fill)
    }

    /** Disco cheio com bordas de pixel (mesmo traço do mockup). */
    fun disc(b: PixelBuffer, cx: Int, cy: Int, r: Int, color: Int) {
        for (y in -r..r) { val w = Math.round(Math.sqrt((r * r - y * y).toDouble())).toInt(); b.rect(cx - w, cy + y, w * 2 + 1, 1, color) }
    }

    fun sprite(w: Int, h: Int, draw: PixelBuffer.() -> Unit): PixelBuffer = PixelBuffer(w, h).apply(draw)

    fun flipped(src: PixelBuffer): PixelBuffer = PixelBuffer(src.width, src.height).also { it.blit(src, 0, 0, flipX = true) }

    /** Ícones 7×7 dos balões. */
    val ICON = mapOf(
        "heart" to listOf("0110110", "1111111", "1111111", "0111110", "0011100", "0001000", "0000000"),
        "dumbbell" to listOf("0000000", "1100011", "1111111", "1111111", "1100011", "0000000", "0000000"),
        "laptop" to listOf("0111110", "0100010", "0100010", "0111110", "1111111", "0000000", "0000000"),
        "fork" to listOf("1010110", "1010110", "1110110", "0100110", "0100010", "0100010", "0100010"),
        "cross" to listOf("0011100", "0011100", "1111111", "1111111", "1111111", "0011100", "0011100"),
        "sun" to listOf("1001001", "0111110", "1111111", "1111111", "1111111", "0111110", "1001001"),
        "dots" to listOf("0000000", "0000000", "0000000", "1101101", "1101101", "0000000", "0000000"),
        "note" to listOf("0001110", "0001010", "0001010", "0001010", "0111010", "1110110", "1100000"),
        "bag" to listOf("0011100", "0100010", "1111111", "1111111", "1111111", "1111111", "0111110"),
        "book" to listOf("1110111", "1010101", "1010101", "1010101", "1110111", "0011100", "0000000"),
        "star" to listOf("0001000", "0011100", "1111111", "0111110", "0110110", "1100011", "0000000"),
    )
    val ICON_COLOR = mapOf(
        "heart" to c("#e85a7a"), "dumbbell" to c("#3e4258"), "laptop" to c("#3368a8"), "fork" to c("#c86b4a"),
        "cross" to c("#2f9a5a"), "sun" to c("#f0a020"), "dots" to c("#3e4258"), "note" to c("#8a5ac8"),
        "bag" to c("#d86a8a"), "book" to c("#3368a8"), "star" to c("#f0a020"),
    )
    private val PAPER = c("#fffdf4")

    /** Balão 13×11 com o ícone, a ponta em (x, y). */
    fun bubble(b: PixelBuffer, x: Int, y: Int, icon: String) {
        box(b, x - 6, y - 12, 13, 11, PAPER); b.rect(x - 5, y - 11, 11, 1, 0xFFFFFFFF.toInt())
        b.rect(x - 1, y - 2, 3, 1, PAPER); b.rect(x - 1, y - 1, 1, 1, INK); b.rect(x + 1, y - 1, 1, 1, INK); b.rect(x, y, 1, 1, INK)
        val rows = ICON[icon] ?: return
        val color = ICON_COLOR.getValue(icon)
        rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> if (ch == '1') b.set(x - 3 + i, y - 10 + j, color) } }
    }
}
