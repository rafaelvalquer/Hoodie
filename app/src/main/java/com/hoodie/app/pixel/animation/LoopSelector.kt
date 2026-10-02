package com.hoodie.app.pixel.animation

import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.VisualState
import kotlin.random.Random

/** Weighted microaction choice and duration, sharing the player's random source. */
internal class LoopSelector(private val random: Random) {
    fun pick(v: VisualState, previous: MicroAction?): MicroAction {
        val options = if (v.actions.size > 1 && previous != null) v.actions.filter { it != previous } else v.actions
        val total = options.sumOf { it.weight }.coerceAtLeast(1)
        var roll = random.nextInt(total)
        for (action in options) {
            if (roll < action.weight) return action
            roll -= action.weight
        }
        return options.first()
    }

    fun duration(action: MicroAction): Long {
        val span = (action.maxMs - action.minMs).coerceAtLeast(0)
        return action.minMs + if (span > 0) random.nextLong(span + 1) else 0
    }
}
