package com.hoodie.app.engine.mobility

import com.hoodie.app.core.database.QuestionDao
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.engine.context.ContextEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uma porta para responder perguntas (botões da notificação e cartão da Home):
 * perguntas de mobilidade vão para o [MobilityEngine]; o resto, para o [ContextEngine].
 * Assim o ContextEngine não depende da mobilidade.
 */
@Singleton
class QuestionRouter @Inject constructor(
    private val questions: QuestionDao,
    private val contextEngine: ContextEngine,
    private val mobilityEngine: MobilityEngine,
) {
    suspend fun answerYesNo(questionId: Long, yes: Boolean) {
        val q = questions.getById(questionId) ?: return
        if (q.kind.isMobility) mobilityEngine.answerYesNo(questionId, yes) else contextEngine.answerYesNo(questionId, yes)
    }

    suspend fun answerTransportMode(questionId: Long, mode: MovementMode) = mobilityEngine.answerTransportMode(questionId, mode)
}
