package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.Lighting
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.scene.Light
import com.hoodie.app.pixel.scene.PixelScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.Spot
import com.hoodie.app.pixel.scene.SpotId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LightingDayFastPathTest {
    @Test
    fun `daylight skips building dynamic light lists`() {
        val scene = CountingLightScene()

        assertNull(Lighting.map(scene, SceneEnv(DayPeriod.DAY, 12 * 60)))

        assertEquals(0, scene.lightCalls)
    }

    private class CountingLightScene : PixelScene(SceneId.UNKNOWN) {
        var lightCalls = 0
        override val spots: Map<SpotId, Spot> = emptyMap()
        override fun drawBackground(b: PixelBuffer, env: SceneEnv) = Unit
        override fun props() = emptyList<com.hoodie.app.pixel.scene.Prop>()
        override fun lights(env: SceneEnv): List<Light> {
            lightCalls++
            return emptyList()
        }
    }
}
