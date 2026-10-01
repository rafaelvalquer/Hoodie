package com.hoodie.app.pixel

import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.VisualDirector
import org.junit.Test
import kotlin.random.Random

/** Gera a "Scene Gallery" em PNG para revisão visual (build/pixel-preview). */
class ScenePreviewTest {

    private fun shot(activity: HoodieActivity, ctx: UserContextType, period: DayPeriod, commute: CommuteStyle = CommuteStyle.WALK, at: Long = 3_000): PixelBuffer {
        val sm = AnimationStateMachine(Random(4))
        val v = VisualDirector.resolve(activity, ctx, commute = commute, variant = 1)
        sm.setVisual(v, 1)
        var frame = sm.frame(1, 10 * 60 + 8, period)!!
        var t = 1L
        while (t < at) { t += 33; frame = sm.frame(t, 10 * 60 + 8, period)!! }
        val out = PixelBuffer(240, 320)
        out.copyFrom(SceneRenderer().render(frame, t))
        return out
    }

    @Test
    fun exportVerticalSlice() {
        val slice = listOf(
            shot(HoodieActivity.SLEEPING, UserContextType.HOME, DayPeriod.NIGHT),
            shot(HoodieActivity.BREAKFAST, UserContextType.HOME, DayPeriod.MORNING),
            shot(HoodieActivity.COMMUTING, UserContextType.COMMUTING, DayPeriod.MORNING),
            shot(HoodieActivity.WORKING, UserContextType.WORK, DayPeriod.DAY),
            shot(HoodieActivity.EATING, UserContextType.LUNCH, DayPeriod.DAY),
            shot(HoodieActivity.COMMUTING, UserContextType.COMMUTING, DayPeriod.EVENING, CommuteStyle.BUS),
            shot(HoodieActivity.GAMING, UserContextType.HOME, DayPeriod.EVENING),
            shot(HoodieActivity.TRAINING, UserContextType.GYM, DayPeriod.EVENING),
            shot(HoodieActivity.IDLE, UserContextType.UNKNOWN, DayPeriod.NIGHT),
            shot(HoodieActivity.WALKING, UserContextType.LEISURE, DayPeriod.DAY),
            shot(HoodieActivity.PHONE, UserContextType.VISITING, DayPeriod.DAY),
            shot(HoodieActivity.WORKING, UserContextType.WORK, DayPeriod.NIGHT),
        )
        PreviewExport.sheet("vertical_slice", slice, columns = 4, scale = 2)
    }
}
