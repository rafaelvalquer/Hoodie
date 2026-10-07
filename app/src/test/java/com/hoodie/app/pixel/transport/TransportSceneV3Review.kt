package com.hoodie.app.pixel.transport

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.animation.RenderFrame
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.VisualDirector
import kotlin.random.Random

/** Renderização das cenas V3 pelo caminho real (máquina de estados + SceneRenderer). */
object TransportSceneV3Review {
    val PERIOD_MINUTE = mapOf(DayPeriod.MORNING to 7 * 60, DayPeriod.DAY to 11 * 60, DayPeriod.EVENING to 18 * 60 + 30, DayPeriod.NIGHT to 22 * 60)

    data class Shot(val image: PixelBuffer, val frame: RenderFrame, val timeMs: Long)

    /** Renderiza [mode] no [period] em [atMs]. */
    fun render(mode: MovementMode, period: DayPeriod, atMs: Long = 4_000L, seed: Int = 401): Shot {
        val sm = AnimationStateMachine(Random(seed + mode.ordinal))
        sm.setVisual(VisualDirector.resolve(HoodieActivity.COMMUTING, UserContextType.COMMUTING, commute = CommuteStyle.WALK, mobilityMode = mode), 1)
        val minute = PERIOD_MINUTE.getValue(period)
        var t = 1L
        var frame = sm.frame(t, minute, period)!!
        while (t < atMs) { t += 33; frame = sm.frame(t, minute, period)!! }
        frame = frame.copy(env = frame.env.copy(transportAmbient = TransportVisualRegistry.profileFor(mode, CommuteStyle.WALK).ambient))
        val image = PixelBuffer(240, 320).also { it.copyFrom(SceneRenderer().render(frame, t)) }
        return Shot(image, frame, t)
    }
}
