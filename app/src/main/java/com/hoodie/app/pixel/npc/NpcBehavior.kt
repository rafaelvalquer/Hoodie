package com.hoodie.app.pixel.npc

import com.hoodie.app.pixel.character.CharacterRenderMotion
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.sprite.Facing

/**
 * Peso e personalidade do movimento de cada espécie (V2).
 *
 *     Bulldog: passo curto, bob menor, braço pesado
 *     Coelho:  bob maior, passo longo, orelhas atrasadas
 *     Rato:    passo curto, cadência alta, cauda atrasada
 *     Pato:    balanço lateral, asa entreaberta
 */
data class SpeciesMotionProfile(
    val walkBob: Int,
    val stepAmplitude: Int,
    val headLag: Int,
    val tailAmplitude: Int,
    val armSwing: Int,
    val earLag: Int = 0,
    val tailLag: Int = 0,
    /** 1 = referência; >1 mais pesado (passo curto e lento). */
    val bodyWeight: Float = 1f,
    /** Comprimento do passo no quadro (px do canvas 48×72). */
    val stepLength: Int = 8,
    /** Altura máxima do pé no swing. */
    val footLift: Int = 2,
    /** Velocidade de deslocamento: ms por pixel de cena. */
    val msPerPixel: Long = 30,
    /** Balanço lateral do tronco (pato). */
    val sway: Boolean = false,
    /** Braços como asas entreabertas. */
    val wingArms: Boolean = false,
) {
    fun renderMotion() = CharacterRenderMotion(
        stepAmplitude = stepAmplitude,
        armSwing = armSwing,
        tailAmplitude = tailAmplitude,
    )
}

/** Um passo do roteiro de comportamento (determinístico, sem sorteio por quadro). */
data class NpcStep(
    val animation: NpcAnimation,
    val durationMs: Long,
    val seated: Boolean = animation.seated,
    val facing: Facing? = null,
    val reaction: NpcReaction? = null,
)

/** Sequência cíclica: o passo atual depende só de tempo + seed. */
data class NpcBehaviorSequence(val steps: List<NpcStep>) {
    init { require(steps.isNotEmpty() && steps.all { it.durationMs > 0 }) }
    val cycleMs: Long get() = steps.sumOf { it.durationMs }

    /** Passo e tempo local dentro dele. */
    fun at(timeMs: Long, seed: Int): Pair<NpcStep, Long> {
        var k = Math.floorMod(timeMs + seed * 1_291L, cycleMs)
        for (s in steps) {
            if (k < s.durationMs) return s to k
            k -= s.durationMs
        }
        return steps.last() to 0
    }

    companion object {
        fun of(vararg steps: NpcStep) = NpcBehaviorSequence(steps.toList())
    }
}

data class NpcBehaviorProfile(
    /** Animação principal (usada quando não há sequência). */
    val animation: NpcAnimation,
    val motion: SpeciesMotionProfile,
    val sequence: NpcBehaviorSequence? = null,
    /** Reações raras ao Hoodie quando parado. */
    val reactions: Boolean = true,
)

data class NpcSpeechProfile(
    val lines: List<String>,
    val cycleMs: Long = 48_000,
    val visibleMs: Long = 1_500,
)

data class NpcPathPoint(
    val x: Int,
    val floorY: Int,
    val stop: NpcAnimation? = null,
    val holdMs: Long = 0,
    val seated: Boolean = false,
)

/** Waypoints percorrem o plano da cena; os pontos de parada podem direcionar fala/olhar. */
data class NpcPath(val points: List<NpcPathPoint>, val millisPerPixel: Long = 35, val repeat: Boolean = true) {
    init { require(points.size >= 2); require(millisPerPixel > 0); require(points.all { it.holdMs >= 0 }) }
}

/** Papel do trecho do caminho: entrar em cena, andar, parar ou sair. */
enum class PathPhase { ENTER, WALK, STOP, EXIT, TURN, IDLE }

data class NpcMovement(
    val x: Int,
    val floorY: Int,
    val facingRight: Boolean,
    val animation: NpcAnimation,
    val localTimeMs: Long,
    /** Distância total andada no ciclo (px de cena): dirige a passada sem deslizar. */
    val walkedPx: Float = 0f,
    val phase: PathPhase = PathPhase.IDLE,
    val seated: Boolean = animation.seated,
    val facing: Facing? = null,
    val reaction: NpcReaction? = null,
)

/** Escalas permitidas por profundidade (sempre nearest-neighbor). */
object AmbientScale {
    const val BACKGROUND_FAR = 0.90f
    const val BACKGROUND = 0.95f
    const val DEFAULT = 1.00f
    const val MIDGROUND = 1.00f
    const val FOREGROUND = 1.00f
    const val FULL = 1.00f
    val allowed = listOf(BACKGROUND_FAR, BACKGROUND, DEFAULT).distinct()
}

enum class NpcDepth { BACKGROUND_FAR, BACKGROUND, SCENE }

object NpcScalePolicy {
    fun scale(style: CharacterStyle, depth: NpcDepth): Float {
        val desired = when (depth) {
            NpcDepth.BACKGROUND_FAR -> AmbientScale.BACKGROUND_FAR
            NpcDepth.BACKGROUND -> AmbientScale.BACKGROUND
            NpcDepth.SCENE -> AmbientScale.FULL
        }
        return maxOf(desired, style.minimumAmbientScale)
    }
}

object SpeciesMotionProfiles {
    fun forCharacter(style: CharacterStyle): SpeciesMotionProfile = forSpecies(style.species.id)

    fun forSpecies(id: String): SpeciesMotionProfile = when (id) {
        "bulldog" -> SpeciesMotionProfile(
            walkBob = 1, stepAmplitude = 1, headLag = 1, tailAmplitude = 0, armSwing = 1,
            bodyWeight = 1.4f, stepLength = 6, footLift = 1, msPerPixel = 34,
        )
        "rabbit" -> SpeciesMotionProfile(
            walkBob = 2, stepAmplitude = 2, headLag = 0, tailAmplitude = 1, armSwing = 2,
            earLag = 2, bodyWeight = 0.8f, stepLength = 10, footLift = 3, msPerPixel = 26,
        )
        "mouse" -> SpeciesMotionProfile(
            walkBob = 1, stepAmplitude = 2, headLag = 1, tailAmplitude = 2, armSwing = 2,
            tailLag = 2, bodyWeight = 0.7f, stepLength = 5, footLift = 2, msPerPixel = 24,
        )
        "duck" -> SpeciesMotionProfile(
            walkBob = 1, stepAmplitude = 1, headLag = 1, tailAmplitude = 0, armSwing = 1,
            bodyWeight = 1.1f, stepLength = 5, footLift = 1, msPerPixel = 34, sway = true, wingArms = true,
        )
        "raccoon" -> SpeciesMotionProfile(
            walkBob = 1, stepAmplitude = 1, headLag = 1, tailAmplitude = 1, armSwing = 1,
            tailLag = 2, stepLength = 7, msPerPixel = 30,
        )
        "dog" -> SpeciesMotionProfile(
            walkBob = 1, stepAmplitude = 1, headLag = 1, tailAmplitude = 1, armSwing = 1,
            earLag = 1, tailLag = 1, stepLength = 8, msPerPixel = 28,
        )
        else -> SpeciesMotionProfile(walkBob = 1, stepAmplitude = 2, headLag = 0, tailAmplitude = 2, armSwing = 2, tailLag = 1)
    }
}
