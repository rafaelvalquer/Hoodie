package com.hoodie.app.engine.notification

import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.ContextQuestionEntity
import com.hoodie.app.core.database.DeliveryStatus
import com.hoodie.app.core.database.NotificationDeliveryDao
import com.hoodie.app.core.database.NotificationDeliveryEntity
import com.hoodie.app.core.database.QuestionDao
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.notification.HoodieNotifier
import com.hoodie.app.core.notification.Notifier
import com.hoodie.app.core.notification.NotificationGate
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.startOfDay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executa a decisão da [NotificationDeliveryPolicy]: mostra, adia (fila persistente), deixa só no app ou expira.
 * Nunca apaga a pergunta: expirar/encerrar a entrega só afeta o alerta do sistema.
 */
@Singleton
class NotificationDeliveryCoordinator(
    private val deliveries: NotificationDeliveryDao,
    private val questions: QuestionDao,
    private val contextDao: ContextEventDao,
    private val settings: SettingsRepository,
    private val signals: DeliverySignalSource,
    private val delegate: Notifier,
    private val gate: NotificationGate,
    private val clock: ClockProvider,
) {
    @Inject constructor(
        deliveries: NotificationDeliveryDao, questions: QuestionDao, contextDao: ContextEventDao, settings: SettingsRepository,
        signals: DeliverySignalSource, delegate: HoodieNotifier, clock: ClockProvider,
    ) : this(deliveries, questions, contextDao, settings, signals, delegate, delegate, clock)

    /** Registra a entrega (idempotente por pergunta) e já avalia. Perguntas duplicadas não geram alerta duplicado. */
    suspend fun submitQuestion(questionId: Long, text: String, yesNo: Boolean) {
        val now = clock.nowMillis()
        val row = NotificationDeliveryEntity(
            questionId = questionId, kind = if (yesNo) KIND_YES_NO else KIND_IN_APP, text = text, createdAt = now, nextEligibleAt = now,
            expiresAt = now + NotificationDeliveryPolicy.MAX_QUESTION_AGE_MS, deliveredAt = null, status = DeliveryStatus.PENDING,
            attemptCount = 0, dedupeKey = "question:$questionId",
        )
        if (deliveries.insertIgnore(row) == -1L) return
        deliveries.byDedupeKey(row.dedupeKey)?.let { process(it, allowShow = true) }
    }

    /** A pergunta foi respondida ou dispensada: encerra a entrega pendente. */
    suspend fun cancelForQuestion(questionId: Long) {
        deliveries.cancelForQuestion(questionId)
    }

    /**
     * Reavalia o que ficou pendente/adiado (chamado pelo worker de reconciliação e após reinício).
     * No máximo [maxShow] alertas por rodada, para não despejar tudo de uma vez.
     */
    suspend fun reevaluateDue(maxShow: Int = 1): Int {
        var shown = 0
        deliveries.due(clock.nowMillis()).forEach { row ->
            if (process(row, allowShow = shown < maxShow) == DeliveryDecision.SHOW_NOW) shown++
        }
        return shown
    }

    private suspend fun process(row: NotificationDeliveryEntity, allowShow: Boolean): DeliveryDecision {
        val now = clock.nowMillis()
        val question = row.questionId?.let { questions.getById(it) }
        val s = settings.current()
        val state = signals.signals()
        val input = NotificationDecisionInput(
            mode = s.notificationMode,
            notificationsAllowed = gate.canNotify(),
            isVehicleMoving = state.vehicleMoving,
            dayState = state.dayState?.state,
            isProvisionalDayState = state.dayState?.provisional == true,
            questionAgeMs = now - (question?.askedAt ?: row.createdAt),
            deliveredToday = deliveries.deliveredSince(startOfDay(clock.today(), clock.zone())),
            isStillRelevant = question != null && isRelevant(question),
        )
        var decision = NotificationDeliveryPolicy.decide(input)
        // Limite de uma entrega por rodada: o resto espera a próxima.
        if (decision == DeliveryDecision.SHOW_NOW && !allowShow) decision = DeliveryDecision.DEFER
        when (decision) {
            DeliveryDecision.SHOW_NOW -> {
                if (row.kind == KIND_YES_NO) delegate.askYesNo(row.questionId!!, row.text) else delegate.askInApp(row.questionId!!, row.text)
                deliveries.update(row.id, DeliveryStatus.DELIVERED, now, 1, row.nextEligibleAt)
            }
            DeliveryDecision.DEFER -> deliveries.update(row.id, DeliveryStatus.DEFERRED, null, 1, now + NotificationDeliveryPolicy.retryDelayMs(input))
            DeliveryDecision.IN_APP_ONLY -> deliveries.update(row.id, DeliveryStatus.IN_APP_ONLY, null, 0, row.nextEligibleAt)
            DeliveryDecision.EXPIRE -> {
                deliveries.update(row.id, DeliveryStatus.EXPIRED, null, 0, row.nextEligibleAt)
                row.questionId?.let { delegate.cancelQuestion(it) }
            }
        }
        return decision
    }

    /** Respondida = irrelevante; perguntas sobre o contexto atual só valem enquanto esse evento for o atual. */
    private suspend fun isRelevant(q: ContextQuestionEntity): Boolean {
        if (q.answeredAt != null) return false
        val eventId = q.contextEventId ?: return true
        return q.kind.isMobility || contextDao.current()?.id == eventId
    }

    companion object {
        const val KIND_YES_NO = "QUESTION_YES_NO"
        const val KIND_IN_APP = "QUESTION_IN_APP"
    }
}