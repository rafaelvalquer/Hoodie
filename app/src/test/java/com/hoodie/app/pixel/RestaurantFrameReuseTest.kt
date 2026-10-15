package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcDirector
import com.hoodie.app.pixel.performance.SceneBenchmarkScenes
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneFlag
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.*
import org.junit.Test

class RestaurantFrameReuseTest {
    @Test fun `cached meal state equals fresh reconstruction through forward and backward seeks`() {
        for (minute in listOf(600, 720, 1320)) for (served in listOf(false, true)) {
            val env = SceneEnv(DayPeriod.DAY, minute, daySeed = 2026, flags = if (served) setOf(SceneFlag.FOOD_SERVED) else emptySet())
            val cached = RestaurantNpcDirector.brain(env)
            for (time in listOf(0L, 100, 7_000, 60_000, 600_000, 7_000, 300_000, 0)) {
                val expected = RestaurantNpcDirector.brain(env).stateAt(time)
                val actual = cached.stateAt(time)
                assertEquals(expected, actual)
                assertSame(actual, cached.stateAt(time))
                assertEquals(RestaurantNpcDirector.brain(env).tableStateAt(time), cached.tableStateAt(time))
            }
            assertSame(cached.stateAt(-1), cached.stateAt(0))
        }
    }

    @Test fun `renderer reuses guest and story slots until a relevant input changes`() {
        val renderer = SceneRenderer()
        val machine = AnimationStateMachine()
        machine.place(SceneBenchmarkScenes.cases.single { it.scene == SceneId.RESTAURANT }.visual(), 1)
        val frame = machine.frame(3_000, 600, DayPeriod.DAY)!!
        val baseField = SceneRenderer::class.java.getDeclaredField("restaurantBase").apply { isAccessible = true }
        val storyField = SceneRenderer::class.java.getDeclaredField("restaurantStory").apply { isAccessible = true }
        try {
            renderer.render(frame, 3_000)
            val base = baseField.get(renderer)
            val story = storyField.get(renderer)
            renderer.render(frame.copy(env = frame.env.copy(period = DayPeriod.NIGHT)), 3_033)
            assertSame(base, baseField.get(renderer))
            assertSame(story, storyField.get(renderer))
            renderer.render(frame.copy(env = frame.env.copy(clockMinute = 601)), 3_066)
            assertNotSame(base, baseField.get(renderer))
            val minuteBase = baseField.get(renderer)
            renderer.render(frame.copy(env = frame.env.copy(flags = frame.env.flags + SceneFlag.FOOD_SERVED)), 3_099)
            assertNotSame(minuteBase, baseField.get(renderer))
        } finally { renderer.dispose() }
    }
}
