package com.hoodie.app.pixel.npc.events

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.npc.office.OfficeExecutiveTimeline
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.OfficeScene
import com.hoodie.app.pixel.scene.SceneEnv
import org.junit.Test

/** Folha de revisão da entrega de documento (build/pixel-preview/story-events/). Só exporta: aprovação é humana. */
class SceneStoryReviewExportTest {
    @Test
    fun `exporta os momentos da entrega de documento`() {
        val seed = (1..12).first { s ->
            val session = OfficeNpcDirector.createSession(SceneEnv(DayPeriod.DAY, 9 * 60, variant = s, daySeed = s))
            (0 until 20L).any { session.social.deliveries!!.deliveryAt(it * OfficeExecutiveTimeline.CYCLE_MS) != null }
        }
        val env = SceneEnv(DayPeriod.DAY, 9 * 60, variant = seed, daySeed = seed)
        val probe = OfficeNpcDirector.createSession(env)
        val d = (0 until 20L).firstNotNullOf { probe.social.deliveries!!.deliveryAt(it * OfficeExecutiveTimeline.CYCLE_MS) }
        val moments = listOf(
            d.startAt - 500,                       // folha na mesa, coelho trabalhando
            d.pickupAt + 100,                      // levantou com a folha
            (d.walkOutAt + d.arriveAt) / 2,        // a caminho
            d.arriveAt + 200,                      // chega ao chefe
            d.handoffAt + 200,                     // chefe recebe e lê
            (d.returnAt + d.deskArriveAt) / 2,     // volta sem a folha
            d.endsAt + 500,                        // sentado de novo
            d.bulldogExitAt - 9_000,               // chefe vai embora com a folha
        )
        val renderer = SceneRenderer()
        val scene = OfficeScene()
        val frames = moments.map { t ->
            val frame = renderer.renderEmpty(scene, env, t)
            com.hoodie.app.pixel.renderer.PixelBuffer(frame.width, frame.height).apply { copyFrom(frame) }
        }
        PreviewExport.sheet("story-events/office-document-delivery", frames, columns = 4, scale = 2)
        // Recorte ampliado da faixa dos NPCs para conferir a folha nas mãos.
        val crops = frames.map { f ->
            com.hoodie.app.pixel.renderer.PixelBuffer(240, 70).apply {
                for (y in 0 until 70) for (x in 0 until 240) set(x, y, f.get(x, 140 + y))
            }
        }
        PreviewExport.sheet("story-events/office-document-delivery-closeup", crops, columns = 2, scale = 4)
    }
}
