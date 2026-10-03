package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.pixel.diary.DiaryMapPalette
import com.hoodie.app.pixel.transport.TransportRouteStyle
import com.hoodie.app.pixel.transport.TransportVisualRegistry

/**
 * Cores da Jornada. Herda o chão, a água, as árvores e os prédios do mapa
 * clássico (mesma família do quarto e do Hoodie) e acrescenta o céu e um
 * estilo de rua por meio de transporte.
 */
object JourneyPalette {
    const val OUTLINE = DiaryMapPalette.OUTLINE
    const val GRASS = DiaryMapPalette.GRASS
    const val GRASS_DARK = DiaryMapPalette.GRASS_DARK
    const val GRASS_LIGHT = DiaryMapPalette.GRASS_LIGHT
    const val ROAD = DiaryMapPalette.ROAD
    const val ROAD_EDGE = DiaryMapPalette.ROAD_EDGE
    const val SIDEWALK = DiaryMapPalette.SIDEWALK
    const val SIDEWALK_DARK = DiaryMapPalette.SIDEWALK_DARK
    const val TREE = DiaryMapPalette.TREE
    const val TREE_LIGHT = DiaryMapPalette.TREE_LIGHT
    const val TREE_DARK = 0xFF23573A.toInt()
    const val TRUNK = DiaryMapPalette.TRUNK
    const val WATER = DiaryMapPalette.WATER
    const val WATER_LIGHT = DiaryMapPalette.WATER_LIGHT
    const val FLOWER_A = DiaryMapPalette.FLOWER_A
    const val FLOWER_B = DiaryMapPalette.FLOWER_B
    const val LAMP = DiaryMapPalette.LAMP
    const val LAMP_LIGHT = DiaryMapPalette.LAMP_LIGHT
    const val BENCH = DiaryMapPalette.BENCH
    const val GOLD = DiaryMapPalette.GOLD
    const val SMOKE = DiaryMapPalette.SMOKE
    const val CLOUD = 0xFFF4F1EA.toInt()
    const val CLOUD_SHADE = 0xFFD6D9E6.toInt()
    const val BIRD = 0xFF2B2E4A.toInt()
    const val LEAF = 0xFFE9A04F.toInt()
    const val SPARK = 0xFFFFF4B8.toInt()
    const val SELECTED = 0xFF8FD3FF.toInt()
    const val PAD = 0xFFCFC9B8.toInt()
    const val PAD_SHADE = 0xFFA9A392.toInt()
    const val CAR_A = DiaryMapPalette.CAR_A
    const val CAR_B = DiaryMapPalette.CAR_B
    const val CAR_C = 0xFF86A9E8.toInt()
    const val GLASS = 0xFF9CC7EE.toInt()
    const val TIRE = 0xFF2B2E4A.toInt()

    /** Como a linha do trecho é desenhada (sobre a rua). */
    enum class Stroke { DOTTED, DOUBLE, SOLID, SEGMENTED, THIN }

    /** Estilo de rua por meio: cor principal, cor secundária e traço (plano §10.1). */
    data class RouteStyle(val color: Int, val accent: Int, val stroke: Stroke)

    fun route(mode: MovementMode?): RouteStyle = when (TransportVisualRegistry.profileFor(mode, CommuteStyle.WALK).journey.routeStyle) {
        TransportRouteStyle.WALK -> RouteStyle(0xFF7FE0A0.toInt(), 0xFF4FB676.toInt(), Stroke.DOTTED)
        TransportRouteStyle.CAR -> RouteStyle(0xFFC9CCD8.toInt(), 0xFF8A8FA6.toInt(), Stroke.DOUBLE)
        TransportRouteStyle.BUS -> RouteStyle(0xFF6FA3F2.toInt(), 0xFF3F72C4.toInt(), Stroke.SOLID)
        TransportRouteStyle.TRAIN -> RouteStyle(0xFFB58CF0.toInt(), 0xFF6FE0E8.toInt(), Stroke.SEGMENTED)
        TransportRouteStyle.METRO -> RouteStyle(0xFFBA78E4.toInt(), 0xFF4CE0DE.toInt(), Stroke.SEGMENTED)
        TransportRouteStyle.BICYCLE -> RouteStyle(0xFFF2CF5B.toInt(), 0xFFC9A63A.toInt(), Stroke.THIN)
        TransportRouteStyle.OTHER -> RouteStyle(0xFFF09A55.toInt(), 0xFFFFD084.toInt(), Stroke.DOTTED)
        TransportRouteStyle.GENERIC_TRANSIT -> RouteStyle(0xFF9AA6B8.toInt(), 0xFFD5DCE6.toInt(), Stroke.SOLID)
    }

    /** Céu do horizonte por período (topo, base). */
    fun sky(period: com.hoodie.app.core.time.DayPeriod): Pair<Int, Int> = when (period) {
        com.hoodie.app.core.time.DayPeriod.MORNING -> 0xFFF6C99B.toInt() to 0xFFF9E2B8.toInt()
        com.hoodie.app.core.time.DayPeriod.DAY -> 0xFF8FC6F2.toInt() to 0xFFC8E4F7.toInt()
        com.hoodie.app.core.time.DayPeriod.EVENING -> 0xFFE58A6A.toInt() to 0xFFF2C07E.toInt()
        com.hoodie.app.core.time.DayPeriod.NIGHT -> 0xFF1B1D3F.toInt() to 0xFF2F3366.toInt()
    }

    fun dim(c: Int): Int = DiaryMapPalette.dim(c)
}
