package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.domain.phoneinsights.model.AppSession

enum class RealUserActivitySource { PHONE, MOBILITY, CONTEXT, TIMELINE }

data class RealUserActivity(
    val timestamp: Long,
    val source: RealUserActivitySource,
)

/** Consolida somente sinais persistidos do usuário; as atividades simuladas do Hoodie ficam de fora. */
object RealUserActivityBuilder {
    private val realContextSources = setOf(
        ContextSource.GEOFENCE, ContextSource.MANUAL, ContextSource.CONFIRMATION,
        ContextSource.MOBILITY, ContextSource.LOCATION_CHECK, ContextSource.USER_CORRECTION,
    )

    fun build(
        from: Long,
        until: Long,
        appSessions: List<AppSession> = emptyList(),
        mobilitySessions: List<MobilitySessionEntity> = emptyList(),
        contexts: List<ContextEventEntity> = emptyList(),
        timeline: List<TimelineEventEntity> = emptyList(),
        analysisEnabled: Boolean = false,
        mobilityEnabled: Boolean = true,
    ): List<RealUserActivity> = buildList {
        fun addIfInRange(timestamp: Long, source: RealUserActivitySource) {
            if (timestamp in from until until) add(RealUserActivity(timestamp, source))
        }

        if (analysisEnabled) appSessions.forEach { session ->
            addIfInRange(session.startedAt, RealUserActivitySource.PHONE)
            addIfInRange(session.endedAt, RealUserActivitySource.PHONE)
        }
        if (mobilityEnabled) mobilitySessions.filter { it.confirmed }.forEach { session ->
            addIfInRange(session.startedAt, RealUserActivitySource.MOBILITY)
            session.endedAt?.let { addIfInRange(it, RealUserActivitySource.MOBILITY) }
        }
        contexts.filter { it.source in realContextSources }.forEach { context ->
            addIfInRange(context.startedAt, RealUserActivitySource.CONTEXT)
            context.endedAt?.let { addIfInRange(it, RealUserActivitySource.CONTEXT) }
        }
        timeline.filter { it.actor == TimelineActor.USER }.forEach { addIfInRange(it.timestamp, RealUserActivitySource.TIMELINE) }
    }.distinct().sortedWith(compareBy<RealUserActivity> { it.timestamp }.thenBy { it.source.ordinal })
}
