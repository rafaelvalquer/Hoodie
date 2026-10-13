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
    suspend fun dismissPendingContextConfirmations() = with(processor) {
        val now = clock.nowMillis()
        questionDao.since(0L).filter { it.kind == QuestionKind.CONFIRM_CONTEXT && it.answeredAt == null }.forEach { question ->
            questionDao.update(question.copy(answeredAt = now, answer = "PASSIVE_POLICY"))
            runCatching { notifier.cancelQuestion(question.id) }
        }
    }

    suspend fun confirmCurrent(expectedEventId: Long, confirmedAt: Long): Boolean = with(processor) {
        val current = contextDao.current() ?: return@with false
        if (current.id != expectedEventId || current.endedAt != null) return@with false
        if (current.source in setOf(ContextSource.MANUAL, ContextSource.CONFIRMATION, ContextSource.USER_CORRECTION)) return@with true
        val confirmed = transitions.confirm(expectedEventId, 1f, ContextSource.CONFIRMATION) ?: return@with false
        recordConfirmation(confirmed.type, confirmed.placeId, confirmedAt, accepted = true)
        memory.onContext(confirmed.type, null, confirmedAt)
        hoodie.resolve()
        true
    }

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
        val now = clock.nowMillis()
        val classified = transitions.atomic {
            val q = questionDao.getById(questionId) ?: return@atomic null
            if (q.answeredAt != null) return@atomic null
            require(q.kind == QuestionKind.NEW_PLACE)
            val ctx = inferredContextFor(type, q.askedAt)
            q.contextEventId?.let { transitions.classifyDiscoveredVisit(it, ctx, type) }
            questionDao.update(q.copy(answeredAt = now, answer = type.name, chosenPlaceType = type))
            // Also offer manual location when the original observation had no position.
            questionDao.insert(
                ContextQuestionEntity(
                    kind = QuestionKind.SAVE_PLACE, candidate = ctx, placeId = null,
                    encryptedCoordinates = q.encryptedCoordinates, chosenPlaceType = type,
                    contextEventId = q.contextEventId, askedAt = now,
                ),
            )
            ctx to q.askedAt
        }
        classified ?: return@with
        notifier.cancelQuestion(questionId)
        recordConfirmation(classified.first, null, classified.second, accepted = true)
        memory.unlock(Milestone.FIRST_NEW_PLACE)
        memory.onContext(classified.first, null, now)
        hoodie.resolve()
    }

    suspend fun dismissQuestion(questionId: Long): Unit = with(processor) {
        val q = questionDao.getById(questionId) ?: return@with
        questionDao.update(q.copy(answeredAt = clock.nowMillis(), answer = "DISMISSED"))
        notifier.cancelQuestion(questionId)
    }
}
