package com.hoodie.app.pixel.character

/** Qual pé está plantado no chão neste quadro. */
enum class FootContact { LEFT, RIGHT, BOTH, NONE }

/**
 * Posição explícita dos pés (px no canvas, relativos ao centro do quadril; + = para a
 * frente do personagem). Quando informada, substitui a tabela de passada da pose:
 * é o que permite sincronizar o pé plantado com o deslocamento no mundo (sem deslizar).
 */
data class GaitSample(
    val nearX: Int,
    val farX: Int,
    val nearLift: Int = 0,
    val farLift: Int = 0,
    val contact: FootContact = FootContact.BOTH,
)

/** Movimento secundário fornecido pelo personagem semântico, separado do estado da pose. */
data class CharacterRenderMotion(
    val stepAmplitude: Int = 2,
    val armSwing: Int = 2,
    /** null usa a oscilação corporal padrão; zero mantém a cauda parada. */
    val tailAmplitude: Int? = null,
    val gait: GaitSample? = null,
    /** Atraso das orelhas (follow-through): + abaixa/inclina para trás. */
    val earLag: Int = 0,
    /** Balanço lateral do tronco e cabeça (pato), sem mover os pés. */
    val sway: Int = 0,
    /** Respiração: 1 = peito expandido (ombros 1 px mais largos e altos). */
    val breath: Int = 0,
)
