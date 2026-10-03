package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.pixel.diary.BuildingState
import com.hoodie.app.pixel.diary.DiaryMapBuildings
import com.hoodie.app.pixel.diary.journey.JourneyPalette as P
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Estado visual de uma parada (plano §11.2). Selecionada é um extra, não um estado. */
enum class NodeState { UPCOMING, ACTIVE, VISITED }

/**
 * Uma parada: prédio do tipo do lugar (o mesmo do mapa clássico), calçada até a
 * plataforma e a plataforma onde o Hoodie para. Ativa pulsa em dourado;
 * selecionada ganha um contorno azul extra; retorno ganha uma setinha ↺.
 */
object JourneyNodeRenderer {

    fun paint(b: PixelBuffer, node: JourneyNodeLayout, state: NodeState, selected: Boolean, isReturn: Boolean, lightsOn: Boolean, timeMs: Long) {
        val building = when (state) {
            NodeState.UPCOMING -> BuildingState.UPCOMING
            NodeState.ACTIVE -> BuildingState.ACTIVE
            NodeState.VISITED -> BuildingState.VISITED
        }
        DiaryMapBuildings.paint(b, node.asPlaceNode(""), building, lightsOn, timeMs)
        pad(b, node, state, timeMs)
        if (isReturn) returnMark(b, node, if (state == NodeState.UPCOMING) P.dim(P.SELECTED) else P.SELECTED)
        if (selected) {
            val (x, y, w, h) = node.building.pixels.toList()
            frame(b, x - 3, y - 3, x + w + 2, y + h + 2, P.SELECTED)
            frame(b, node.pad.x - 2, node.pad.y - 2, node.pad.right + 1, node.pad.bottom + 1, P.SELECTED)
        }
    }

    /** Plataforma de pedra com contorno; ativa = anel dourado pulsando em 3 tempos. */
    private fun pad(b: PixelBuffer, node: JourneyNodeLayout, state: NodeState, timeMs: Long) {
        val r = node.pad
        val fill = if (state == NodeState.UPCOMING) P.dim(P.PAD) else P.PAD
        val shade = if (state == NodeState.UPCOMING) P.dim(P.PAD_SHADE) else P.PAD_SHADE
        b.outlined(r.x, r.y, r.right - 1, r.bottom - 1, fill, P.OUTLINE)
        b.hline(r.x + 1, r.right - 2, r.bottom - 2, shade)
        // Pedrinhas.
        b.set(r.x + 4, r.y + 3, shade); b.set(r.x + 11, r.y + 5, shade); b.set(r.right - 6, r.y + 3, shade)
        if (state == NodeState.ACTIVE) {
            val grow = DiaryMapBuildings.pulseWidth(timeMs)
            frame(b, r.x - grow, r.y - grow, r.right - 1 + grow, r.bottom - 1 + grow, P.GOLD)
        }
    }

    /** ↺ em 5×5 no canto da plataforma: "voltou para cá". */
    private fun returnMark(b: PixelBuffer, node: JourneyNodeLayout, color: Int) {
        val x = if (node.side == JourneySide.LEFT) node.pad.x - 7 else node.pad.right + 2
        val y = node.pad.y + 1
        b.glyph(listOf(".###.", "#...#", "#.#..", "#..#.", ".##.."), x, y, color)
    }

    private fun frame(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        b.hline(x0, x1, y0, c); b.hline(x0, x1, y1, c); b.vline(x0, y0, y1, c); b.vline(x1, y0, y1, c)
    }
}
