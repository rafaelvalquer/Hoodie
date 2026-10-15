package com.hoodie.app.engine.reaction

enum class ReactionState { REQUESTED, ACCEPTED, DEFERRED, STARTED, FINISHED, REJECTED, EXPIRED, CANCELLED }
data class ReactionLifecycle(val id: String, val state: ReactionState, val at: Long, val reason: String)
