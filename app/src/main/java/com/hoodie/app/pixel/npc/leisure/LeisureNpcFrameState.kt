package com.hoodie.app.pixel.npc.leisure

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMovement
import com.hoodie.app.pixel.npc.NpcReaction

/** Fala ativa de um visitante: um só balão por vez na cena. */
data class LeisureSpeechEvent(val line: String, val startedAt: Long, val durationMs: Long)

/** Retrato de um visitante num instante: o mesmo valor alimenta o sprite, o balão e a profundidade. */
data class LeisureNpcFrameState(
    val npcId: String,
    val intent: LeisureNpcIntent,
    val activity: LeisureActivity?,
    val spot: LeisureNpcSpot?,
    val movement: NpcMovement,
    val speech: LeisureSpeechEvent?,
    /** Reação ao Hoodie sobreposta à atividade (olhar, sorrir, acenar). */
    val reaction: NpcReaction?,
    /** Fora da tela (saiu e ainda não voltou). */
    val offstage: Boolean,
    /** Id do encontro social em andamento (-1 = nenhum). */
    val socialId: Int,
) {
    val x: Int get() = movement.x
    val floorY: Int get() = movement.floorY
    val animation: NpcAnimation get() = movement.animation
    val seated: Boolean get() = movement.seated
}

/** Tudo o que a cena precisa num instante, calculado uma vez e reaproveitado no desenho. */
data class LeisureFrameSnapshot(
    val timeMs: Long,
    val npcs: List<LeisureNpcFrameState>,
    val ambient: LeisureAmbientState,
    /** Quem está no banco agora: "hoodie", o id de um NPC ou null. */
    val benchHolder: String?,
    val activeSocialId: Int,
)
