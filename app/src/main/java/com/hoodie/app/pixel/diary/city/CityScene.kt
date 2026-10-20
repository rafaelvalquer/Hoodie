package com.hoodie.app.pixel.diary.city

import com.hoodie.app.engine.diary.city.CityPlan
import com.hoodie.app.engine.diary.city.CityTown
import com.hoodie.app.engine.diary.city.CityTown.TS
import com.hoodie.app.engine.diary.city.CityTown.Tile
import com.hoodie.app.pixel.diary.city.CityDraw.c
import com.hoodie.app.pixel.diary.city.CityDraw.hash
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Objeto parado do cenário já posicionado no mundo ([base] = linha do chão, para a ordem de profundidade). */
class PlacedProp(val image: PixelBuffer, val x: Int, val y: Int, val base: Int, val tree: Boolean)

/**
 * Parte estática da cidade de um dia, preparada fora da thread principal: chão pré-desenhado,
 * prédios com os nomes dos lugares do usuário e objetos. O quadro a quadro só recorta e sobrepõe.
 */
class CityScene private constructor(
    val plan: CityPlan,
    val ground: PixelBuffer,
    val buildings: List<PaintedBuilding>,
    val props: List<PlacedProp>,
) {
    val buildingBySlot: Map<String, PaintedBuilding> = buildings.associateBy { it.slot.id }

    /** Visão geral: chão + prédios + objetos numa imagem só (desenhada reduzida). */
    val overview: PixelBuffer by lazy {
        PixelBuffer(CityTown.WW, CityTown.WH).also { o ->
            o.copyFrom(ground)
            (buildings.map { it.slot.py + it.slot.ph to { o.blit(it.image, it.slot.px, it.slot.py) } } +
                props.map { it.base to { o.blit(it.image, it.x, it.y) } }).sortedBy { it.first }.forEach { it.second() }
        }
    }

    companion object {
        fun build(plan: CityPlan): CityScene {
            val namesBySlot = HashMap<String, String>()
            plan.stays.forEach { s -> namesBySlot.putIfAbsent(s.slotId, s.placeName) }
            val buildings = CityTown.slots.map { CityArt.paint(it, namesBySlot[it.id]) }
            val props = CityTown.props.map { spec ->
                val img = CityArt.prop(spec.kind, spec.variant)
                val base = (spec.ty + 1) * TS + spec.oy
                if (spec.kind == "curbCar") PlacedProp(img, spec.tx * TS, 36 * TS - 3, 36 * TS - 3 + img.height, false)
                else PlacedProp(img, spec.tx * TS + spec.ox + TS / 2 - img.width / 2, base - img.height, base, CityArt.isTree(spec.kind))
            }
            return CityScene(plan, paintGround(buildings, props), buildings, props)
        }

        private val GRASS = c("#7cc06a"); private val GRASS_D = c("#6aad5c"); private val GRASS_L = c("#93d07c")
        private val SIDE = c("#d8cfbd"); private val SEAM = c("#c4bba8"); private val SEAM2 = c("#cec5b2"); private val CURB = c("#a89e8a")
        private val ROAD = c("#5b5e6e"); private val LINE = c("#e9e1b8"); private val WHITE_LINE = c("#f2eedf")

        private fun paintGround(buildings: List<PaintedBuilding>, props: List<PlacedProp>): PixelBuffer {
            val g = PixelBuffer(CityTown.WW, CityTown.WH)
            for (ty in 0 until CityTown.MH) for (tx in 0 until CityTown.MW) tile(g, tx, ty)
            // retenção antes das travessias da avenida e setas de sentido
            listOf(17 to 13, 22 to 1, 44 to 13, 49 to 1).forEach { (tx, off) -> g.rect(tx * TS + off, 22 * TS, 2, 3 * TS, WHITE_LINE) }
            listOf(12 to 24, 40 to 24, 28 to 22, 62 to 22).forEach { (tx, ty) ->
                val x = tx * TS; val y = ty * TS + 6; val dir = if (ty == 24) 1 else -1
                g.rect(x + 2, y + 1, 8, 2, c("#e9e4dc"))
                for (k in 0 until 3) g.rect(if (dir > 0) x + 10 + k else x + 1 - k, y - 2 + k, 1, 6 - 2 * k, c("#e9e4dc"))
            }
            buildings.forEach { b -> val s = b.slot; g.rect(s.px + s.pw, s.py + 10, 5, s.ph - 6, 0x381E2440); g.rect(s.px + 6, s.py + s.ph, s.pw - 1, 3, 0x381E2440) }
            props.filter { it.tree }.forEach { p -> g.oval(p.x + p.image.width / 2 - 8, p.base - 4, p.x + p.image.width / 2 + 10, p.base, 0x331E2440) }
            return g
        }

        private fun tile(g: PixelBuffer, tx: Int, ty: Int) {
            val t = CityTown.tile(tx, ty) ?: Tile.GRASS
            val x = tx * TS; val y = ty * TS
            fun r(s: Int) = hash(tx, ty, s)
            val roadish = { a: Int, b: Int -> CityTown.tile(a, b) in CityTown.roadKinds }
            when (t) {
                Tile.GRASS -> {
                    g.rect(x, y, TS, TS, GRASS)
                    for (k in 0 until 8) g.set(x + (r(k) * 15).toInt(), y + (r(k + 9) * 15).toInt(), if (r(k + 20) > .5f) GRASS_D else GRASS_L)
                    if (r(40) > .8f) { val fx = x + 3 + (r(41) * 9).toInt(); val fy = y + 3 + (r(42) * 9).toInt(); g.set(fx, fy - 1, GRASS_D); g.rect(fx - 1, fy, 3, 1, c(listOf("#f6efd2", "#f2c45a", "#efa3c8", "#c9b6ef")[(r(43) * 4).toInt().coerceAtMost(3)])); g.set(fx, fy, c("#f2c45a")) }
                    if (r(50) > .88f) { g.rect(x + 5, y + 9, 1, 3, c("#5a9a4e")); g.rect(x + 7, y + 8, 1, 4, c("#5a9a4e")); g.rect(x + 9, y + 10, 1, 2, c("#5a9a4e")) }
                }
                Tile.SIDE, Tile.DOOR -> {
                    g.rect(x, y, TS, TS, SIDE); g.rect(x, y, TS, 1, SEAM); g.rect(x, y, 1, TS, SEAM); g.rect(x + 8, y, 1, TS, SEAM2); g.rect(x, y + 8, 8, 1, SEAM2)
                    if (r(3) > .82f) g.rect(x + 3 + (r(4) * 8).toInt(), y + 4 + (r(5) * 8).toInt(), 3, 1, c("#bdb3a0"))
                    if (roadish(tx, ty + 1)) { g.rect(x, y + 13, TS, 3, CURB); g.rect(x, y + 13, TS, 1, c("#e6dfd0")) }
                    if (roadish(tx, ty - 1)) { g.rect(x, y, TS, 3, CURB); g.rect(x, y + 2, TS, 1, c("#bdb3a0")) }
                    if (roadish(tx + 1, ty)) g.rect(x + 13, y, 3, TS, CURB)
                    if (roadish(tx - 1, ty)) g.rect(x, y, 3, TS, CURB)
                }
                Tile.ROAD, Tile.CROSS_H, Tile.CROSS_V, Tile.PARKING -> {
                    g.rect(x, y, TS, TS, ROAD)
                    for (k in 0 until 6) g.rect(x + (r(k) * 15).toInt(), y + (r(k + 7) * 15).toInt(), 2, 1, if (r(k + 30) > .5f) c("#535666") else c("#62657a"))
                    if (t == Tile.ROAD && r(60) > .97f) { CityDraw.disc(g, x + 8, y + 8, 4, c("#474a58")); g.rect(x + 5, y + 8, 7, 1, c("#3e4150")); g.rect(x + 8, y + 5, 1, 7, c("#3e4150")) }
                    if (t == Tile.ROAD && r(61) > .95f) { g.rect(x + 3, y + 10, 6, 2, c("#4f5262")); g.rect(x + 4, y + 10, 3, 1, c("#6a7a9a")) }
                    if (ty == 23 && tx % 2 == 0 && t == Tile.ROAD) g.rect(x + 2, y + 7, 12, 2, LINE)
                    if (ty == 37 && tx % 2 == 0 && t == Tile.ROAD) g.rect(x + 2, y + 15, 12, 1, c("#cfc8a8"))
                    if ((tx == 19 || tx == 46) && ty % 2 == 0 && t == Tile.ROAD && (ty < 21 || ty > 25)) g.rect(x + 15, y + 2, 1, 12, c("#cfc8a8"))
                    if (t == Tile.CROSS_V) for (k in 0 until 4) g.rect(x + 2, y + 1 + k * 4, 12, 2, WHITE_LINE)
                    if (t == Tile.CROSS_H) for (k in 0 until 4) g.rect(x + 1 + k * 4, y + 2, 2, 12, WHITE_LINE)
                    if (t == Tile.PARKING) { g.rect(x, y + 15, TS, 1, c("#e9e4dc")); g.rect(x, y, 1, TS, c("#e9e4dc")) }
                }
                Tile.PLAZA -> {
                    g.rect(x, y, TS, TS, c("#d6ccb8")); g.rect(x, y, 8, 8, c("#cfc4ae")); g.rect(x + 8, y + 8, 8, 8, c("#cfc4ae"))
                    g.rect(x, y, TS, 1, c("#c2b79f")); g.rect(x, y, 1, TS, c("#c2b79f"))
                    if (r(9) > .9f) g.rect(x + 3, y + 11, 2, 1, c("#b8ad94"))
                }
                Tile.PATH -> {
                    g.rect(x, y, TS, TS, c("#d9c08a")); for (k in 0 until 6) g.set(x + (r(k) * 15).toInt(), y + (r(k + 3) * 15).toInt(), c("#c4a874"))
                    if (CityTown.tile(tx, ty - 1) == Tile.GRASS) g.rect(x, y, TS, 1, c("#a8c07a"))
                    if (CityTown.tile(tx, ty + 1) == Tile.GRASS) g.rect(x, y + 15, TS, 1, c("#a8c07a"))
                }
                Tile.LOT -> {
                    g.rect(x, y, TS, TS, c("#6d7080")); for (k in 0 until 4) g.set(x + (r(k) * 15).toInt(), y + (r(k + 3) * 15).toInt(), c("#62657a"))
                    if (tx % 2 == 0 && ty in setOf(5, 6, 9, 10)) g.rect(x, y + 1, 1, 14, c("#e9e4dc"))
                    if (ty == 8) for (k in 0 until 2) g.rect(x + 2 + k * 8, y + 7, 4, 1, LINE)
                    if (tx == 23) g.rect(x, y, 3, TS, CURB); if (tx == 43) g.rect(x + 13, y, 3, TS, CURB)
                    if (ty == 3) g.rect(x, y, TS, 3, CURB); if (ty == 12) g.rect(x, y + 13, TS, 3, CURB)
                }
                Tile.WATER -> {
                    g.rect(x, y, TS, TS, c("#5aa6dc"))
                    val stone = c("#c9c5ba")
                    if (CityTown.tile(tx, ty - 1) != Tile.WATER) { g.rect(x, y, TS, 3, stone); g.rect(x, y + 3, TS, 1, c("#3f86b8")) }
                    if (CityTown.tile(tx - 1, ty) != Tile.WATER) { g.rect(x, y, 2, TS, stone); g.rect(x + 2, y, 1, TS, c("#3f86b8")) }
                    if (CityTown.tile(tx + 1, ty) != Tile.WATER) g.rect(x + 14, y, 2, TS, stone)
                    if (CityTown.tile(tx, ty + 1) != Tile.WATER) g.rect(x, y + 14, TS, 2, stone)
                    if (r(7) > .7f) { g.rect(x + 4, y + 7, 3, 3, c("#4f9a58")); g.set(x + 5, y + 6, c("#efa3c8")) }
                }
            }
        }
    }
}
