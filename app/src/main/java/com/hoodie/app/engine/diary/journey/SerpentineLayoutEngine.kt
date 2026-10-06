package com.hoodie.app.engine.diary.journey

import com.hoodie.app.domain.diary.journey.JourneyLeg
import com.hoodie.app.domain.diary.journey.JourneyStop
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.diary.journey.JourneyPath
import com.hoodie.app.pixel.diary.journey.JourneyPathBuilder
import com.hoodie.app.pixel.diary.journey.JourneyRect

/** Medidas da serpentina em pixels lógicos (mesma largura do mapa clássico → mesma escala inteira). */
data class SerpentineSpec(
    val width: Int = 240,
    val columns: Int = 3,
    val columnX: List<Int> = listOf(44, 120, 196),
    val rowHeight: Int = 66,
    val topPadding: Int = 46,
    val bottomPadding: Int = 48,
    /** Curva da virada pela borda. */
    val turnBulge: Int = 34,
    val buildingSize: Int = 32,
    val signW: Int = 40,
    val signH: Int = 18,
) {
    init { require(columnX.size == columns) }
    fun height(rows: Int) = topPadding + (maxOf(rows, 1) - 1) * rowHeight + bottomPadding
}

/** Lado da plaquinha: sempre para dentro da serpentina; na coluna do meio, embaixo do prédio. */
enum class SignSide { LEFT, RIGHT, BELOW }

data class OverworldStopLayout(
    val stopId: String,
    val index: Int,
    val row: Int,
    val col: Int,
    /** Ponto da trilha na porta (onde os pés do Hoodie ficam). */
    val point: MapPoint,
    val building: JourneyRect,
    val sign: JourneyRect,
    val signSide: SignSide,
) {
    /** Área de toque lógica: construção + placa (a UI garante ≥ 48 dp). */
    val hit: JourneyRect get() {
        val x0 = minOf(building.x, sign.x); val y0 = minOf(building.y, sign.y)
        val x1 = maxOf(building.right, sign.right); val y1 = maxOf(building.bottom, sign.bottom)
        return JourneyRect(x0, y0, x1 - x0, y1 - y0)
    }
}

enum class LinkKind { STRAIGHT, TURN, PORTAL_IN, PORTAL_OUT }

/** Trecho desenhado entre duas paradas (ou por um portal). */
data class OverworldLink(
    val id: String,
    val kind: LinkKind,
    val fromStopId: String?,
    val toStopId: String?,
    /** Deslocamento real por trás (meio de transporte + horários); null entre fantasma e parada sem trecho. */
    val legId: String?,
    val path: JourneyPath,
)

data class OverworldLayout(
    val width: Int,
    val height: Int,
    val rows: Int,
    val stops: List<OverworldStopLayout>,
    val links: List<OverworldLink>,
    val portalIn: JourneyRect? = null,
    val portalOut: JourneyRect? = null,
) {
    val isEmpty get() = stops.isEmpty()
    fun stop(id: String?) = stops.firstOrNull { it.stopId == id }
    fun stopAt(p: MapPoint) = stops.firstOrNull { p in it.hit }
    fun link(id: String?) = links.firstOrNull { it.id == id }

    /** O que a decoração não pode cobrir. */
    val reserved: List<JourneyRect> by lazy {
        stops.flatMap { listOf(it.building.grow(2), it.sign.grow(2)) } + listOfNotNull(portalIn?.grow(2), portalOut?.grow(2))
    }

    companion object {
        val EMPTY = OverworldLayout(240, SerpentineSpec().height(1), 1, emptyList(), emptyList())
    }
}

/**
 * Serpentina de RPG: 3 paradas por linha, linha 1 da esquerda para a direita, linha 2
 * de volta, viradas em U pela borda. Puro e determinístico (sem aleatoriedade);
 * coordenadas inteiras para o renderer em escala inteira.
 */
object SerpentineLayoutEngine {

    fun position(index: Int, spec: SerpentineSpec): Pair<Int, Int> {
        val row = index / spec.columns
        val c = index % spec.columns
        return row to if (row % 2 == 1) spec.columns - 1 - c else c
    }

    fun linkId(fromStopId: String, toStopId: String) = "link-$fromStopId-$toStopId"

    fun layout(
        stops: List<JourneyStop>,
        spec: SerpentineSpec = SerpentineSpec(),
        legBetween: (String, String) -> JourneyLeg? = { _, _ -> null },
        entryLegId: String? = null,
        exitLegId: String? = null,
    ): OverworldLayout {
        if (stops.isEmpty()) return OverworldLayout.EMPTY.copy(width = spec.width, height = spec.height(1))
        val rows = (stops.size + spec.columns - 1) / spec.columns
        val height = spec.height(rows)
        val half = spec.buildingSize / 2
        val nodes = stops.mapIndexed { i, s ->
            val (row, col) = position(i, spec)
            val x = spec.columnX[col]
            val y = spec.topPadding + row * spec.rowHeight
            val last = i == stops.lastIndex
            var side = when (col) {
                0 -> SignSide.RIGHT
                spec.columns - 1 -> SignSide.LEFT
                else -> SignSide.BELOW
            }
            // A saída pelo portal desce reto da parada: a placa sai do caminho.
            if (last && exitLegId != null && side == SignSide.BELOW) side = SignSide.RIGHT
            val building = JourneyRect(x - half, y - spec.buildingSize + 2, spec.buildingSize, spec.buildingSize)
            val sign = when (side) {
                SignSide.RIGHT -> JourneyRect(x + half + 2, y - 26, spec.signW, spec.signH)
                SignSide.LEFT -> JourneyRect(x - half - 2 - spec.signW, y - 26, spec.signW, spec.signH)
                SignSide.BELOW -> JourneyRect(x - spec.signW / 2 - 2, y + 6, spec.signW + 4, spec.signH - 2)
            }
            OverworldStopLayout(s.id, i, row, col, MapPoint(x.toFloat(), y.toFloat()), building, sign, side)
        }
        val links = mutableListOf<OverworldLink>()
        nodes.zipWithNext().forEach { (a, b) ->
            val leg = legBetween(a.stopId, b.stopId)
            val id = linkId(a.stopId, b.stopId)
            val link = if (a.row == b.row) {
                OverworldLink(id, LinkKind.STRAIGHT, a.stopId, b.stopId, leg?.id, JourneyPathBuilder.straight(id, a.point, b.point))
            } else {
                val outward = if (a.col == spec.columns - 1) 1 else -1
                OverworldLink(id, LinkKind.TURN, a.stopId, b.stopId, leg?.id, JourneyPathBuilder.uTurn(id, a.point, b.point, spec.turnBulge, outward))
            }
            links += link
        }
        var portalIn: JourneyRect? = null
        var portalOut: JourneyRect? = null
        entryLegId?.let { legId ->
            val first = nodes.first()
            val topX = (first.building.x - 6).coerceAtLeast(4)
            links.add(0, OverworldLink("portal-in-$legId", LinkKind.PORTAL_IN, null, first.stopId, legId, JourneyPathBuilder.portalIn("portal-in-$legId", topX, first.point)))
            portalIn = JourneyRect(topX - 8, 0, 16, 8)
        }
        exitLegId?.let { legId ->
            val last = nodes.last()
            links += OverworldLink("portal-out-$legId", LinkKind.PORTAL_OUT, last.stopId, null, legId, JourneyPathBuilder.portalOut("portal-out-$legId", last.point, height - 1))
            portalOut = JourneyRect(last.point.x.toInt() - 8, height - 8, 16, 8)
        }
        return OverworldLayout(spec.width, height, rows, nodes, links, portalIn, portalOut)
    }
}
