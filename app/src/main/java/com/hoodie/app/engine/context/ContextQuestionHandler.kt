package com.hoodie.app.engine.context

import com.hoodie.app.core.database.ContextQuestionEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.memory.Milestone
import com.hoodie.app.core.config.HoodieConfig

/** Internal handler; synchronization belongs exclusively to ContextEngine. */
internal class ContextQuestionHandler(private val processor: ContextSignalProcessor) {
    suspend fun answerYesNo(questionId: Long, yes: Boolean): Unit = with(processor) {
        val q = questionDao.getById(questionId) ?: return@with
        if (q.answeredAt != null) return@with
        // Perguntas de mobilidade são do MobilityEngine (ver QuestionRouter).
        if (q.kind.isMobility) return@with
        val now = clock.nowMillis()
        questionDao.update(q.copy(answeredAt = now, answer = if (yes) "YES" else "NO"))
        notifier.cancelQuestion(questionId)
        val candidate = q.candidate ?: return@with
        val event = q.contextEventId?.let { contextDao.getById(it) }
        if (event?.source == ContextSource.USER_CORRECTION) { hoodie.resolve(); return@with }
        recordConfirmation(candidate, q.placeId, q.askedAt, accepted = yes)
        when {
            yes -> {
                if (HoodieConfig.UNIFIED_CONFIDENCE_ENGINE && event != null && event.type != candidate) {
                    if (event.endedAt == null) switchTo(candidate, now, 1f, q.placeId, ContextSource.CONFIRMATION, TransitionReason.NEW_PLACE_ANSWER)
                } else event?.let { transitions.confirm(it.id, 1f, ContextSource.CONFIRMATION) }
                memory.onContext(candidate, null, now)
                hoodie.resolve()
            }
            event != null && event.endedAt == null -> {
                // "Não": boundary agora. O que já passou continua registrado como foi inferido.
                val fixed = if (candidate == UserContextType.WORK) UserContextType.LEISURE else UserContextType.UNKNOWN
                switchTo(fixed, now, 1f, event.placeId, ContextSource.CONFIRMATION, TransitionReason.CONFIRMATION_REJECTED, note = "${fixed.label} (corrigido)")
            }
            else -> hoodie.resolve()
        }
    }

    suspend fun answerNewPlace(questionId: Long, type: PlaceType): Unit = with(processor) {
        val q = questionDao.getById(questionId) ?: return@with
        if (q.answeredAt != null) return@with
        val now = clock.nowMillis()
        questionDao.update(q.copy(answeredAt = now, answer = type.name, chosenPlaceType = type))
        notifier.cancelQuestion(questionId)
        val ctx = inferredContextFor(type, now)
        recordConfirmation(ctx, null, now, accepted = true)
        memory.unlock(Milestone.FIRST_NEW_PLACE)
        val asked = q.contextEventId?.let { contextDao.getById(it) }
        val eventId = if (asked != null && asked.endedAt == null) {
            switchTo(ctx, now, 1f, null, ContextSource.CONFIRMATION, TransitionReason.NEW_PLACE_ANSWER).event.id
        } else {
            // A pessoa já saiu de lá: aprende a resposta, mas não muda o presente.
            memory.onContext(ctx, null, now)
            hoodie.resolve()
            q.contextEventId
        }
        // Segunda etapa da mesma conversa: salvar o lugar? (não conta no limite diário)
        if (q.encryptedCoordinates != null) {
            questionDao.insert(
                ContextQuestionEntity(
                    kind = QuestionKind.SAVE_PLACE, candidate = ctx, placeId = null,
                    encryptedCoordinates = q.encryptedCoordinates, chosenPlaceType = type,
                    contextEventId = eventId, askedAt = now,
                ),
            )
        }
    }

    suspend fun dismissQuestion(questionId: Long): Unit = with(processor) {
        val q = questionDao.getById(questionId) ?: return@with
        questionDao.update(q.copy(answeredAt = clock.nowMillis(), answer = "DISMISSED"))
        notifier.cancelQuestion(questionId)
    }
}
