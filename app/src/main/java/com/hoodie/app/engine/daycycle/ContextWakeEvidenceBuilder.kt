package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType

object ContextWakeEvidenceBuilder {
    private val realSources = setOf(ContextSource.GEOFENCE, ContextSource.MANUAL, ContextSource.CONFIRMATION, ContextSource.MOBILITY, ContextSource.LOCATION_CHECK)

    fun build(contexts: List<ContextEventEntity>, from: Long, until: Long): List<WakeEvidence> = buildList {
        contexts.filter { it.source in realSources }.forEach { event ->
            val started = event.startedAt
            if (started in from until until) {
                add(WakeEvidence(started, WakeEvidenceType.CONTEXT_ACTIVITY, WakeDetectionPolicy.CONTEXT_SCORE))
            }
            val ended = event.endedAt
            if (event.type == UserContextType.HOME && ended != null && ended in from until until) {
                add(WakeEvidence(ended, WakeEvidenceType.HOME_EXIT, WakeDetectionPolicy.HOME_EXIT_SCORE))
            }
        }
        sortBy { it.timestamp }
    }

    fun hoodieWake(activities: List<HoodieActivityEntity>, from: Long, until: Long): List<WakeEvidence> =
        activities.asSequence()
            .filter { it.activity == HoodieActivity.WAKING_UP && it.startedAt in from until until }
            .map { WakeEvidence(it.startedAt, WakeEvidenceType.HOODIE_WAKING_UP, WakeDetectionPolicy.HOODIE_SCORE) }
            .sortedBy { it.timestamp }
            .toList()
}
