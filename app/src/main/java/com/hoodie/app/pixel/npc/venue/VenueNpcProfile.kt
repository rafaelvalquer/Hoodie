package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.character.CharacterStyle

/**
 * Personalidade e função de um personagem de estabelecimento. Os pesos escolhem a próxima
 * atividade; as restrições (ocupação, cooldown, histórico) vêm da sessão.
 */
data class VenueNpcProfile(
    val id: String,
    val role: VenueRole,
    val preferredActions: Map<VenueAction, Int>,
    val socialProbability: Float,
    val minIdleMs: Long,
    val maxIdleMs: Long,
) {
    init {
        require(minIdleMs in 1..maxIdleMs) { "$id: pausa inválida $minIdleMs..$maxIdleMs" }
        require(socialProbability in 0f..1f) { "$id: probabilidade social $socialProbability" }
        require(preferredActions.values.all { it >= 0 }) { "$id: peso negativo" }
    }
}

/** Como o personagem começa a sessão. */
enum class VenueStart {
    /** No posto (funcionário atrás do balcão, sorveteiro no carrinho). */
    POST,

    /** Fora de cena, entra depois de [VenueActorSpec.startDelayMs]. */
    OFFSTAGE,

    /** Já dentro, num ponto de atividade (alguém que estava na mesa quando a cena abriu). */
    INSIDE,
}

/** Um personagem da sessão: identidade visual + perfil + semente própria. */
internal data class VenueActorSpec(
    val style: CharacterStyle,
    val profile: VenueNpcProfile,
    val slotSeed: Int,
    val start: VenueStart,
    val startDelayMs: Long = 0,
    /** Ponto inicial quando [start] é POST ou INSIDE. */
    val startSpot: String? = null,
)
