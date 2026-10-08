package com.hoodie.app.engine.correction

import com.hoodie.app.core.database.*
import com.hoodie.app.core.model.*
import com.hoodie.app.core.mobility.MobilitySource
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.domain.correction.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryCorrectionService @Inject constructor(
    private val db: HoodieDatabase,
    private val transactions: TransactionRunner,
    private val phone: PhoneContextRecalculator,
    private val clock: ClockProvider,
    private val learning: dagger.Lazy<com.hoodie.app.engine.routine.RoutineLearningCoordinator>? = null,
    private val transportLearning: dagger.Lazy<com.hoodie.app.engine.mobility.TransportPatternLearner>? = null,
    private val notifier: com.hoodie.app.core.notification.Notifier? = null,
) {
    suspend fun save(request: DiaryCorrection): Long {
        val cancelledQuestions = mutableSetOf<Long>()
        val result = transactions.run {
            require(request.startedAt <= clock.nowMillis()) { "O início não pode estar no futuro." }
            require(request.endedAt == null || request.endedAt > request.startedAt) { "O fim precisa ser posterior ao início." }
            require(request.endedAt == null || request.endedAt <= clock.nowMillis()) { "O fim não pode estar no futuro." }
            request.placeId?.let { require(db.placeDao().getById(it) != null) { "Local não encontrado." } }
            val audit = when (request.targetType) {
                CorrectionTargetType.CONTEXT -> correctContext(request, cancelledQuestions)
                CorrectionTargetType.MOBILITY_SEGMENT -> correctMovement(request, cancelledQuestions)
                CorrectionTargetType.CONTEXT_BOUNDARY -> error("Uma correção de limite depende do evento principal.")
            }
            if (com.hoodie.app.core.config.HoodieConfig.LEARNED_ROUTINE) learning?.get()?.recompute()
            audit
        }
        // Notifications are external effects: cancel only after the canonical transaction commits.
        cancelledQuestions.forEach { id -> runCatching { notifier?.cancelQuestion(id) } }
        return result
    }

    private suspend fun correctContext(r: DiaryCorrection, cancelledQuestions: MutableSet<Long>): Long {
        val dao = db.contextEventDao()
        val old = requireNotNull(dao.getById(r.targetId)) { "Evento não encontrado." }
        val type = requireNotNull(r.context) { "Selecione um contexto." }
        require(old.endedAt == null || r.endedAt != null) { "Um evento encerrado precisa de horário final." }
        val end = r.endedAt ?: clock.nowMillis()
        require(type == UserContextType.COMMUTING || r.startedAt < end || db.mobilitySessionDao().open() == null) {
            "O início precisa ser anterior a agora para corrigir o deslocamento em andamento."
        }
        val siblings = dao.overlapping(minOf(old.startedAt, r.startedAt), maxOf(old.endedAt ?: clock.nowMillis(), end))
            .filter { it.id != old.id }.sortedBy { it.startedAt }
        for (other in siblings) {
            val otherEnd = other.endedAt ?: clock.nowMillis()
            if (otherEnd <= r.startedAt || other.startedAt >= end) continue
            require(other.source != ContextSource.USER_CORRECTION) { "O intervalo sobrepõe outro evento corrigido." }
            val adjusted = when {
                other.startedAt < r.startedAt && otherEnd <= end -> other.copy(endedAt = r.startedAt)
                other.startedAt >= r.startedAt && otherEnd > end -> other.copy(startedAt = end)
                else -> error("O intervalo apagaria outro evento. Ajuste os horários.")
            }
            auditContext(other, adjusted, CorrectionTargetType.CONTEXT_BOUNDARY)
            dao.update(adjusted)
            rewriteTimeline(adjusted)
        }
        if (r.endedAt == null) require(dao.current()?.id == old.id) { "Somente o evento atual pode permanecer aberto." }
        val corrected = old.copy(type = type, placeId = r.placeId, startedAt = r.startedAt, endedAt = r.endedAt, confidence = 1f, source = ContextSource.USER_CORRECTION)
        val audit = auditContext(old, corrected, CorrectionTargetType.CONTEXT)
        dao.update(corrected)
        rewriteTimeline(corrected)
        supersedeQuestions(cancelledQuestions) { it.contextEventId == corrected.id }
        if (old.type == UserContextType.COMMUTING && type != UserContextType.COMMUTING) {
            val trips = db.mobilitySessionDao().overlapping(corrected.startedAt, end)
            val segments = if (trips.isEmpty()) emptyList() else db.mobilitySegmentDao().forSessions(trips.map { it.id })
                .filter { it.startedAt < end && (it.endedAt ?: clock.nowMillis()) > corrected.startedAt }
            for (segment in segments) {
                require(segment.startedAt >= corrected.startedAt && (segment.endedAt ?: clock.nowMillis()) <= end) {
                    "O intervalo corta um trecho de viagem. Ajuste o trecho antes de corrigir o contexto."
                }
                correctMovement(r.copy(targetType = CorrectionTargetType.MOBILITY_SEGMENT, targetId = segment.id,
                    startedAt = segment.startedAt, endedAt = segment.endedAt, mode = com.hoodie.app.core.mobility.MovementMode.NONE), cancelledQuestions)
            }
        }
        if (type == UserContextType.COMMUTING && r.mode != null) {
            val trips = db.mobilitySessionDao().overlapping(corrected.startedAt, end)
            val segments = if (trips.isEmpty()) emptyList() else db.mobilitySegmentDao().forSessions(trips.map { it.id })
                .filter { it.startedAt < end && (it.endedAt ?: clock.nowMillis()) > corrected.startedAt }
            require(segments.size <= 1) { "A viagem tem vários trechos. Edite cada trecho para preservar a caminhada e o transporte." }
            if (segments.isEmpty()) {
                val draft = MobilitySessionEntity(startedAt = corrected.startedAt, endedAt = corrected.endedAt, destinationPlaceId = corrected.placeId,
                    initialMode = r.mode, currentMode = r.mode, state = if (corrected.endedAt == null) com.hoodie.app.engine.mobility.MobilityStateMachine.stateFor(r.mode) else com.hoodie.app.core.mobility.MobilityState.ARRIVED,
                    confidence = 1f, confirmed = true, source = MobilitySource.USER_CORRECTION, leftOrigin = true)
                val sessionId = db.mobilitySessionDao().insert(draft)
                db.mobilitySegmentDao().insert(MobilitySegmentEntity(sessionId = sessionId, mode = r.mode, startedAt = corrected.startedAt, endedAt = corrected.endedAt, confidence = 1f, confirmed = true, source = MobilitySource.USER_CORRECTION))
            } else correctMovement(r.copy(targetType = CorrectionTargetType.MOBILITY_SEGMENT, targetId = segments.single().id), cancelledQuestions)
        }
        phone.recalculate(minOf(old.startedAt, corrected.startedAt), maxOf(old.endedAt ?: clock.nowMillis(), corrected.endedAt ?: clock.nowMillis()))
        return audit
    }

    private suspend fun correctMovement(r: DiaryCorrection, cancelledQuestions: MutableSet<Long>): Long {
        val dao = db.mobilitySegmentDao()
        val old = requireNotNull(dao.getById(r.targetId)) { "Trecho não encontrado." }
        val session = requireNotNull(db.mobilitySessionDao().getById(old.sessionId))
        val mode = requireNotNull(r.mode) { "Selecione o transporte." }
        require(r.startedAt >= session.startedAt && (session.endedAt == null || (r.endedAt ?: clock.nowMillis()) <= session.endedAt)) { "O trecho precisa permanecer dentro da viagem." }
        require(old.endedAt == null || r.endedAt != null)
        val end = r.endedAt ?: clock.nowMillis()
        require(dao.forSession(old.sessionId).filter { it.id != old.id }.none {
            it.startedAt < end && (it.endedAt ?: clock.nowMillis()) > r.startedAt
        }) { "Os horários sobrepõem outro trecho da viagem." }
        val corrected = old.copy(mode = mode, startedAt = r.startedAt,
            endedAt = if (mode == com.hoodie.app.core.mobility.MovementMode.NONE) r.endedAt ?: clock.nowMillis() else r.endedAt,
            confidence = 1f, confirmed = true, source = MobilitySource.USER_CORRECTION)
        val id = db.intelligenceDao().insertCorrection(DiaryCorrectionEntity(
            targetType = CorrectionTargetType.MOBILITY_SEGMENT.name, targetId = old.id,
            originalContext = null, correctedContext = null, originalPlaceId = session.destinationPlaceId, correctedPlaceId = r.placeId,
            originalStartAt = old.startedAt, correctedStartAt = corrected.startedAt, originalEndAt = old.endedAt, correctedEndAt = corrected.endedAt,
            originalMode = old.mode.name, correctedMode = mode.name, originalSource = old.source.name, originalConfidence = old.confidence, createdAt = clock.nowMillis(),
        ))
        dao.update(corrected)
        supersedeQuestions(cancelledQuestions) { it.mobilitySessionId == old.sessionId && it.kind == QuestionKind.SELECT_TRANSPORT_MODE }
        val all = dao.forSession(old.sessionId)
        val hasMovement = all.any { it.mode != com.hoodie.app.core.mobility.MovementMode.NONE }
        db.mobilitySessionDao().update(session.copy(initialMode = all.first().mode, currentMode = all.last().mode, destinationPlaceId = r.placeId,
            confidence = all.minOf { it.confidence }, confirmed = hasMovement && all.all { it.confirmed },
            endedAt = if (hasMovement) all.last().endedAt else session.endedAt ?: clock.nowMillis(),
            state = when {
                !hasMovement -> com.hoodie.app.core.mobility.MobilityState.STATIONARY
                all.last().endedAt != null -> com.hoodie.app.core.mobility.MobilityState.ARRIVED
                else -> session.state
            },
            source = if (all.last().source == MobilitySource.USER_CORRECTION) MobilitySource.USER_CORRECTION else session.source))
        if (com.hoodie.app.core.config.HoodieConfig.TRANSPORT_CLASSIFIER_V2 &&
            (old.source != MobilitySource.USER_CORRECTION || old.mode != mode || session.destinationPlaceId != r.placeId)) {
            val patterns = transportLearning?.get()
            if (session.destinationPlaceId != r.placeId) {
                patterns?.record(session, com.hoodie.app.core.mobility.MovementMode.NONE, old.mode)
                patterns?.record(session.copy(destinationPlaceId = r.placeId), mode)
            } else patterns?.record(session, mode, old.mode.takeIf { it != mode })
        }
        return id
    }

    private suspend fun auditContext(old: ContextEventEntity, corrected: ContextEventEntity, target: CorrectionTargetType) = db.intelligenceDao().insertCorrection(DiaryCorrectionEntity(
        targetType = target.name, targetId = old.id, originalContext = old.type.name, correctedContext = corrected.type.name,
        originalPlaceId = old.placeId, correctedPlaceId = corrected.placeId, originalStartAt = old.startedAt, correctedStartAt = corrected.startedAt,
        originalEndAt = old.endedAt, correctedEndAt = corrected.endedAt, originalMode = null, correctedMode = null,
        originalSource = old.source.name, originalConfidence = old.confidence, createdAt = clock.nowMillis(),
    ))

    private suspend fun rewriteTimeline(event: ContextEventEntity) {
        val dao = db.timelineDao()
        dao.deleteBySource(TimelineSourceType.CONTEXT, event.id)
        val place = event.placeId?.let { db.placeDao().getById(it)?.name }
        dao.insert(TimelineEventEntity(timestamp = event.startedAt, actor = TimelineActor.USER, emoji = event.type.emoji,
            text = event.type.label + (place?.let { " · $it" } ?: ""), sourceType = TimelineSourceType.CONTEXT, sourceId = event.id))
    }

    private suspend fun supersedeQuestions(cancelledQuestions: MutableSet<Long>, matches: (ContextQuestionEntity) -> Boolean) {
        val now = clock.nowMillis()
        db.questionDao().since(0).filter { it.answeredAt == null && matches(it) }.forEach {
            db.questionDao().update(it.copy(answeredAt = now, answer = "CORRECTED"))
            cancelledQuestions += it.id
        }
    }
}
