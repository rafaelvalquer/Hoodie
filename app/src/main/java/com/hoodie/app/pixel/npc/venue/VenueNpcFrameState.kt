package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.NpcMovement
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Item

/** Fala ativa de um personagem (um balão por vez na cena). */
data class VenueSpeechEvent(val line: String, val startedAt: Long, val durationMs: Long)

/** O que um personagem está fazendo num instante. */
data class VenueNpcFrameState(
    val npcId: String,
    val role: VenueRole,
    val state: VenueNpcState,
    val action: VenueAction?,
    /** Ponto onde está (ou para onde anda). */
    val spot: String?,
    val movement: NpcMovement,
    val speech: VenueSpeechEvent?,
    /** Objeto na mão: xícara, sacola, mala, pipoca… acompanha a âncora da mão, inclusive andando. */
    val held: Item,
    /** Dentro de uma loja, do elevador ou fora de cena: não é desenhado. */
    val hidden: Boolean,
    val seated: Boolean,
) {
    val x: Int get() = movement.x
    val floorY: Int get() = movement.floorY
}

/**
 * Estado dos objetos e do ambiente de uma cena de estabelecimento, derivado da simulação
 * (pedidos, eventos, reservas) — nunca de flags do Hoodie. Cada cena tem o seu.
 */
sealed interface VenueAmbientState {
    val scene: SceneId
    val timeMs: Long
}

/** Tudo da cena num instante; calculado uma vez por quadro e reaproveitado. */
data class VenueFrameSnapshot(
    val timeMs: Long,
    val npcs: List<VenueNpcFrameState>,
    val ambient: VenueAmbientState,
)
