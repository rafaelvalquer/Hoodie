package com.hoodie.app.engine.interaction

/** One Home lifecycle owns reservations and history. No reaction survives leaving Home. */
class HoodieInteractionSession {
    val history = HoodieInteractionHistory()
    var id = 0L
        private set
    var active = false
        private set
    private var reserved: InteractionRequest? = null
    private var hasStarted = false

    fun start() { id++; active = true; reserved = null; hasStarted = false; history.clear() }
    fun stop() { id++; active = false; reserved = null; hasStarted = false; history.clear() }
    fun reserve(response: InteractionResponse, scene: com.hoodie.app.pixel.scene.SceneId, acceptedAt: Long, context: InteractionContext? = null): InteractionRequest? {
        if (!active || reserved != null) return null
        hasStarted = false
        history.acceptGesture(acceptedAt)
        return InteractionRequest(response, scene, id, context).also { reserved = it }
    }
    fun current(request: InteractionRequest) = active && request.sessionId == id && reserved === request
    fun started(request: InteractionRequest, now: Long): Boolean {
        if (!current(request) || hasStarted) return false
        hasStarted = true
        history.remember(request.response, now)
        return true
    }
    fun finish(request: InteractionRequest) { if (current(request)) reserved = null }
    fun cancelPending() { reserved = null; hasStarted = false }
}
