package com.hoodie.app.engine.interaction

/** One Home lifecycle owns reservations and history. No reaction survives leaving Home. */
class HoodieInteractionSession(private val config: HoodieInteractionConfig = HoodieInteractionConfig()) {
    val history = HoodieInteractionHistory(config)
    var id = 0L
        private set
    var active = false
        private set
    private var reserved: InteractionRequest? = null
    private var hasStarted = false
    private var nextRequestId = 0L

    fun start() { id++; active = true; reserved = null; hasStarted = false; history.clear() }
    fun stop() { id++; active = false; reserved = null; hasStarted = false; history.clear() }
    fun reserve(response: InteractionResponse, scene: com.hoodie.app.pixel.scene.SceneId, acceptedAt: Long, context: InteractionContext? = null): InteractionRequest? {
        if (!active || reserved != null) return null
        hasStarted = false
        history.acceptGesture(acceptedAt)
        return InteractionRequest(response, scene, id, context, ++nextRequestId,
            acceptedAt, acceptedAt + config.maxPendingMs).also { reserved = it }
    }
    fun current(request: InteractionRequest) = active && request.sessionId == id && reserved === request
    fun eligible(request: InteractionRequest, now: Long) = current(request) && (hasStarted || now < request.expiresAtMs)
    fun started(request: InteractionRequest, now: Long): Boolean {
        if (!eligible(request, now) || hasStarted) return false
        hasStarted = true
        history.remember(request.response, now)
        return true
    }
    fun finish(request: InteractionRequest) { if (current(request)) reserved = null }
    fun cancelPending() { reserved = null; hasStarted = false }
}
