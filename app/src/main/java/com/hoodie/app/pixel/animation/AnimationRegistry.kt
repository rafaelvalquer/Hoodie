package com.hoodie.app.pixel.animation

import com.hoodie.app.pixel.animation.definitions.*

/** Complete procedural fallback catalog; sprite sheets may override individual clips. */
object AnimationRegistry {
    val clips: Map<AnimationId, AnimationClip> = buildMap {
        val families = listOf(walkAnimations(), idleAnimations(), sleepAnimations(), workAnimations(), phoneAnimations(), foodAnimations(), gymAnimations(), leisureAnimations(), transitionAnimations(), studyAnimations(), shoppingAnimations(), visitAnimations(), transportAnimations())
        families.forEach { family ->
            family.forEach { (id, clip) ->
                require(id !in this) { "Duplicate animation family: $id" }
                put(id, clip)
            }
        }
        require(keys == AnimationId.entries.toSet()) { "Incomplete animation registry" }
    }
}
