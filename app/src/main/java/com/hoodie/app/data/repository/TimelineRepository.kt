package com.hoodie.app.data.repository

import com.hoodie.app.core.database.TimelineDao
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.TimelineSourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Única porta de escrita da timeline. Cada linha guarda a origem, então quando a
 * origem é corrigida (ex.: saída desfeita por oscilação de GPS) a linha some junto.
 */
@Singleton
class TimelineRepository @Inject constructor(private val dao: TimelineDao) {

    suspend fun recordContext(eventId: Long, emoji: String, text: String, at: Long): Long =
        dao.insert(TimelineEventEntity(timestamp = at, actor = TimelineActor.USER, emoji = emoji, text = text, sourceType = TimelineSourceType.CONTEXT, sourceId = eventId))

    /** Atividade do Hoodie: a origem é o instante em que ela começou. */
    suspend fun recordHoodieActivity(emoji: String, text: String, startedAt: Long): Long =
        dao.insert(TimelineEventEntity(timestamp = startedAt, actor = TimelineActor.HOODIE, emoji = emoji, text = text, sourceType = TimelineSourceType.HOODIE_ACTIVITY, sourceId = startedAt))

    suspend fun recordMemory(emoji: String, text: String, at: Long): Long =
        dao.insert(TimelineEventEntity(timestamp = at, actor = TimelineActor.HOODIE, emoji = emoji, text = text, sourceType = TimelineSourceType.MEMORY))

    suspend fun recordSystem(emoji: String, text: String, at: Long): Long =
        dao.insert(TimelineEventEntity(timestamp = at, actor = TimelineActor.HOODIE, emoji = emoji, text = text, sourceType = TimelineSourceType.SYSTEM))

    /** Remove tudo o que foi registrado a partir de uma origem. */
    suspend fun invalidateSource(type: TimelineSourceType, id: Long): Int = dao.deleteBySource(type, id)

    /** Remove as linhas de um tipo de origem num intervalo (inclusivo). */
    suspend fun invalidateRange(type: TimelineSourceType, from: Long, to: Long): Int = dao.deleteSourceRange(type, from, to)

    /** Troca o que foi dito sobre uma origem (mantém uma única linha por origem). */
    suspend fun replaceSource(type: TimelineSourceType, id: Long, actor: TimelineActor, emoji: String, text: String, at: Long): Long {
        dao.deleteBySource(type, id)
        return dao.insert(TimelineEventEntity(timestamp = at, actor = actor, emoji = emoji, text = text, sourceType = type, sourceId = id))
    }
}
