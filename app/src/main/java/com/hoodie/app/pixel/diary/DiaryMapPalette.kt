package com.hoodie.app.pixel.diary

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Paleta da cidade do diário: mesma família de cores do quarto e do Hoodie. */
object DiaryMapPalette {
    const val OUTLINE = 0xFF1A1C33.toInt()
    const val GRASS = 0xFF5E9E6B.toInt()
    const val GRASS_DARK = 0xFF4C8659.toInt()
    const val GRASS_LIGHT = 0xFF77B57F.toInt()
    const val ROAD = 0xFF4A4E66.toInt()
    const val ROAD_LINE = 0xFFE7D99A.toInt()
    const val ROAD_EDGE = 0xFF3A3D52.toInt()
    const val SIDEWALK = 0xFFB7B3A6.toInt()
    const val SIDEWALK_DARK = 0xFF9A968A.toInt()
    const val TREE = 0xFF2F6E46.toInt()
    const val TREE_LIGHT = 0xFF43875A.toInt()
    const val TRUNK = 0xFF7A4E36.toInt()
    const val WATER = 0xFF4F86C6.toInt()
    const val WATER_LIGHT = 0xFF8FB8E8.toInt()
    const val FLOWER_A = 0xFFEFA3C8.toInt()
    const val FLOWER_B = 0xFFF2CF5B.toInt()
    const val LAMP = 0xFF2B2E4A.toInt()
    const val LAMP_LIGHT = 0xFFFFE9A0.toInt()
    const val BENCH = 0xFF8A5A3C.toInt()
    const val WALL = 0xFFEDE4D3.toInt()
    const val WALL_SHADE = 0xFFCFC3AE.toInt()
    const val WINDOW = 0xFF9CC7EE.toInt()
    const val WINDOW_LIT = 0xFFFFDD7A.toInt()
    const val DOOR = 0xFF6B4630.toInt()
    const val GOLD = 0xFFF2CF5B.toInt()
    const val VISITED = 0xFFFFFFFF.toInt()
    const val SMOKE = 0xFFD9DCE6.toInt()
    const val PATH = 0xFFDAE5FA.toInt()
    const val PATH_ACTIVE = 0xFFF2CF5B.toInt()
    const val CAR_A = 0xFFE58AAE.toInt()
    const val CAR_B = 0xFF86C99A.toInt()

    /** Telhado por tipo de lugar (cor principal, sombra). */
    fun roof(type: PlaceType): Pair<Int, Int> = when (type) {
        PlaceType.HOME -> 0xFFD9694F.toInt() to 0xFFB0503B.toInt()
        PlaceType.WORK -> 0xFF88AFE9.toInt() to 0xFF6586CE.toInt()
        PlaceType.RESTAURANT -> 0xFFE99C7D.toInt() to 0xFFC97A5D.toInt()
        PlaceType.GYM -> 0xFFE6C86D.toInt() to 0xFFC4A64E.toInt()
        PlaceType.MARKET, PlaceType.STORE -> 0xFF86C99A.toInt() to 0xFF5FA676.toInt()
        PlaceType.SCHOOL -> 0xFFB79AE9.toInt() to 0xFF9478C9.toInt()
        PlaceType.LEISURE -> 0xFFEFA3C8.toInt() to 0xFFCB7FA6.toInt()
        PlaceType.FAMILY -> 0xFFF0B27A.toInt() to 0xFFCF9058.toInt()
        PlaceType.OTHER -> 0xFFA9B1C7.toInt() to 0xFF858DA6.toInt()
    }

    /** Tinta do horário no replay (cor, intensidade 0..255). */
    fun tint(period: DayPeriod): Pair<Int, Int>? = when (period) {
        DayPeriod.MORNING -> 0xFFFFC98A.toInt() to 28
        DayPeriod.DAY -> null
        DayPeriod.EVENING -> 0xFFE0705A.toInt() to 52
        DayPeriod.NIGHT -> 0xFF1C2350.toInt() to 120
    }

    fun applyTint(buf: PixelBuffer, period: DayPeriod) {
        val (color, alpha) = tint(period) ?: return
        for (i in buf.pixels.indices) buf.pixels[i] = PixelBuffer.blend(buf.pixels[i], color, alpha)
    }

    /** Versão escurecida (lugar ainda não visitado no replay). */
    fun dim(c: Int): Int = PixelBuffer.blend(c, 0xFF1A1C33.toInt(), 110)
}
