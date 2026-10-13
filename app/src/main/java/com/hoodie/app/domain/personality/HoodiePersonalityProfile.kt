package com.hoodie.app.domain.personality

import kotlin.math.roundToInt

/** Traços da personalidade. São estáveis: não decaem com o tempo nem mudam com as necessidades. */
enum class PersonalityTrait { CURIOSITY, COFFEE_AFFINITY, SOCIABILITY, REST_AFFINITY, SPONTANEITY }

/**
 * Quem o Hoodie é. Necessidades (energia, fome...) dizem como ele está agora; o perfil diz como
 * ele costuma reagir. Valores em 0..100, versionados para uma futura migração do perfil.
 */
data class HoodiePersonalityProfile(
    val id: String = "hoodie",
    val version: Int = 1,
    val curiosity: Int = 80,
    val coffeeAffinity: Int = 85,
    val sociability: Int = 65,
    val restAffinity: Int = 70,
    val spontaneity: Int = 50,
) {
    init {
        PersonalityTrait.entries.forEach { trait ->
            require(value(trait) in 0..100) { "Traço $trait fora de 0..100: ${value(trait)}" }
        }
    }

    fun value(trait: PersonalityTrait): Int = when (trait) {
        PersonalityTrait.CURIOSITY -> curiosity
        PersonalityTrait.COFFEE_AFFINITY -> coffeeAffinity
        PersonalityTrait.SOCIABILITY -> sociability
        PersonalityTrait.REST_AFFINITY -> restAffinity
        PersonalityTrait.SPONTANEITY -> spontaneity
    }

    companion object {
        val DEFAULT = HoodiePersonalityProfile()
    }
}

/**
 * Pesos com personalidade, só para escolhas visuais (microações e reações). Decisões persistidas
 * do simulador e do HoodieDecisionEngine não passam por aqui.
 *
 *     café      × (1 + afinidade/300)   85 → ×1,28
 *     descanso  × (1 + afinidade/300)   70 → ×1,23
 *
 * Só ajusta ações elegíveis com peso > 0: uma ação bloqueada (peso 0 ou ausente) continua
 * bloqueada, e nenhuma ação vira obrigatória.
 */
object PersonalityWeightPolicy {
    fun coffeeMultiplier(p: HoodiePersonalityProfile): Float = 1f + p.coffeeAffinity / 300f
    fun restMultiplier(p: HoodiePersonalityProfile): Float = 1f + p.restAffinity / 300f
    fun curiosityMultiplier(p: HoodiePersonalityProfile): Float = 1f + p.curiosity / 300f
    fun socialMultiplier(p: HoodiePersonalityProfile): Float = 1f + p.sociability / 300f

    fun adjust(base: Int, multiplier: Float, eligible: Boolean): Int =
        if (!eligible || base <= 0) base else maxOf(1, (base * multiplier).roundToInt())
}
