package com.hoodie.app.presentation.screens.home

import androidx.compose.runtime.*
import com.hoodie.app.engine.interaction.InteractionRequest
import kotlinx.coroutines.delay

/** Ephemeral Home speech, committed only when the renderer presents a reaction. */
@Stable
internal class InteractiveSpeechState {
    var current: InteractionRequest? by mutableStateOf(null)
        private set

    fun present(request: InteractionRequest) {
        if (request.response.text != null) current = request
    }

    fun clear() { current = null }
    fun resolve(automatic: String?): String? = current?.response?.text ?: automatic
}

@Composable
internal fun rememberInteractiveSpeech(): InteractiveSpeechState {
    val state = remember { InteractiveSpeechState() }
    LaunchedEffect(state.current) {
        val request = state.current ?: return@LaunchedEffect
        delay(request.response.bubbleDurationMs)
        if (state.current === request) state.clear()
    }
    return state
}
