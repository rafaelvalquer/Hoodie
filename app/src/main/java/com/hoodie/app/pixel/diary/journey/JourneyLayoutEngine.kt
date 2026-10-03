package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.pixel.diary.DiaryMapPlaceNode
import com.hoodie.app.pixel.diary.DiaryMapTiles
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.diary.TilePos
import com.hoodie.app.pixel.diary.TileRect

enum class JourneySide { LEFT, RIGHT }

/** Retângulo em pixels lógicos (x, y, largura, altura). */
data class JourneyRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    val right get() = x + w
    val bottom get() = y + h
    val center get() = MapPoint(x + w / 2f, y + h / 2f)
    operator fun contains(p: MapPoint) = p.x >= x && p.x < right && p.y >= y && p.y < bottom
    fun intersects(o: JourneyRect) = x < o.right && o.x < right && y < o.bottom && o.y < bottom
    fun grow(n: Int) = JourneyRect(x - n, y - n, w + 2 * n, h + 2 * n)
}

/**
 * Onde cada parada fica no mapa:
 *
 *     ┌──────────────────────────────┐
 *     │ [🏠] ◉──  ┌ CASA ─────────┐   │  linha 0 (esquerda)
 *     │      │    └ 06:00–07:47   ┘   │
 *     │      └──── 🚌 31 min ─────┐   │  trecho 0 (desce, curva, atravessa)
 *     │   ┌ TRABALHO ──────┐  ◉ [🏢] │  linha 1 (direita)
 *     │   └ 08:15–12:08    ┘  │      │
 *     └──────────────────────────────┘
 */
data class JourneyNodeLayout(
    val nodeId: String,
    val index: Int,
    val side: JourneySide,
    val type: PlaceType,
    /** Topo da faixa desta parada. */
    val rowTop: Int,
    /** Prédio 3×3 tiles (alinhado à grade para reaproveitar os prédios do mapa clássico). */
    val building: TileRect,
    /** Plataforma onde o Hoodie para (pés no centro). */
    val pad: JourneyRect,
    /** Área do cartão de texto (sobreposto pela UI). */
    val card: JourneyRect,
) {
    /** Ponto onde os pés do Hoodie ficam parado nesta parada. */
    val stop: MapPoint get() = MapPoint(pad.x + pad.w / 2f, pad.y + pad.h - 3f)

    /** Prédio no formato do mapa clássico (para [com.hoodie.app.pixel.diary.DiaryMapBuildings]). */
    fun asPlaceNode(label: String) = DiaryMapPlaceNode(
        id = nodeId, placeId = null, label = label, type = type, footprint = building,
        door = TilePos(building.col + 1, building.lastRow + 1), visitIndices = listOf(index),
    )

    /** Toque: prédio, plataforma e cartão. */
    fun hit(p: MapPoint): Boolean = p in JourneyRect(building.pixels[0], building.pixels[1], building.pixels[2], building.pixels[3]) || p in pad.grow(4) || p in card
}

data class JourneySegmentLayout(
    val segmentId: String,
    val index: Int,
    val path: JourneyPath,
    /** Altura da rua horizontal do trecho. */
    val crossY: Int,
    /** Centro do selo "🚌 31 min" (sobre a rua horizontal). */
    val chipAnchor: MapPoint,
)

data class JourneyLayout(
    val width: Int,
    val height: Int,
    val nodes: List<JourneyNodeLayout>,
    val segments: List<JourneySegmentLayout>,
) {
    val isEmpty get() = nodes.isEmpty()
    fun node(id: String?) = nodes.firstOrNull { it.nodeId == id }
    fun nodeAt(p: MapPoint) = nodes.firstOrNull { it.hit(p) }

    /** Retângulos que a decoração não pode cobrir (prédios, plataformas, cartões, selos). */
    val reserved: List<JourneyRect> by lazy {
        nodes.flatMap { n ->
            val (bx, by, bw, bh) = n.building.pixels.toList()
            listOf(JourneyRect(bx, by, bw, bh).grow(2), n.pad.grow(4), n.card.grow(2))
        } + segments.map { s -> JourneyRect(s.chipAnchor.x.toInt() - 48, s.chipAnchor.y.toInt() - 14, 96, 28) }
    }

    companion object {
        val EMPTY = JourneyLayout(JourneyLayoutEngine.WIDTH, JourneyLayoutEngine.emptyHeight, emptyList(), emptyList())
    }
}

/**
 * Jornada em zigue-zague: parada par à esquerda, ímpar à direita; entre elas o
 * trecho desce, faz curva, atravessa e desce de novo até a próxima plataforma.
 * Determinístico: os mesmos dados dão sempre o mesmo layout. Só depende dos
 * nós (não do horário), então é recalculado apenas quando o dia muda.
 */
object JourneyLayoutEngine {
    const val WIDTH = DiaryMapTiles.WIDTH // 240: mesma largura do mapa clássico → mesma escala inteira
    const val TILE = DiaryMapTiles.SIZE
    /** Faixa de céu no topo (horizonte do dia). */
    const val HEADER = 3 * TILE
    /** Altura de cada parada (12 tiles). */
    const val ROW = 12 * TILE
    /** Rua decorativa no rodapé (carros passando). */
    const val FOOTER = 5 * TILE
    val emptyHeight = HEADER + ROW + FOOTER

    // Colunas (em pixels lógicos).
    const val LEFT_BUILDING_COL = 1          // x 8..32
    const val RIGHT_BUILDING_COL = 26        // x 208..232
    const val LEFT_STOP_X = 48
    const val RIGHT_STOP_X = 192
    const val CARD_X = 64
    const val CARD_W = 108                   // 64..172: nunca encosta nas ruas verticais (48 e 192)
    const val CARD_TOP = 8
    const val CARD_H = 40
    const val BUILDING_TOP = 2 * TILE        // prédio y 16..40 dentro da faixa
    const val PAD_TOP = 32
    const val PAD_W = 22
    const val PAD_H = 10
    /** Rua horizontal do trecho: abaixo do cartão, acima da próxima faixa. */
    const val CROSS = 72

    fun sideOf(index: Int) = if (index % 2 == 0) JourneySide.LEFT else JourneySide.RIGHT
    fun stopX(side: JourneySide) = if (side == JourneySide.LEFT) LEFT_STOP_X else RIGHT_STOP_X
    fun rowTop(index: Int) = HEADER + index * ROW
    fun heightFor(nodeCount: Int) = HEADER + maxOf(nodeCount, 1) * ROW + FOOTER

    fun layout(data: JourneyMapData): JourneyLayout {
        if (data.isEmpty) return JourneyLayout.EMPTY
        val nodes = data.nodes.map { n ->
            val side = sideOf(n.visitIndex)
            val top = rowTop(n.visitIndex)
            val stopX = stopX(side)
            JourneyNodeLayout(
                nodeId = n.id,
                index = n.visitIndex,
                side = side,
                type = n.placeType,
                rowTop = top,
                building = TileRect(if (side == JourneySide.LEFT) LEFT_BUILDING_COL else RIGHT_BUILDING_COL, (top + BUILDING_TOP) / TILE, 3, 3),
                pad = JourneyRect(stopX - PAD_W / 2, top + PAD_TOP, PAD_W, PAD_H),
                card = JourneyRect(CARD_X, top + CARD_TOP, CARD_W, CARD_H),
            )
        }
        val segments = data.segments.mapNotNull { s ->
            val from = nodes.getOrNull(s.index) ?: return@mapNotNull null
            val to = nodes.getOrNull(s.index + 1) ?: return@mapNotNull null
            val crossY = from.rowTop + CROSS
            JourneySegmentLayout(
                segmentId = s.id,
                index = s.index,
                path = JourneyPathBuilder.build(s.id, from.stop, to.stop, crossY),
                crossY = crossY,
                chipAnchor = MapPoint(WIDTH / 2f, crossY.toFloat()),
            )
        }
        return JourneyLayout(WIDTH, heightFor(nodes.size), nodes, segments)
    }
}
