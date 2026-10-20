package com.hoodie.app.presentation.components

import com.hoodie.app.engine.interaction.InteractionRequest
import com.hoodie.app.pixel.animation.RenderFrame

internal class InteractionPresentation {
    var frame: RenderFrame? = null
    var request: InteractionRequest? = null
    var active = true

    fun present(
        actual: RenderFrame,
        renderedRequest: InteractionRequest?,
        started: (InteractionRequest) -> Unit,
        finished: (InteractionRequest) -> Unit,
    ) {
        frame = actual
        val current = request
        // A new request can arrive while this frame's bitmap renders off Main.
        if (current != null && current === renderedRequest) {
            if (actual.interactionStarted === current.response) started(current)
            if (!actual.interactionBusy) {
                finished(current)
                request = null
            }
        }
    }
}
