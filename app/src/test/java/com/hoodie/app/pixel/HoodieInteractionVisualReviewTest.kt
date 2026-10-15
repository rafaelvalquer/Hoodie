package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.engine.interaction.InteractionResponse
import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.scene.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Produces a review artifact; passing this test is not visual or device approval. */
class HoodieInteractionVisualReviewTest {
    @Test fun exportBeforePettingAndResumeWithSceneObjects() {
        val rows = listOf(
            Triple(SceneId.HOME, AnimationId.IDLE, emptySet()),
            Triple(SceneId.OFFICE, AnimationId.COFFEE_SIT, setOf(SceneFlag.MUG_IN_HAND)),
            Triple(SceneId.RESTAURANT, AnimationId.WAIT_FOOD, setOf(SceneFlag.FOOD_SERVED)),
            Triple(SceneId.STORE, AnimationId.STORE_HOLD_GARMENT, setOf(SceneFlag.GARMENT_HELD)),
        )
        val sheet = PixelBuffer(240 * 5, 320 * rows.size)
        rows.forEachIndexed { row, (scene, animation, flags) ->
            val machine = AnimationStateMachine(Random(1))
            val spot = when (scene) {
                SceneId.OFFICE -> SpotId.DESK
                SceneId.RESTAURANT -> SpotId.TABLE
                SceneId.STORE -> SpotId.MIRROR
                else -> SpotId.CENTER
            }
            machine.place(VisualState(scene, spot, listOf(MicroAction(animation, 1, 60_000, 60_000)), steadyFlags = flags), 1)
            val before = machine.frame(702, 600, DayPeriod.DAY)!!
            assertTrue(machine.interact(InteractionResponse("review", listOf(AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE), null, 0, 3_000), scene, 703))
            val sequence = (704L..8_000L step 40).map { machine.frame(it, 600, DayPeriod.DAY)!! }
            val frames = listOf(before) + listOf(AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE).map { id -> sequence.first { it.animation == id } } + sequence.last()
            assertEquals(animation, frames.last().animation)
            val renderer = SceneRenderer(true)
            frames.forEachIndexed { col, frame ->
                val buffer = renderer.render(frame, 10_000L + col * 300)
                sheet.blit(buffer, col * 240, row * 320)
            }
            renderer.dispose()
        }
        val output = File("../build/interaction-review/contact-sheet.png")
        output.parentFile?.mkdirs()
        PreviewExport.write(output, sheet, 1, null)
        assertTrue(output.isFile && output.length() > 0)
        File(output.parentFile, "contact-sheet.txt").writeText("Columns: before, PET_HEAD, PURR, NUZZLE, resumed\n" + rows.joinToString("\n") { "${it.first}: ${it.second}" })
    }
}
