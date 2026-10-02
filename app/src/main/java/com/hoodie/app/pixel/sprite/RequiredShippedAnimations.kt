package com.hoodie.app.pixel.sprite

import com.hoodie.app.pixel.animation.AnimGroup
import com.hoodie.app.pixel.animation.AnimationId

/**
 * Clips que PRECISAM existir como arte final no APK da V0.2 (o resto pode ser
 * procedural). O ShippedSheetsValidationTest falha numa versão de release sem eles,
 * e o CI confere os arquivos dentro do APK.
 */
object RequiredShippedAnimations {

    val required: Set<Pair<AnimationId, Facing>> = setOf(
        AnimationId.WALK to Facing.SIDE,
        AnimationId.WALK to Facing.FRONT,
        AnimationId.WALK to Facing.BACK,
        AnimationId.IDLE to Facing.FRONT,
        AnimationId.SLEEP to Facing.FRONT,
        AnimationId.WORK_TYPING to Facing.FRONT,
    )

    /** Arquivos que levam esses clips para o APK. */
    val files = listOf("hoodie_walk", "hoodie_idle", "hoodie_sleep", "hoodie_work")

    fun missing(available: Set<Pair<AnimationId, Facing>>): Set<Pair<AnimationId, Facing>> = required - available

    /** Cobertura visual: fração dos clips (vista frontal ou lateral quando direcional) servidos por arte final. */
    data class Coverage(val finalCount: Int, val total: Int) {
        val percent: Int get() = if (total == 0) 0 else finalCount * 100 / total
    }

    fun coverage(available: Set<Pair<AnimationId, Facing>>, group: AnimGroup? = null): Coverage {
        val clips = AnimationId.entries.filter { group == null || it.group == group }
        val done = clips.count { id ->
            if (id.clip.directional) Facing.entries.all { (id to it) in available } else (id to Facing.FRONT) in available
        }
        return Coverage(done, clips.size)
    }
}
