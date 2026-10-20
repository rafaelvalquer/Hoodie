package com.hoodie.app.presentation.components

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.engine.interaction.InteractionRequest
import com.hoodie.app.engine.interaction.InteractionResponse
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualState
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class InteractionPresentationTest {
    private val response = InteractionResponse("petting", listOf(AnimationId.PET_HEAD, AnimationId.PURR), null, 0)

    private fun machine() = AnimationStateMachine(Random(1)).also {
        it.place(VisualState(SceneId.BEACH, SpotId.CENTER,
            listOf(MicroAction(AnimationId.IDLE, 1, 60_000, 60_000))), 1)
    }

    private fun request(id: Long) = InteractionRequest(response, SceneId.BEACH, 1, requestId = id)

    @Test fun `idle bitmap already rendering cannot finish a request received later`() {
        val oldFrame = requireNotNull(machine().frame(2, 600, DayPeriod.DAY))
        val pending = request(1)
        val presentation = InteractionPresentation().apply { request = pending }
        val started = mutableListOf<InteractionRequest>()
        val finished = mutableListOf<InteractionRequest>()

        presentation.present(oldFrame, null, started::add, finished::add)

        assertSame(pending, presentation.request)
        assertSame(oldFrame, presentation.frame)
        assertTrue(started.isEmpty())
        assertTrue(finished.isEmpty())
    }

    @Test fun `real reaction still announces its start after an older idle bitmap arrives`() {
        val machine = machine()
        val oldFrame = requireNotNull(machine.frame(2, 600, DayPeriod.DAY))
        val pending = request(1)
        assertTrue(machine.interact(response, SceneId.BEACH, 3))
        val presentation = InteractionPresentation().apply { request = pending }
        val started = mutableListOf<InteractionRequest>()
        val finished = mutableListOf<InteractionRequest>()

        presentation.present(oldFrame, null, started::add, finished::add)
        for (now in 4L..8_000L step 20) {
            val frame = requireNotNull(machine.frame(now, 600, DayPeriod.DAY))
            presentation.present(frame, pending, started::add, finished::add)
        }

        assertEquals(listOf(pending), started)
        assertEquals(listOf(pending), finished)
        assertNull(presentation.request)
    }

    @Test fun `old request cannot acknowledge its replacement even with the same response object`() {
        val oldRequest = request(1)
        val replacement = request(2)
        val oldFrame = requireNotNull(machine().frame(2, 600, DayPeriod.DAY))
            .copy(interactionStarted = response, interactionBusy = false)
        val presentation = InteractionPresentation().apply { request = replacement }
        val started = mutableListOf<InteractionRequest>()
        val finished = mutableListOf<InteractionRequest>()

        presentation.present(oldFrame, oldRequest, started::add, finished::add)

        assertSame(replacement, presentation.request)
        assertTrue(started.isEmpty())
        assertTrue(finished.isEmpty())
    }

    @Test fun `matching idle frame still completes an unstarted expired or cancelled reaction`() {
        val idleFrame = requireNotNull(machine().frame(2, 600, DayPeriod.DAY))
        val pending = request(1)
        val presentation = InteractionPresentation().apply { request = pending }
        val started = mutableListOf<InteractionRequest>()
        val finished = mutableListOf<InteractionRequest>()

        presentation.present(idleFrame, pending, started::add, finished::add)
        presentation.present(idleFrame, pending, started::add, finished::add)

        assertTrue(started.isEmpty())
        assertEquals(listOf(pending), finished)
        assertNull(presentation.request)
    }
}
