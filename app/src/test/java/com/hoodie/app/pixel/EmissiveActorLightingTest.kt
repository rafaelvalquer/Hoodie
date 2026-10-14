package com.hoodie.app.pixel

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.npc.venue.MallVenue
import com.hoodie.app.pixel.renderer.ActorMask
import com.hoodie.app.pixel.renderer.Lighting
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.Light
import com.hoodie.app.pixel.scene.MallScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.VisualDirector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Vitrines e telas acendem à noite, mas quem está na frente delas (o gato, NPCs) recebe só a luz ambiente.
 * Antes, o mapa de luz era por posição na tela: o gato diante da vitrine ficava claro e "transparente"
 * enquanto os pés (fora do retângulo) ficavam escuros.
 */
class EmissiveActorLightingTest {
    private val night = SceneEnv(DayPeriod.NIGHT, 21 * 60)
    private val emissive = Light.Emissive(8, 86, 58, 154) // vitrine da loja A no MallScene

    @Test fun `o plano de luz da vitrine tem mapa para atores sem o brilho emissivo`() {
        val plan = Lighting.plan(MallScene(), night)
        assertNotNull(plan)
        val actorMap = plan!!.actorMap!!
        val w = MallScene().width
        val inWindow = 100 * w + 20 // dentro da vitrine
        val outside = 100 * w + 120 // parede, fora de qualquer emissiva
        assertEquals("a vitrine segue acesa", 256, plan.map[inWindow] ushr 20)
        assertTrue("o ator na frente dela recebe a luz ambiente", (actorMap[inWindow] ushr 20) < 256)
        assertEquals("fora das emissivas os mapas são iguais", plan.map[outside], actorMap[outside])
        assertEquals(listOf(8, 10, 234).first(), plan.emissiveBounds!![0].coerceAtMost(8))
    }

    @Test fun `de dia nao ha mapa e cenas sem emissiva nao pagam o custo`() {
        assertNull(Lighting.plan(MallScene(), SceneEnv(DayPeriod.DAY, 12 * 60)))
    }

    @Test fun `a mascara marca so o que o ator desenhou dentro da regiao emissiva`() {
        val buffer = PixelBuffer(240, 320).also { it.clear() }
        val mask = ActorMask(240, 320)
        mask.begin(intArrayOf(10, 10, 50, 50))
        mask.track(buffer) {
            buffer.pixels[20 * 240 + 20] = 0xFF112233.toInt() // dentro
            buffer.pixels[100 * 240 + 100] = 0xFF445566.toInt() // fora da região rastreada
        }
        assertTrue(mask.mask[20 * 240 + 20])
        assertFalse(mask.mask[100 * 240 + 100])
        mask.begin(null) // próximo quadro: limpa e desativa
        assertFalse(mask.mask[20 * 240 + 20])
        assertFalse(mask.active)
    }

    @Test fun `o Hoodie na frente da vitrine a noite nao e iluminado como o vidro`() {
        val sm = AnimationStateMachine(Random(1))
        sm.setVisual(VisualDirector.resolve(HoodieActivity.IDLE, UserContextType.HOME), 1)
        val base = sm.frame(1_000, 21 * 60, DayPeriod.NIGHT)!!
        val here = base.copy(scene = MallScene(), env = night, x = MallVenue.HOODIE_STOREFRONT_A_X, y = MallVenue.HOODIE_STOREFRONT_Y)
        val away = here.copy(x = -2_000) // mesma cena sem o Hoodie
        val day = SceneEnv(DayPeriod.DAY, 12 * 60)
        val renderer = SceneRenderer()
        fun snapshot(frame: com.hoodie.app.pixel.animation.RenderFrame) = renderer.render(frame, 1_000).pixels.copyOf()
        val rawWith = snapshot(here.copy(env = day)); val rawWithout = snapshot(away.copy(env = day))
        val lit = snapshot(here)
        val ambient = Lighting.plan(MallScene(), night)!!.actorMap!!
        val w = 240
        var checked = 0
        for (y in emissive.y0..emissive.y1) for (x in emissive.x0..emissive.x1) {
            val i = y * w + x
            if (rawWith[i] == rawWithout[i]) continue // pixel do cenário, não do Hoodie
            checked++
            val f = ambient[i]
            for ((shift, factor) in listOf(16 to (f ushr 20), 8 to ((f shr 10) and 0x3FF), 0 to (f and 0x3FF))) {
                val expected = (((rawWith[i] shr shift) and 0xFF) * factor) shr 8
                val actual = (lit[i] shr shift) and 0xFF
                assertEquals("pixel ($x,$y) canal $shift", expected, actual)
            }
        }
        assertTrue("o teste precisa pegar pixels do Hoodie dentro da vitrine (achou $checked)", checked > 40)
    }
}