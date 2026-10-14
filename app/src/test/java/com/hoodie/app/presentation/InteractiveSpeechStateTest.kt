package com.hoodie.app.presentation

import com.hoodie.app.engine.interaction.*
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.presentation.screens.home.InteractiveSpeechState
import org.junit.Assert.*
import org.junit.Test

class InteractiveSpeechStateTest {
    private fun request(text: String?) = InteractionRequest(
        InteractionResponse("pet", listOf(AnimationId.PET_HEAD), text, 3_500), SceneId.HOME, 1,
    )

    @Test fun interactivePhraseWinsAndClearReturnsTheCurrentAutomaticPhrase() {
        val state = InteractiveSpeechState()
        assertEquals("automática", state.resolve("automática"))
        state.present(request("carinho"))
        assertEquals("carinho", state.resolve("automática atualizada"))
        state.clear()
        assertEquals("automática atualizada", state.resolve("automática atualizada"))
        assertNull(state.resolve(null))
    }

    @Test fun silentResponseDoesNotReplaceThePhraseOrItsIdentity() {
        val state = InteractiveSpeechState()
        val spoken = request("carinho")
        state.present(spoken)
        state.present(request(null))
        assertSame(spoken, state.current)
        state.clear()
        state.present(request(null))
        assertNull(state.current)
    }
}
