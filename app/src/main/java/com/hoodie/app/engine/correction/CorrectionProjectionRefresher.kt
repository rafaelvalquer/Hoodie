package com.hoodie.app.engine.correction

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.TimelineSourceType
import com.hoodie.app.core.time.ClockProvider
import javax.inject.Inject

/** Reconstrói o que deriva dos eventos restaurados: timeline (dentro da transação) e resumo digital por contexto (depois dela). */
class CorrectionProjectionRefresher @Inject constructor(
    private val db: HoodieDatabase,
    private val phone: PhoneContextRecalculator,
    private val clock: ClockProvider,
) {
    suspend fun rewriteTimeline(event: ContextEventEntity) {
        val dao = db.timelineDao()
        dao.deleteBySource(TimelineSourceType.CONTEXT, event.id)
        val place = event.placeId?.let { db.placeDao().getById(it)?.name }
        dao.insert(TimelineEventEntity(timestamp = event.startedAt, actor = TimelineActor.USER, emoji = event.type.emoji,
            text = event.type.label + (place?.let { " · $it" } ?: ""), sourceType = TimelineSourceType.CONTEXT, sourceId = event.id))
    }

    suspend fun removeTimeline(eventId: Long) {
        db.timelineDao().deleteBySource(TimelineSourceType.CONTEXT, eventId)
    }

    suspend fun recalculatePhone(from: Long) {
        phone.recalculate(from, clock.nowMillis())
    }
}