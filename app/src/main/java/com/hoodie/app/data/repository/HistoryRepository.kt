package com.hoodie.app.data.repository

import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityDao
import com.hoodie.app.core.database.MemoryDao
import com.hoodie.app.core.database.TimelineDao
import com.hoodie.app.core.model.ContextEvent
import com.hoodie.app.core.model.Memory
import com.hoodie.app.core.model.TimelineEvent
import com.hoodie.app.engine.timeline.ActivitySpan
import com.hoodie.app.engine.timeline.ContextSpan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Leitura do histórico local (linha do tempo, resumos, memórias). */
@Singleton
class HistoryRepository @Inject constructor(
    private val timelineDao: TimelineDao,
    private val contextDao: ContextEventDao,
    private val activityDao: HoodieActivityDao,
    private val memoryDao: MemoryDao,
) {
    fun timeline(from: Long, to: Long): Flow<List<TimelineEvent>> = timelineDao.observeRange(from, to).map { list ->
        list.map { TimelineEvent(it.id, it.timestamp, it.actor, it.emoji, it.text) }
    }

    fun contextSpans(from: Long, to: Long): Flow<List<ContextSpan>> = contextDao.observeOverlapping(from, to).map { list ->
        list.map { ContextSpan(it.type, it.startedAt, it.endedAt) }
    }

    fun activitySpans(from: Long, to: Long): Flow<List<ActivitySpan>> = activityDao.observeOverlapping(from, to).map { list ->
        list.map { ActivitySpan(it.activity, it.startedAt, it.endedAt) }
    }

    val memories: Flow<List<Memory>> = memoryDao.observeAll().map { list -> list.map { Memory(it.key, it.emoji, it.title, it.unlockedAt) } }
}

fun ContextEventEntity.toDomain() = ContextEvent(id, type, startedAt, endedAt, confidence, placeId, source, venueType)
