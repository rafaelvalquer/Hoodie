package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

/** Construction DSL shared by independent animation families. */
internal class ClipDefinitions {

    val S = HoodiePose()
    val SIT = HoodiePose(legs = Legs.SIT)
    val I = HoodiePose(legs = Legs.INHERIT)

    class Builder {
        val frames = mutableListOf<AnimationFrame>()
        fun f(ms: Long, pose: HoodiePose, vararg events: AnimationEvent) { frames += AnimationFrame(pose, ms, events.toSet()) }
    }

    val clips = linkedMapOf<AnimationId, AnimationClip>()

    fun clip(id: AnimationId, loop: Boolean = true, policy: InterruptPolicy = FINISH_FRAME, directional: Boolean = false, build: Builder.() -> Unit) {
        require(id !in clips) { "Duplicate clip: $id" }
        clips[id] = AnimationClip(id, Builder().apply(build).frames, loop, policy, directional)
    }



}
