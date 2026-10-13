package com.hoodie.app.pixel.scene

import com.hoodie.app.domain.personality.HoodiePersonalityProfile
import com.hoodie.app.domain.personality.PersonalityWeightPolicy
import com.hoodie.app.pixel.animation.AnimGroup
import com.hoodie.app.pixel.animation.AnimationId

/**
 * Personalidade nas microações: café e descanso ganham peso relativo, sem criar nem tirar ações.
 * Estados com uma ação só (caminhar, dormir) ficam iguais, porque só há o que escolher quando há
 * mais de uma opção.
 */
object PersonalityVisuals {
    /** Pausas de descanso: alongar, cochilar sentado, ficar à toa sentado ou no banco. */
    val restAnimations: Set<AnimationId> = setOf(
        AnimationId.STRETCH_SIT, AnimationId.STRETCH, AnimationId.IDLE_SIT, AnimationId.NAP_SIT, AnimationId.LEISURE_BENCH,
    )

    fun isCoffee(anim: AnimationId): Boolean = anim.group == AnimGroup.COFFEE
    fun isRest(anim: AnimationId): Boolean = anim in restAnimations

    fun apply(actions: List<MicroAction>, profile: HoodiePersonalityProfile): List<MicroAction> {
        if (actions.size < 2) return actions
        val coffee = PersonalityWeightPolicy.coffeeMultiplier(profile)
        val rest = PersonalityWeightPolicy.restMultiplier(profile)
        return actions.map { action ->
            val weight = when {
                isCoffee(action.anim) -> PersonalityWeightPolicy.adjust(action.weight, coffee, eligible = true)
                isRest(action.anim) -> PersonalityWeightPolicy.adjust(action.weight, rest, eligible = true)
                else -> action.weight
            }
            if (weight == action.weight) action else action.copy(weight = weight)
        }
    }
}
