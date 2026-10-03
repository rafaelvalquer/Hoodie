package com.hoodie.app.engine.context

import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.TransactionRunner
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.TimelineSourceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.data.repository.TimelineRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Por que o contexto mudou (log/Developer Lab). */
enum class TransitionReason {
    GEOFENCE_ENTER, GEOFENCE_EXIT, LUNCH_CHECK, COMMUTE_CHECK,
    CONFIRMATION_REJECTED, NEW_PLACE_ANSWER, MANUAL, PLACE_SAVED, ROUTINE_FALLBACK,
    MOBILITY_START, MOBILITY_ARRIVAL, ARRIVAL_REJECTED,
}

data class TransitionResult(
    /** Evento vigente depois da operação. */
    val event: ContextEventEntity,
    /** Evento que estava aberto antes (null no primeiro contexto). */
    val previous: ContextEventEntity?,
    /** true quando um boundary novo foi criado. */
    val changed: Boolean,
)

/**
 * Único lugar que escreve em context_events. Só existem duas operações:
 *
 *  1. manter o contexto atual, ou
 *  2. fechar o atual e abrir outro (boundary).
 *
 * Nunca muda retroativamente o tipo de um evento — é o boundary que faz o
 * HoodieSimulator perceber a mudança na hora. A única exceção é a correção
 * histórica explícita [revertFlap].
 */
@Singleton
class ContextTransitionService @Inject constructor(
    private val contextDao: ContextEventDao,
    private val timeline: TimelineRepository,
    private val places: PlaceRepository,
    private val tx: TransactionRunner,
    private val log: DebugEventLogger,
) {

    suspend fun transition(
        to: UserContextType,
        at: Long,
        confidence: Float,
        placeId: Long?,
        source: ContextSource,
        reason: TransitionReason,
        note: String? = null,
    ): TransitionResult = tx.run {
        val current = contextDao.current()
        keep(current, to, placeId, confidence, source)?.let { return@run it }

        // Um evento atrasado nunca fecha o atual antes de ele começar.
        val start = if (current != null) maxOf(at, current.startedAt) else at
        if (current != null) {
            if (start == current.startedAt) {
                // Duração zero: o evento anterior não chegou a existir.
                contextDao.delete(current.id)
                timeline.invalidateSource(TimelineSourceType.CONTEXT, current.id)
            } else {
                contextDao.closeOpen(start)
            }
        }
        val draft = ContextEventEntity(type = to, startedAt = start, endedAt = null, confidence = confidence, placeId = placeId, source = source)
        val event = draft.copy(id = contextDao.insert(draft))
        timeline.recordContext(event.id, to.emoji, note ?: describe(to, placeId, current), start)
        log.log(DebugEventLogger.Category.CONTEXT, "${current?.type ?: "∅"} → $to ($reason, ${source.name}, ${(confidence * 100).toInt()}%)")
        TransitionResult(event, current, changed = true)
    }

    /**
     * Mesmo contexto: preserva o evento. Se só agora soubermos o lugar (rotina →
     * geofence, por exemplo), o evento é enriquecido — o tipo não muda.
     */
    private suspend fun keep(current: ContextEventEntity?, to: UserContextType, placeId: Long?, confidence: Float, source: ContextSource): TransitionResult? {
        if (current == null || current.type != to) return null
        return when {
            placeId == null || placeId == current.placeId -> TransitionResult(current, current, changed = false)
            current.placeId == null -> {
                val enriched = current.copy(placeId = placeId, confidence = maxOf(current.confidence, confidence), source = source)
                contextDao.update(enriched)
                TransitionResult(enriched, current, changed = false)
            }
            else -> null
        }
    }

    /** Usuário confirmou o contexto atual: só a confiança/origem mudam. */
    suspend fun confirm(eventId: Long, confidence: Float, source: ContextSource): ContextEventEntity? {
        val event = contextDao.getById(eventId) ?: return null
        val confirmed = event.copy(confidence = confidence, source = source)
        contextDao.update(confirmed)
        return confirmed
    }

    /** O lugar do evento foi salvo depois (pergunta "salvar este lugar?"). */
    suspend fun attachPlace(eventId: Long, placeId: Long) {
        val event = contextDao.getById(eventId)?.takeIf { it.endedAt == null } ?: return
        contextDao.update(event.copy(placeId = placeId))
    }

    /**
     * Correção histórica: a "saída" era oscilação de GPS. Remove o deslocamento,
     * reabre o contexto anterior e apaga da timeline o que foi contado — tudo junto.
     */
    suspend fun revertFlap(flap: ContextEventEntity, previous: ContextEventEntity): ContextEventEntity = tx.run {
        contextDao.delete(flap.id)
        val reopened = previous.copy(endedAt = null)
        contextDao.update(reopened)
        timeline.invalidateSource(TimelineSourceType.CONTEXT, flap.id)
        log.log(DebugEventLogger.Category.CONTEXT, "FLAP revertido: ${flap.type} removido, ${previous.type} reaberto")
        reopened
    }

    private suspend fun describe(type: UserContextType, placeId: Long?, previous: ContextEventEntity?): String = when (type) {
        UserContextType.COMMUTING -> {
            val from = previous?.placeId?.let { places.byId(it)?.name } ?: previous?.type?.label
            if (from != null) "Saiu de: $from" else "Deslocamento"
        }
        // Lugar "Outro" já cadastrado (médico, pet shop…) mostra o nome; sem lugar, é novo mesmo.
        UserContextType.UNKNOWN -> placeId?.let { places.byId(it)?.name } ?: "Lugar novo"
        else -> placeId?.let { places.byId(it)?.name }?.takeIf { it != type.label }?.let { "${type.label} · $it" } ?: type.label
    }
}
