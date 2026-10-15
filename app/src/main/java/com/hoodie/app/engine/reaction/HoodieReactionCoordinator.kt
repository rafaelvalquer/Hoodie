package com.hoodie.app.engine.reaction

import com.hoodie.app.pixel.scene.SceneId

/** One Home owner; one dispatched reaction and one preferred pending intent. No storage or clock IO. */
class HoodieReactionCoordinator {
    private var scene: SceneId? = null
    private var revision = -1L
    private var available = false
    private var active: ReactionIntent? = null
    private var pending: ReactionIntent? = null
    private var animationComplete = false
    private val events = ArrayDeque<ReactionLifecycle>()
    val lifecycle: List<ReactionLifecycle> get() = events.toList()
    fun contains(id: String) = active?.id == id || pending?.id == id
    var presentation: ReactionPresentation? = null
        private set

    private fun record(intent: ReactionIntent, state: ReactionState, now: Long, reason: String) {
        events.addLast(ReactionLifecycle(intent.id, state, now, reason))
        while (events.size > 64) events.removeFirst()
    }

    fun context(sceneId: SceneId?, contextVersion: Long, enabled: Boolean, now: Long) {
        if (scene != sceneId || revision != contextVersion || !enabled) cancelAll(now, "CONTEXT_OR_VISIBILITY_CHANGED")
        scene = sceneId; revision = contextVersion; available = enabled
        expire(now)
    }

    fun request(intent: ReactionIntent, now: Long): Boolean {
        expire(now)
        record(intent, ReactionState.REQUESTED, now, "SUBMITTED")
        if (!available || scene != intent.sceneId || revision != intent.contextVersion || now >= intent.expiresAt ||
            active?.id == intent.id || pending?.id == intent.id) {
            record(intent, ReactionState.REJECTED, now, "STALE_UNAVAILABLE_OR_DUPLICATE"); return false
        }
        val waiting = pending
        if (waiting != null && !ReactionArbitrationPolicy.replacesPending(intent, waiting)) {
            record(intent, ReactionState.REJECTED, now, "PENDING_PRIORITY"); return false
        }
        waiting?.let { record(it, ReactionState.CANCELLED, now, "REPLACED_BY_HIGHER_PRIORITY") }
        // An authorized but not yet presented lower-priority intent may be superseded.
        active?.takeIf { (presentation == null || animationComplete) && it.priority.rank < intent.priority.rank }?.let {
            if (presentation != null) finish(it.id, now)
            else { record(it, ReactionState.CANCELLED, now, "SUPERSEDED_BEFORE_PRESENTATION"); active = null }
        }
        pending = intent
        record(intent, ReactionState.ACCEPTED, now, "PRIORITY_ACCEPTED")
        return true
    }

    fun dispatch(now: Long, safe: Boolean, essential: Boolean = false, socialBusy: Boolean = false): ReactionIntent? {
        expire(now)
        val intent = pending ?: return null
        if (active != null || !ReactionArbitrationPolicy.canDispatch(safe, essential, socialBusy, intent)) {
            if (events.lastOrNull()?.let { it.id == intent.id && it.state == ReactionState.DEFERRED } != true)
                record(intent, ReactionState.DEFERRED, now, "WAITING_FOR_SAFE_PRESENTATION")
            return null
        }
        pending = null; active = intent; animationComplete = false
        return intent
    }

    fun current(id: String, now: Long): Boolean { expire(now); return available && active?.id == id }

    fun started(id: String, now: Long): Boolean {
        expire(now)
        val intent = active?.takeIf { it.id == id } ?: return false
        if (presentation != null) return false
        presentation = ReactionPresentation(id, intent.dialogue, intent.source, intent.priority, now,
            intent.durationMs, intent.sceneId, intent.contextVersion)
        record(intent, ReactionState.STARTED, now, "RENDERER_PRESENTED")
        return true
    }

    fun finish(id: String, now: Long) {
        val intent = active?.takeIf { it.id == id } ?: return
        record(intent, if (presentation == null) ReactionState.CANCELLED else ReactionState.FINISHED,
            now, if (presentation == null) "ENGINE_DID_NOT_START" else "PRESENTATION_FINISHED")
        active = null; presentation = null
    }

    fun animationFinished(id: String, now: Long) {
        val intent = active?.takeIf { it.id == id } ?: return
        animationComplete = true
        if (presentation == null || intent.dialogue == null || pending?.let { it.priority.rank > intent.priority.rank } == true)
            finish(id, now)
    }

    fun cancel(id: String, now: Long, reason: String) {
        active?.takeIf { it.id == id }?.let { record(it, ReactionState.CANCELLED, now, reason); active = null; presentation = null }
        pending?.takeIf { it.id == id }?.let { record(it, ReactionState.CANCELLED, now, reason); pending = null }
    }

    fun cancelAll(now: Long, reason: String) {
        active?.let { cancel(it.id, now, reason) }
        pending?.let { cancel(it.id, now, reason) }
    }

    fun expire(now: Long) {
        pending?.takeIf { now >= it.expiresAt }?.let { record(it, ReactionState.EXPIRED, now, "PENDING_DEADLINE"); pending = null }
        active?.takeIf { presentation == null && now >= it.expiresAt }?.let {
            record(it, ReactionState.EXPIRED, now, "PRESENTATION_DEADLINE"); active = null
        }
        presentation?.takeIf { now >= it.expiresAt }?.let { finish(it.id, now) }
    }
}
