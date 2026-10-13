package com.hoodie.app.pixel.animation

import com.hoodie.app.pixel.scene.SpotId

/**
 * Onde o Hoodie está e para onde vai, publicado em cada [RenderFrame]. Serve para o ambiente
 * dividir o espaço com ele (ex.: o banco do Passeio) pelo DESTINO da microação, não pela
 * distância: quando ele escolhe sentar no banco, quem estiver lá já começa a levantar.
 */
data class HoodieOccupancy(
    val present: Boolean,
    /** Pés do Hoodie na cena. */
    val x: Int,
    val y: Int,
    /** Spot da microação atual ou do trajeto em andamento (null = sem destino). */
    val targetSpot: SpotId?,
    val animation: AnimationId?,
    val seated: Boolean,
    val transition: Transition,
) {
    enum class Transition { NONE, APPROACHING, LEAVING }

    val walking: Boolean get() = transition != Transition.NONE

    companion object {
        /** Sem Hoodie na cena (galerias, prévias, renderEmpty). */
        val ABSENT = HoodieOccupancy(false, 0, 0, null, null, false, Transition.NONE)
    }
}
