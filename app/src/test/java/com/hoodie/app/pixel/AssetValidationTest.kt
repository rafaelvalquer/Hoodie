package com.hoodie.app.pixel

import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.HoodieClips
import com.hoodie.app.pixel.debug.SpriteDebugRenderer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneFlag
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.HoodiePalette
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Posture
import com.hoodie.app.pixel.sprite.ProceduralSpriteProvider
import com.hoodie.app.pixel.sprite.SpriteRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Critérios automáticos de qualidade dos sprites e cenas: o build falha se uma
 * animação, direção, âncora ou spot referenciado não existir, ou se o personagem
 * "tremer" (pés deslizando, rosto mudando de lugar).
 */
class AssetValidationTest {

    private val provider = ProceduralSpriteProvider

    private fun allFrames(action: (AnimationId, Direction, Posture, Int) -> Unit) {
        AnimationId.entries.forEach { anim ->
            val dirs = if (anim.clip.directional) Direction.entries else listOf(Direction.FRONT)
            dirs.forEach { dir -> Posture.entries.forEach { p -> anim.frames.indices.forEach { i -> action(anim, dir, p, i) } } }
        }
    }

    @Test
    fun `toda animacao existe, tem frames e duracoes positivas`() {
        AnimationId.entries.forEach { anim ->
            val clip = HoodieClips[anim]
            assertTrue("${anim.name} sem frames", clip.frames.isNotEmpty())
            clip.frames.forEach { assertTrue("${anim.name} duração", it.durationMs > 0) }
            assertTrue("${anim.name} longo demais para loop", !clip.loop || clip.totalMs <= 6_000)
        }
        assertEquals(AnimationId.entries.size, AnimationId.entries.map { it.label }.toSet().size)
    }

    @Test
    fun `todo frame em toda direcao tem 48x72, ancoras dentro do sprite e pes no chao`() {
        allFrames { anim, dir, posture, i ->
            val f = provider.frame(SpriteRequest(anim, dir, i, posture))
            assertEquals(HoodiePainter.WIDTH, f.image.width); assertEquals(HoodiePainter.HEIGHT, f.image.height)
            val a = f.anchors
            listOf(a.head, a.rightHand, a.leftHand, a.back, a.feet).forEach { p ->
                assertTrue("${anim.name}/$dir/$i âncora fora: $p", p.x in 0 until 48 && p.y in -4 until 72)
            }
            // Pés sempre no mesmo ponto: o sprite nunca "desliza" em relação ao chão.
            assertEquals("${anim.name}/$dir/$i pés", HoodiePainter.FEET, a.feet)
        }
    }

    @Test
    fun `rosto frontal nunca muda de coluna`() {
        allFrames { anim, dir, posture, i ->
            val pose = provider.poseFor(SpriteRequest(anim, dir, i, posture))
            if (pose.facing != com.hoodie.app.pixel.sprite.Facing.FRONT) return@allFrames
            val s = HoodiePainter.sprite(pose)
            val tx = if (pose.headOnly) pose.headTilt * 2 else 0
            val lift = pose.lift
            val noseRow = (14..34).firstOrNull { y -> s[23 + tx, y - lift] == HoodiePalette.NOSE && s[24 + tx, y - lift] == HoodiePalette.NOSE }
            assertTrue("${anim.name}/$i: nariz fora do lugar", noseRow != null)
        }
    }

    @Test
    fun `idle nao treme - largura e chao estaveis`() {
        // (SLEEP fica de fora: deitado, a respiração mexe a cabeça inteira de propósito.)
        listOf(AnimationId.IDLE, AnimationId.IDLE_SIT, AnimationId.WORK_READ).forEach { anim ->
            val boxes = anim.frames.indices.map { SpriteDebugRenderer.bbox(provider.frame(SpriteRequest(anim, frameIndex = it)))!! }
            assertTrue("$anim largura variando", boxes.map { it[2] - it[0] }.distinct().size <= 2)
            assertEquals("$anim chão variando", 1, boxes.map { it[3] }.distinct().size)
        }
    }

    @Test
    fun `caminhada lateral tem 8 poses e a direita e o espelho da esquerda`() {
        assertEquals(8, AnimationId.WALK.frames.size)
        for (i in 0 until 8) {
            val left = provider.frame(SpriteRequest(AnimationId.WALK, Direction.LEFT, i))
            val right = provider.frame(SpriteRequest(AnimationId.WALK, Direction.RIGHT, i))
            for (y in 0 until 72) for (x in 0 until 48) assertEquals(left.image[47 - x, y], right.image[x, y])
            assertEquals(left.anchors.mirror(48), right.anchors)
        }
        // As três vistas são desenhos diferentes.
        val views = listOf(Direction.FRONT, Direction.BACK, Direction.LEFT).map { provider.frame(SpriteRequest(AnimationId.WALK, it, 0)).image.pixels.toList() }
        assertEquals(3, views.toSet().size)
    }

    @Test
    fun `paleta limitada do personagem`() {
        val colors = mutableSetOf<Int>()
        allFrames { anim, dir, posture, i ->
            val pose = provider.poseFor(SpriteRequest(anim, dir, i, posture))
            if (pose.item != Item.NONE) return@allFrames
            HoodiePainter.sprite(pose).pixels.filter { it ushr 24 != 0 }.forEach { colors += it }
        }
        assertTrue("cores fora da paleta: ${colors - HoodiePalette.ALL.toSet()}", HoodiePalette.ALL.containsAll(colors))
        assertTrue(colors.size in 12..24)
    }

    @Test
    fun `toda combinacao atividade x contexto resolve cena, spots e sequencias validas`() {
        HoodieActivity.entries.forEach { a ->
            UserContextType.entries.forEach { c ->
                CommuteStyle.entries.forEach { style ->
                    listOf(10, 70).forEach { energy ->
                        val v = VisualDirector.resolve(a, c, commute = style, energy = energy, mood = 90)
                        val scene = SceneRegistry[v.scene]
                        val expectedSpot = if (v.scene in setOf(SceneId.STREET, SceneId.BICYCLE, SceneId.GENERIC_RIDE)) SpotId.WALK else v.spot
                        assertTrue("$a/$c → ${v.scene} sem $expectedSpot", expectedSpot in scene.spots)
                        assertTrue("$a/$c sem ações", v.actions.isNotEmpty())
                        v.actions.mapNotNull { it.spot }.forEach { assertTrue("$a/$c → ${v.scene} sem $it", it in scene.spots) }
                        (v.enter + v.exit + v.approach + v.actions.flatMap { it.enter + it.exit }).forEach { HoodieClips[it] }
                    }
                }
            }
        }
    }

    @Test
    fun `cenas renderizam em todos os periodos, portas e flags`() {
        val r = SceneRenderer()
        SceneId.entries.forEach { id ->
            val s = SceneRegistry[id]
            assertTrue("$id sem spot padrão", s.defaultSpot in s.spots)
            DayPeriod.entries.forEach { p ->
                for (door in 0..SceneEnv.DOOR_OPEN) {
                    r.renderEmpty(s, SceneEnv(p, 600, doorFrame = door, flags = SceneFlag.entries.toSet()), 0)
                }
            }
        }
    }
}
