package com.hoodie.app.pixel

import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.pixel.sprite.HoodiePainter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Validações automáticas dos assets: o build falha se uma animação, cena ou
 * âncora referenciada não existir — o equivalente a pegar "hoodie_coffe.png".
 */
class AssetValidationTest {

    @Test
    fun `todo frame tem 48x72 e o rosto nao muda de posicao`() {
        AnimationId.entries.forEach { anim ->
            assertTrue("${anim.name} sem frames", anim.frames.isNotEmpty())
            assertTrue("${anim.name} fps", anim.fps in 1..12)
            anim.frames.forEach { pose ->
                val s = HoodiePainter.sprite(pose)
                assertEquals(48, s.width); assertEquals(72, s.height)
                // Nariz sempre nas colunas 23–24 (identidade do personagem); só a altura muda (respiração/sentar).
                val noseRow = (20..30).firstOrNull { y -> s[23, y] == com.hoodie.app.pixel.sprite.HoodiePalette.NOSE && s[24, y] == com.hoodie.app.pixel.sprite.HoodiePalette.NOSE }
                assertTrue("${anim.name}: nariz fora do lugar", noseRow != null)
            }
        }
    }

    @Test
    fun `ids de animacao e cena sao unicos e toda cena tem spot padrao`() {
        assertEquals(AnimationId.entries.size, AnimationId.entries.map { it.label }.toSet().size)
        SceneId.entries.forEach { id ->
            val s = SceneRegistry[id]
            assertTrue("$id sem spot padrão", s.defaultSpot in s.spots)
            s.spots.values.forEach { assertTrue("$id spot fora da cena", it.x in 0..240 && it.y in 0..320) }
        }
    }

    @Test
    fun `toda combinacao atividade x contexto resolve para uma cena valida com spots existentes`() {
        HoodieActivity.entries.forEach { a ->
            UserContextType.entries.forEach { c ->
                CommuteStyle.entries.forEach { style ->
                    val v = VisualDirector.resolve(a, c, commute = style)
                    val scene = SceneRegistry[v.scene]
                    assertTrue("$a/$c → ${v.scene} sem ${v.spot}", v.spot in scene.spots)
                    assertTrue("$a/$c sem ações", v.actions.isNotEmpty())
                    v.actions.mapNotNull { it.spot }.forEach { assertTrue("$a/$c → ${v.scene} sem $it", it in scene.spots) }
                }
            }
        }
    }

    @Test
    fun `maquina de estados faz transicao entre cenas com fade e chega ao destino`() {
        val sm = AnimationStateMachine(Random(1))
        val renderer = SceneRenderer()
        sm.setVisual(VisualDirector.resolve(HoodieActivity.WORKING, UserContextType.WORK), 0)
        var t = 0L
        repeat(30) { t += 33; renderer.render(sm.frame(t, 600, DayPeriod.DAY)!!, t) }
        sm.setVisual(VisualDirector.resolve(HoodieActivity.COMMUTING, UserContextType.COMMUTING, commute = CommuteStyle.WALK), t)
        var sawFade = false
        repeat(300) {
            t += 33
            val f = sm.frame(t, 600, DayPeriod.DAY)!!
            if (f.fade < 1f) sawFade = true
            renderer.render(f, t)
        }
        assertTrue(sawFade)
        assertEquals(SceneId.STREET, sm.currentVisual!!.scene)
        assertEquals(AnimationStateMachine.Phase.LOOP, sm.phase)
    }

    @Test
    fun `todas as cenas renderizam em todos os periodos`() {
        val r = SceneRenderer()
        SceneId.entries.forEach { id ->
            DayPeriod.entries.forEach { p ->
                r.renderEmpty(SceneRegistry[id], com.hoodie.app.pixel.scene.SceneEnv(p, 600), 0)
            }
        }
    }
}
