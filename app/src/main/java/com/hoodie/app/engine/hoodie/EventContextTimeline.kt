package com.hoodie.app.engine.hoodie

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.timeline.ContextSpan

/**
 * [ContextTimeline] a partir dos eventos salvos. Sem evento cobrindo o instante,
 * usa [fallback] — a "rotina provável".
 */
class EventContextTimeline(
    events: List<ContextSpan>,
    private val fallback: (Long) -> UserContextType,
) : ContextTimeline {

    private val sorted = events.sortedBy { it.startedAt }
    private val boundaries = sorted.flatMap { listOfNotNull(it.startedAt, it.endedAt) }.distinct().sorted()

    override fun contextAt(time: Long): UserContextType =
        sorted.lastOrNull { it.startedAt <= time && (it.endedAt == null || time < it.endedAt) }?.type ?: fallback(time)

    override fun nextChangeAfter(after: Long): Long? = boundaries.firstOrNull { it > after }
}
