package com.hoodie.app.engine.mobility

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.ContextQuestionEntity
import com.hoodie.app.core.database.MobilitySegmentEntity
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.database.QuestionDao
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.location.LocationSource
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.mobility.ActivityRecognitionPermissions
import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.mobility.MobilitySource
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.mobility.MovementObservation
import com.hoodie.app.core.model.ContextQuestion
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.notification.Notifier
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.core.time.atZone
import com.hoodie.app.core.time.minuteOfDay
import com.hoodie.app.data.repository.MobilityRepository
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.data.repository.RoutineRepository
import com.hoodie.app.engine.context.AskedQuestion
import com.hoodie.app.engine.context.ContextEngine
import com.hoodie.app.engine.context.GeofenceTransition
import com.hoodie.app.engine.routine.RoutineEngine
import com.hoodie.app.worker.MobilityScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Mobility Engine: "a pessoa está se deslocando? como? de onde? para onde?".
 *
 *     Activity Recognition ─┐
 *                           ├─▶ MobilityEngine ─▶ ContextEngine (beginCommute / arriveAt)
 *     Geofence ENTER/EXIT ──┘          │
 *                                      ├─▶ mobility_sessions / mobility_segments
 *                                      └─▶ MobilityEventBus (Hoodie, Diário, futuro Equilíbrio)
 *
 * Não substitui nada: sem permissão ou com a detecção desligada, nenhum método faz
 * nada e o Hoodie segue só com geofences. Não liga GPS contínuo: a única leitura de
 * posição é pontual, para resolver uma chegada sem geofence.
 *
 * Todo o estado fica no banco (sessão aberta + trecho aberto), porque cada evento do
 * sistema pode chegar num processo novo.
 */
@Singleton
class MobilityEngine @Inject constructor(
    private val repo: MobilityRepository,
    private val contextEngine: ContextEngine,
    private val contextDao: ContextEventDao,
    private val places: PlaceRepository,
    private val routines: RoutineRepository,
    private val questions: QuestionDao,
    private val notifier: Notifier,
    private val settings: SettingsRepository,
    private val location: LocationSource,
    private val scheduler: MobilityScheduler,
    private val permissions: ActivityRecognitionPermissions,
    private val bus: MobilityEventBus,
    private val clock: ClockProvider,
    private val log: DebugEventLogger,
    private val transportFeatures: TransportFeatureBuilder? = null,
    private val transportPatterns: TransportPatternLearner? = null,
    private val intelligence: com.hoodie.app.core.database.IntelligenceDao? = null,
) {
    private val mutex = Mutex()
    private var lastClassification: TransportClassification? = null
    private val pendingVehicleSwitches = mutableMapOf<Long, TransportSwitchCandidate>()

    /** O app está na tela: perguntas podem aparecer mesmo com o veículo andando (passageiro olhando). */
    @Volatile var appInForeground: Boolean = false

    private suspend fun enabled(): Boolean = settings.current().mobility.detectionEnabled && permissions.state().granted

    // ───────────────────────── Entradas ─────────────────────────

    /** Transição do Activity Recognition (vinda do ActivityTransitionReceiver). */
    suspend fun onMovement(obs: MovementObservation): Unit = mutex.withLock {
        if (!enabled()) return@withLock
        log.log(DebugEventLogger.Category.MOBILITY, "MOV ${obs.activity}${if (obs.entering) "" else " (fim)"}")
        var s = housekeeping(repo.open(), obs.timestamp)
        if (s != null && obs.timestamp < maxOf(s.startedAt, s.movingSince ?: s.startedAt, s.stillSince ?: s.startedAt,
                s.lastObservationAt ?: s.startedAt, repo.openSegment(s.id)?.startedAt ?: s.startedAt)) return@withLock
        if (s == null && (repo.lastFinished()?.endedAt ?: Long.MIN_VALUE) > obs.timestamp) return@withLock
        if (s != null) {
            s = s.copy(
                lastObservedMovement = obs.activity,
                lastObservationAt = obs.timestamp,
                lastVehicleAt = if (obs.activity == DetectedMovement.IN_VEHICLE && obs.entering) obs.timestamp else s.lastVehicleAt,
                vehicleExitAt = if (obs.activity == DetectedMovement.IN_VEHICLE && !obs.entering) obs.timestamp else s.vehicleExitAt,
                pendingMovementMode = if (obs.activity == DetectedMovement.IN_VEHICLE && obs.entering) null else s.pendingMovementMode,
                pendingMovementAt = if (obs.activity == DetectedMovement.IN_VEHICLE && obs.entering) null else s.pendingMovementAt,
            )
            repo.update(s)
        }
        if (HoodieConfig.TRANSPORT_CLASSIFIER_V2 && s != null) {
            transportFeatures?.begin(s.id, s.startedAt)
            transportFeatures?.restorePersisted(s.id, s.startedAt, s.speedSampleCount, s.meanSpeedKmh, s.maxSpeedKmh, s.speedVariation)
            if (obs.entering) transportFeatures?.movement(obs.activity, obs.timestamp)
        }

        if (!obs.entering) {
            // Saiu do veículo: parado o bastante para a pergunta adiada aparecer.
            if (obs.activity == DetectedMovement.IN_VEHICLE && s?.pendingModeQuestion == true) askTransport(s, vehicleMoving = false)
            return@withLock
        }
        if (obs.activity == DetectedMovement.STILL) {
            s ?: return@withLock
            when {
                s.state == MobilityState.MOVEMENT_CANDIDATE && !s.leftOrigin -> discard(s, "parou antes de sair") // andou pela casa e parou
                else -> {
                    s = s.copy(stillSince = s.stillSince ?: obs.timestamp, movingSince = null)
                    repo.update(s)
                    if (s.pendingModeQuestion) askTransport(s, vehicleMoving = false)
                    if (s.state != MobilityState.MOVEMENT_CANDIDATE) scheduler.scheduleMobilityCheck(HoodieConfig.STILL_ARRIVAL_MS)
                }
            }
            return@withLock
        }
        if (!obs.activity.isMoving) return@withLock

        val mode = obs.activity.toMode()
        when {
            s == null -> {
                s = newCandidate(mode, obs.timestamp, leftOrigin = false, originPlaceId = null).copy(
                    lastObservedMovement = obs.activity, lastObservationAt = obs.timestamp,
                    lastVehicleAt = obs.timestamp.takeIf { obs.activity == DetectedMovement.IN_VEHICLE },
                )
                repo.update(s)
                if (obs.activity == DetectedMovement.IN_VEHICLE) sampleVehicleSpeed(s, obs.timestamp)
                scheduler.scheduleMobilityCheck(MobilityStateMachine.sustainThreshold(mode))
                evaluate(s, obs.timestamp)
            }
            s.state == MobilityState.MOVEMENT_CANDIDATE -> {
                val updated = s.copy(
                    initialMode = if (s.initialMode == MovementMode.NONE) mode else s.initialMode,
                    // Veículo vence caminhada no candidato (o trecho a pé vira detalhe).
                    currentMode = if (s.currentMode.isVehicle && !mode.isVehicle) s.currentMode else mode,
                    movingSince = if (s.stillSince != null || s.movingSince == null) obs.timestamp else s.movingSince,
                    stillSince = null,
                )
                repo.update(updated)
                if (obs.activity == DetectedMovement.IN_VEHICLE) sampleVehicleSpeed(updated, obs.timestamp)
                scheduler.scheduleMobilityCheck(MobilityStateMachine.sustainThreshold(updated.currentMode))
                evaluate(updated, obs.timestamp)
            }
            else -> {
                var active = s.copy(stillSince = null, movingSince = s.movingSince ?: obs.timestamp)
                if (active.state == MobilityState.ARRIVING) active = active.copy(state = MobilityStateMachine.stateFor(active.currentMode))
                if (active.currentMode.isVehicle && !mode.isVehicle && mode in setOf(MovementMode.WALKING, MovementMode.RUNNING)) {
                    val confirmed = MobilityModeHysteresisPolicy.confirmVehicleToWalking(
                        active.currentMode, mode, active.pendingMovementMode, active.pendingMovementAt, obs.timestamp,
                    )
                    if (!confirmed) {
                        active = active.copy(
                            pendingMovementMode = mode,
                            pendingMovementAt = if (active.pendingMovementMode == mode) active.pendingMovementAt ?: obs.timestamp else obs.timestamp,
                        )
                        repo.update(active)
                        scheduler.scheduleMobilityCheck(MobilityModeHysteresisPolicy.WALKING_CONFIRMATION_MS)
                        return@withLock
                    }
                }
                repo.update(active)
                if (MobilityStateMachine.isNewSegment(active.currentMode, mode)) newSegment(active, mode, obs.timestamp)
                else if (mode.isVehicle) {
                    sampleVehicleSpeed(active, obs.timestamp)
                    refreshVehicleClassification(active, obs.timestamp)
                }
            }
        }
    }

    /** Geofence (depois do ContextEngine já ter processado o mesmo evento). */
    suspend fun onGeofence(placeId: Long, transition: GeofenceTransition, at: Long): Unit = mutex.withLock {
        if (!enabled()) return@withLock
        val s = housekeeping(repo.open(), at)
        when (transition) {
            GeofenceTransition.EXIT -> when {
                // Saiu antes do sensor perceber o movimento: candidato esperando o movimento.
                s == null -> newCandidate(MovementMode.NONE, at, leftOrigin = true, originPlaceId = placeId)
                s.state == MobilityState.MOVEMENT_CANDIDATE -> {
                    val updated = s.copy(leftOrigin = true, originPlaceId = s.originPlaceId ?: placeId)
                    repo.update(updated)
                    evaluate(updated, at)
                }
                else -> Unit // saída de um lugar no meio do caminho
            }
            GeofenceTransition.ENTER, GeofenceTransition.DWELL -> {
                s ?: return@withLock
                val place = places.byId(placeId) ?: return@withLock
                arrive(s, place, at, byGeofence = true)
            }
        }
    }

    /** Checagem atrasada (WorkManager): movimento sustentado, chegada por parada, limpeza. */
    suspend fun onCheck(): Unit = mutex.withLock {
        if (!enabled()) return@withLock
        val now = clock.nowMillis()
        val s = housekeeping(repo.open(), now) ?: return@withLock
        if (s.currentMode.isVehicle && s.stillSince == null) sampleVehicleSpeed(s, now)
        if (s.currentMode.isVehicle && s.state != MobilityState.MOVEMENT_CANDIDATE && s.stillSince == null) refreshVehicleClassification(s, now)
        when {
            s.state == MobilityState.MOVEMENT_CANDIDATE -> evaluate(s, now)
            MobilityStateMachine.isArrivalStill(s.stillSince, now) -> resolveStillArrival(s, now)
            s.stillSince != null -> scheduler.scheduleMobilityCheck(s.stillSince + HoodieConfig.STILL_ARRIVAL_MS - now)
        }
        val latest = repo.open()
        if (latest?.currentMode?.isVehicle == true && latest.stillSince == null && latest.speedSampleAttempts < TransportSpeedSamplingPolicy.MAX_ATTEMPTS) {
            scheduler.scheduleMobilityCheck(TransportSpeedSamplingPolicy.INTERVAL_MS)
        }
        repo.open()?.takeIf { it.pendingModeQuestion && it.stillSince != null }?.let { askTransport(it, vehicleMoving = false) }
    }

    /** App aberto: pergunta adiada do transporte pode aparecer agora (dentro do app, sem exigir pressa). */
    suspend fun onAppOpened(): Unit = mutex.withLock {
        appInForeground = true
        if (!enabled()) return@withLock
        val s = repo.open() ?: return@withLock
        if (s.pendingModeQuestion) askTransport(s, vehicleMoving = s.stillSince == null)
    }

    fun onAppClosed() { appInForeground = false }

    // ───────────────────────── Respostas ─────────────────────────

    suspend fun answerYesNo(questionId: Long, yes: Boolean): Unit = mutex.withLock {
        val q = markAnswered(questionId, if (yes) "YES" else "NO") ?: return@withLock
        val s = q.mobilitySessionId?.let { repo.session(it) } ?: return@withLock
        val now = clock.nowMillis()
        when (q.kind) {
            QuestionKind.CONFIRM_MOVEMENT -> if (yes) {
                if (s.state == MobilityState.MOVEMENT_CANDIDATE) promote(s, now, MobilitySource.CONFIRMATION)
            } else {
                // "Não saí": o que parecia deslocamento era ruído (andar pela casa, carro de alguém).
                discard(s, "usuário disse que não saiu")
            }
            QuestionKind.CONFIRM_ARRIVAL -> answerArrival(s, yes, q.placeId, now)
            QuestionKind.CONFIRM_TRIP_PATTERN -> {
                val key = MobilityLearningEngine.patternKey(s.originPlaceId, s.destinationPlaceId)
                val m = settings.current().mobility
                settings.setMobility(if (yes) m.copy(approvedPatterns = m.approvedPatterns + key, declinedPatterns = m.declinedPatterns - key)
                else m.copy(declinedPatterns = m.declinedPatterns + key, approvedPatterns = m.approvedPatterns - key))
            }
            else -> Unit
        }
    }

    /** "Como está se deslocando?" — também vale depois da chegada ("você estava de carro?"). */
    suspend fun answerTransportMode(questionId: Long, mode: MovementMode): Unit = mutex.withLock {
        val q = markAnswered(questionId, mode.name) ?: return@withLock
        val s = q.mobilitySessionId?.let { repo.session(it) } ?: return@withLock
        val segment = repo.segmentsOf(s.id).lastOrNull { it.mode.isVehicle } ?: return@withLock
        if (segment.source == MobilitySource.USER_CORRECTION) return@withLock
        repo.updateSegment(segment.copy(mode = mode, confidence = 1f, confirmed = true, source = MobilitySource.CONFIRMATION))
        var updated = s.copy(pendingModeQuestion = false)
        if (s.endedAt == null && s.currentMode.isVehicle) {
            updated = updated.copy(currentMode = mode, state = MobilityStateMachine.stateFor(mode))
            bus.emit(MobilityEvent.MovementModeChanged(s.id, clock.nowMillis(), s.currentMode, mode))
        }
        repo.update(updated)
        if (HoodieConfig.TRANSPORT_CLASSIFIER_V2) transportPatterns?.record(updated, mode)
        log.log(DebugEventLogger.Category.CONTEXT, "Transporte escolhido: $mode")
    }

    /**
     * O usuário trocou o contexto à mão logo depois de uma chegada confirmada sozinha
     * ("não estou no trabalho"): a chegada vira correção e o padrão perde a confiança.
     */
    suspend fun onManualContext(type: UserContextType, at: Long): Unit = mutex.withLock {
        val last = repo.lastFinished() ?: return@withLock
        val ended = last.endedAt ?: return@withLock
        if (!last.arrivalAutoConfirmed || at - ended > HoodieConfig.ARRIVAL_CORRECTION_WINDOW_MS) return@withLock
        val destType = last.destinationPlaceId?.let { places.byId(it) }?.type?.toContext() ?: return@withLock
        if (destType == type) return@withLock
        repo.update(last.copy(arrivalConfirmed = false, arrivalAutoConfirmed = false))
        log.log(DebugEventLogger.Category.CONTEXT, "Chegada automática corrigida pelo usuário ($destType → $type)")
    }

    /** Settings › "Apagar histórico de deslocamentos". */
    suspend fun clearHistory(): Unit = mutex.withLock {
        repo.clearHistory()
        intelligence?.clearTransportPatterns()
        scheduler.cancelMobilityCheck()
    }

    // ───────────────────────── Núcleo ─────────────────────────

    /** Encerra o que ficou para trás antes de processar um evento novo. */
    private suspend fun housekeeping(s: MobilitySessionEntity?, now: Long): MobilitySessionEntity? {
        s ?: return null
        if (s.state == MobilityState.MOVEMENT_CANDIDATE && MobilityStateMachine.isExpiredCandidate(s.startedAt, now)) {
            discard(s, "candidato expirou"); return null
        }
        if (s.state != MobilityState.MOVEMENT_CANDIDATE && MobilityStateMachine.isStaleSession(s.startedAt, now)) {
            finish(s, destination = null, at = s.stillSince ?: now, reason = "sessão antiga sem chegada"); return null
        }
        return s
    }

    /** Explicit bounded point sample; position coordinates stay inside LocationProvider. */
    private suspend fun sampleVehicleSpeed(session: MobilitySessionEntity, now: Long) {
        if (!HoodieConfig.TRANSPORT_CLASSIFIER_V2 || transportFeatures == null) return
        val current = repo.session(session.id) ?: return
        if (current.endedAt != null || !current.currentMode.isVehicle || current.stillSince != null) return
        val permission = location.permissionState()
        if (current.speedSampleAttempts >= TransportSpeedSamplingPolicy.MAX_ATTEMPTS) return
        if (!permission.canReadPosition || (!appInForeground && permission != LocationPermissionState.BACKGROUND)) {
            log.log(DebugEventLogger.Category.MOBILITY, "Amostra de velocidade ignorada: permissão/localização indisponível")
            return
        }
        if (!TransportSpeedSamplingPolicy.canAttempt(current.speedSampleAttempts, current.lastSpeedSampleAt, now, permission, appInForeground)) return
        val attempted = current.copy(speedSampleAttempts = current.speedSampleAttempts + 1, lastSpeedSampleAt = now)
        repo.update(attempted)
        val sample = location.currentSpeed()
        val valid = sample?.takeIf {
            it.observedAt in current.startedAt..now && now - it.observedAt <= TransportSpeedSamplingPolicy.MAX_AGE_MS &&
                it.metersPerSecond.isFinite() && it.metersPerSecond in 0f..100f
        }
        if (valid == null) {
            log.log(DebugEventLogger.Category.MOBILITY, "Amostra de velocidade sem dado válido (${attempted.speedSampleAttempts}/${TransportSpeedSamplingPolicy.MAX_ATTEMPTS})")
            return
        }
        transportFeatures.begin(current.id, current.startedAt)
        transportFeatures.restorePersisted(current.id, current.startedAt, current.speedSampleCount, current.meanSpeedKmh, current.maxSpeedKmh, current.speedVariation)
        transportFeatures.speed(valid.metersPerSecond, valid.observedAt)
        val features = transportFeatures.build(now)
        repo.update(attempted.copy(
            speedSampleCount = current.speedSampleCount + 1,
            meanSpeedKmh = features.meanSpeedKmh,
            maxSpeedKmh = features.maxSpeedKmh,
            speedVariation = features.speedVariation,
        ))
        log.log(DebugEventLogger.Category.MOBILITY, "Amostra de velocidade válida (${current.speedSampleCount + 1}/${TransportSpeedSamplingPolicy.MAX_ATTEMPTS})")
    }

    private suspend fun newCandidate(mode: MovementMode, at: Long, leftOrigin: Boolean, originPlaceId: Long?): MobilitySessionEntity {
        // Origem: o lugar onde o contexto estava; se o ContextEngine já trocou para
        // deslocamento (a geofence chegou antes), o lugar anterior.
        var origin = originPlaceId
        var left = leftOrigin
        if (origin == null) {
            val current = contextDao.current()
            if (current?.type == UserContextType.COMMUTING) {
                origin = contextDao.previous()?.placeId
                left = left || current.placeId == null
            } else {
                origin = current?.placeId
            }
        }
        val candidate = repo.insert(
            MobilitySessionEntity(
                startedAt = at, originPlaceId = origin, initialMode = mode, currentMode = mode,
                state = MobilityState.MOVEMENT_CANDIDATE, confidence = 0f, source = MobilitySource.ACTIVITY_RECOGNITION,
                leftOrigin = left, movingSince = if (mode != MovementMode.NONE) at else null,
            ),
        )
        if (HoodieConfig.TRANSPORT_CLASSIFIER_V2) {
            transportFeatures?.begin(candidate.id, at)
            transportFeatures?.movement(if (mode.isVehicle) DetectedMovement.IN_VEHICLE else if (mode == MovementMode.WALKING) DetectedMovement.WALKING else DetectedMovement.UNKNOWN, at)
        }
        return candidate
    }

    /** Candidato: pontua a evidência e ignora, pergunta ou aplica. */
    private suspend fun evaluate(s: MobilitySessionEntity, now: Long) {
        val m = settings.current().mobility
        val trips = if (m.learnTrips) repo.trips(now) else emptyList()
        val zone = clock.zone()
        val history = m.learnTrips && MobilityLearningEngine.hasHistory(trips, s.originPlaceId, s.startedAt, zone)
        val evidence = MobilityEvidence(
            movement = s.currentMode != MovementMode.NONE,
            geofenceExit = s.leftOrigin,
            sustained = MobilityStateMachine.isSustained(s.currentMode, s.movingSince ?: s.startedAt, s.stillSince, now),
            timeMatch = timeMatchesRoutine(s.startedAt),
            history = history,
            insideKnownPlace = s.originPlaceId != null && !s.leftOrigin,
        )
        var decision = MobilityConfidenceScorer.decide(evidence)
        val approved = s.originPlaceId != null && m.approvedPatterns.any { it.startsWith("${s.originPlaceId}>") }
        // Primeiras vezes: mesmo com evidência forte, o Hoodie pergunta para aprender.
        val firstTimes = MobilityLearningEngine.confirmedFrom(trips, s.originPlaceId) < HoodieConfig.TRIP_PATTERN_MIN_COUNT && !approved
        if (!HoodieConfig.UNIFIED_CONFIDENCE_ENGINE && decision == MobilityDecision.APPLY && firstTimes && s.questionsAsked == 0) decision = MobilityDecision.ASK
        val withConfidence = s.copy(confidence = MobilityConfidenceScorer.confidence(evidence))
        repo.update(withConfidence)
        when (decision) {
            MobilityDecision.IGNORE -> Unit
            MobilityDecision.APPLY -> promote(withConfidence, now, if (history) MobilitySource.LEARNED else MobilitySource.ACTIVITY_RECOGNITION)
            MobilityDecision.ASK -> {
                if (withConfidence.questionsAsked > 0) {
                    // Já perguntado e sem resposta: evidência forte o bastante aplica sozinha.
                    if (MobilityConfidenceScorer.score(evidence) >= HoodieConfig.MOBILITY_APPLY_SCORE) promote(withConfidence, now, MobilitySource.ACTIVITY_RECOGNITION)
                    return
                }
                val asked = ask(
                    QuestionKind.CONFIRM_MOVEMENT, withConfidence, originContext(withConfidence), null,
                    vehicleMoving = withConfidence.currentMode.isVehicle && withConfidence.stillSince == null,
                )
                // Sem poder perguntar (limite do dia): só evidência forte vira deslocamento.
                if (asked == null && MobilityConfidenceScorer.score(evidence) >= HoodieConfig.MOBILITY_APPLY_SCORE) {
                    promote(withConfidence, now, MobilitySource.ACTIVITY_RECOGNITION)
                }
            }
        }
    }

    /** Candidato → deslocamento: primeiro trecho, COMMUTING no ContextEngine, evento. */
    private suspend fun promote(s: MobilitySessionEntity, now: Long, source: MobilitySource) {
        val classified = classify(s.currentMode, s.originPlaceId, s.startedAt)
        val mode = classified.first
        val promoted = s.copy(
            state = MobilityStateMachine.stateFor(mode).takeIf { it != MobilityState.MOVEMENT_CANDIDATE } ?: MobilityState.WALKING,
            initialMode = if (s.initialMode == MovementMode.NONE) mode else s.initialMode,
            currentMode = mode,
            confirmed = !HoodieConfig.UNIFIED_CONFIDENCE_ENGINE || source == MobilitySource.CONFIRMATION || s.confidence >= .85f,
            source = source,
            confidence = if (source == MobilitySource.CONFIRMATION) 1f else if (HoodieConfig.UNIFIED_CONFIDENCE_ENGINE) s.confidence else maxOf(s.confidence, 0.8f),
        )
        repo.update(promoted)
        val start = s.movingSince?.let { minOf(it, s.startedAt) } ?: s.startedAt
        repo.insertSegment(
            MobilitySegmentEntity(
                sessionId = s.id, mode = mode, startedAt = start, confidence = lastClassification?.confidence?.value ?: promoted.confidence,
                confirmed = if (HoodieConfig.TRANSPORT_CLASSIFIER_V2 && lastClassification != null) lastClassification!!.source == com.hoodie.app.domain.detection.DetectionSource.USER_CONFIRMATION || lastClassification!!.confidence.value >= .85f else source == MobilitySource.CONFIRMATION || classified.second != null,
                source = classified.second ?: source,
            ),
        )
        log.log(DebugEventLogger.Category.CONTEXT, "Deslocamento iniciado: $mode ($source)")
        contextEngine.beginCommute(s.originPlaceId, start, promoted.confidence)
        bus.emit(MobilityEvent.MobilityStarted(s.id, start, s.originPlaceId, mode))
        if (mode == MovementMode.VEHICLE_UNKNOWN) askTransport(promoted, vehicleMoving = true)
        scheduler.scheduleMobilityCheck(HoodieConfig.STILL_ARRIVAL_MS)
    }

    /** Troca de trecho: a pé ⇄ veículo. */
    private suspend fun newSegment(s: MobilitySessionEntity, observed: MovementMode, at: Long) {
        if (HoodieConfig.TRANSPORT_CLASSIFIER_V2 && observed.isVehicle && !s.currentMode.isVehicle) {
            transportFeatures?.begin(s.id, at, newVehicleSegment = true)
            transportFeatures?.movement(DetectedMovement.IN_VEHICLE, at)
        }
        repo.openSegment(s.id)?.let { repo.updateSegment(it.copy(endedAt = at)) }
        val (mode, learnedSource) = classify(observed, s.originPlaceId, s.startedAt)
        repo.insertSegment(
            MobilitySegmentEntity(
                sessionId = s.id, mode = mode, startedAt = at, confidence = lastClassification?.confidence?.value ?: s.confidence,
                confirmed = if (HoodieConfig.TRANSPORT_CLASSIFIER_V2 && lastClassification != null) lastClassification!!.confidence.value >= .85f else learnedSource != null,
                source = learnedSource ?: MobilitySource.ACTIVITY_RECOGNITION,
            ),
        )
        val updated = s.copy(currentMode = mode, state = MobilityStateMachine.stateFor(mode), pendingMovementMode = null, pendingMovementAt = null)
        repo.update(updated)
        bus.emit(MobilityEvent.MovementModeChanged(s.id, at, s.currentMode, mode))
        log.log(DebugEventLogger.Category.CONTEXT, "Trecho: ${s.currentMode} → $mode")
        if (mode.isVehicle) sampleVehicleSpeed(updated, at)
        if (mode == MovementMode.VEHICLE_UNKNOWN) askTransport(updated, vehicleMoving = true)
    }

    /**
     * "Veículo" sem classificação → preferência do usuário, depois padrão aprendido; senão
     * fica VEHICLE_UNKNOWN (e a pergunta resolve). Retorna o modo e a origem da classificação.
     */
    private suspend fun classify(mode: MovementMode, originPlaceId: Long?, at: Long): Pair<MovementMode, MobilitySource?> {
        lastClassification = null
        if (mode != MovementMode.VEHICLE_UNKNOWN) return mode to null
        val m = settings.current().mobility
        m.preferredMode?.takeIf { it.isVehicle }?.let { return it to MobilitySource.PREFERENCE }
        if (HoodieConfig.TRANSPORT_CLASSIFIER_V2 && intelligence != null && transportFeatures != null) {
            val result = vehicleClassification(originPlaceId, at)
            lastClassification = result
            return (if (result.confidence.value >= .60f) result.mode else MovementMode.VEHICLE_UNKNOWN) to
                if (result.evidence[result.mode].orEmpty().any { it.type == com.hoodie.app.domain.detection.EvidenceType.LEARNED_PATTERN }) MobilitySource.LEARNED else MobilitySource.ACTIVITY_RECOGNITION
        }
        if (m.learnTrips) {
            MobilityLearningEngine.learnedVehicleMode(repo.trips(at), originPlaceId, at, clock.zone())?.let { return it to MobilitySource.LEARNED }
        }
        return MovementMode.VEHICLE_UNKNOWN to null
    }

    private suspend fun vehicleClassification(origin: Long?, at: Long): TransportClassification {
        val destination = if (origin != null && settings.current().mobility.learnTrips) MobilityLearningEngine.predictedDestination(repo.trips(clock.nowMillis()), origin, at, clock.zone()) else null
        val local = at.atZone(clock.zone())
        val patterns = if (origin != null && destination != null) intelligence?.transportPatterns(origin, destination,
            com.hoodie.app.domain.routine.routineDayGroup(local.dayOfWeek), local.minuteOfDay() / 30).orEmpty() else emptyList()
        return TransportClassifier.classify(requireNotNull(transportFeatures).build(clock.nowMillis()), patterns, clock.nowMillis())
    }

    private suspend fun refreshVehicleClassification(s: MobilitySessionEntity, at: Long) {
        if (!HoodieConfig.TRANSPORT_CLASSIFIER_V2 || transportFeatures == null || intelligence == null) return
        val segment = repo.openSegment(s.id) ?: return
        if (segment.source == MobilitySource.CONFIRMATION || segment.source == MobilitySource.USER_CORRECTION || segment.source == MobilitySource.PREFERENCE) return
        val incoming = vehicleClassification(s.originPlaceId, s.startedAt)
        val verdict = TransportDecisionPolicy.resolve(segment.mode, com.hoodie.app.domain.detection.ConfidenceScore(segment.confidence), incoming,
            pendingVehicleSwitches[s.id], at)
        if (verdict.pending == null) pendingVehicleSwitches.remove(s.id) else pendingVehicleSwitches[s.id] = verdict.pending
        if (verdict.mode == segment.mode) return
        repo.updateSegment(segment.copy(endedAt = maxOf(at, segment.startedAt)))
        repo.insertSegment(MobilitySegmentEntity(sessionId = s.id, mode = verdict.mode, startedAt = at, confidence = incoming.confidence.value,
            confirmed = incoming.confidence.value >= .85f, source = MobilitySource.LEARNED))
        repo.update(s.copy(currentMode = verdict.mode, state = MobilityStateMachine.stateFor(verdict.mode)))
        bus.emit(MobilityEvent.MovementModeChanged(s.id, at, s.currentMode, verdict.mode))
    }

    /** Chegada (geofence ENTER, leitura pontual ou destino provável confirmado). */
    private suspend fun arrive(s: MobilitySessionEntity, place: Place, at: Long, byGeofence: Boolean) {
        var session = s
        if (session.state == MobilityState.MOVEMENT_CANDIDATE) {
            // Voltou para onde estava (ou nunca saiu de verdade): não houve viagem.
            if (place.id == session.originPlaceId || session.currentMode == MovementMode.NONE || !session.leftOrigin) {
                discard(session, "voltou antes de confirmar"); return
            }
            // Chegou a outro lugar com movimento observado: a viagem existiu.
            promote(session, at, MobilitySource.GEOFENCE)
            session = repo.session(session.id) ?: return
        }
        if (place.id == session.originPlaceId && MobilityStateMachine.isFlap(session.startedAt, at)) {
            discard(session, "oscilação de GPS na borda"); return
        }
        bus.emit(MobilityEvent.KnownPlaceApproaching(session.id, at, place.id))
        val m = settings.current().mobility
        val trips = if (m.learnTrips) repo.trips(at) else emptyList()
        val approved = MobilityLearningEngine.patternKey(session.originPlaceId, place.id) in m.approvedPatterns
        // Uma correção recente ("não cheguei") vale mais que o padrão aprovado: volta a perguntar.
        val auto = m.learnTrips && !MobilityLearningEngine.lastArrivalCorrected(trips, place.id) &&
            (MobilityLearningEngine.arrivalAutoConfirm(trips, place.id) || approved)
        finish(
            session.copy(arrivalConfirmed = if (auto) true else null, arrivalAutoConfirmed = auto),
            destination = place.id, at = at, reason = if (auto) "chegada automática" else "chegada",
        )
        val ended = repo.session(session.id) ?: return
        if (HoodieConfig.TRANSPORT_CLASSIFIER_V2 && session.destinationPlaceId == null) {
            repo.segmentsOf(session.id).filter {
                it.confirmed && it.mode.isVehicle &&
                    it.source in setOf(MobilitySource.CONFIRMATION, MobilitySource.USER_CORRECTION)
            }.map { it.mode }.distinct().forEach { transportPatterns?.record(ended, it) }
        }
        bus.emit(MobilityEvent.PlaceArrived(session.id, at, place.id, auto))
        if (!byGeofence) contextEngine.arriveAt(place.id, at)

        if (!auto && !contextAlreadyAsked(place.id, at)) {
            ask(QuestionKind.CONFIRM_ARRIVAL, ended, places.byId(place.id)?.type?.toContext(), place.id, vehicleMoving = false)
        }
        // Veículo nunca classificado: agora parado, pode perguntar ("você estava de carro?").
        val current = repo.session(session.id) ?: return
        if (current.pendingModeQuestion) askTransport(current, vehicleMoving = false)
        maybeAskTripPattern(repo.session(session.id) ?: return, trips, m.learnTrips)
    }

    /** Parado há [HoodieConfig.STILL_ARRIVAL_MS] sem geofence: resolve pela ordem do [ArrivalResolver]. */
    private suspend fun resolveStillArrival(s: MobilitySessionEntity, now: Long) {
        val stillAt = s.stillSince ?: now
        val m = settings.current().mobility
        val trips = if (m.learnTrips) repo.trips(now) else emptyList()
        val resolution = ArrivalResolver.resolve(
            geofencePlace = null,
            readPosition = { location.current() },
            containing = { lat, lng -> places.containing(lat, lng) },
            probablePlaceId = { if (m.learnTrips) MobilityLearningEngine.predictedDestination(trips, s.originPlaceId, s.startedAt, clock.zone()) else null },
        )
        when (resolution) {
            is ArrivalResolution.KnownPlace -> arrive(s, resolution.place, stillAt, byGeofence = false)
            is ArrivalResolution.Unknown -> {
                finish(s, destination = null, at = stillAt, reason = "lugar desconhecido")
                // Fluxo atual de lugar novo (UNKNOWN + "o que é este lugar?").
                contextEngine.arriveAtPosition(resolution.latitude, resolution.longitude, askNewPlace = m.confirmNewPlaces)
            }
            is ArrivalResolution.Probable -> {
                // Sem posição: só pergunta; o contexto muda quando confirmado ou pela geofence.
                val arriving = s.copy(state = MobilityState.ARRIVING, destinationPlaceId = resolution.placeId)
                repo.update(arriving)
                ask(QuestionKind.CONFIRM_ARRIVAL, arriving, places.byId(resolution.placeId)?.type?.toContext(), resolution.placeId, vehicleMoving = false)
            }
            ArrivalResolution.Unresolved -> repo.update(s.copy(state = MobilityState.ARRIVING))
        }
    }

    private suspend fun answerArrival(s: MobilitySessionEntity, yes: Boolean, placeId: Long?, now: Long) {
        if (yes) {
            if (s.endedAt == null) {
                // Destino provável confirmado: agora sim é chegada (e o contexto muda).
                val place = (placeId ?: s.destinationPlaceId)?.let { places.byId(it) } ?: return
                arrive(s.copy(arrivalConfirmed = true), place, now, byGeofence = false)
                repo.session(s.id)?.let { repo.update(it.copy(arrivalConfirmed = true)) }
            } else {
                repo.update(s.copy(arrivalConfirmed = true))
            }
            return
        }
        // "Não estou aí": a confiança desse padrão cai (arrivalConfirmed = false entra no aprendizado)
        // e o deslocamento continua.
        val reopened = s.copy(
            arrivalConfirmed = false, endedAt = null, destinationPlaceId = null,
            state = MobilityStateMachine.stateFor(s.currentMode).takeIf { it != MobilityState.MOVEMENT_CANDIDATE } ?: MobilityState.WALKING,
            stillSince = null,
        )
        // Guarda a correção numa sessão encerrada (para o aprendizado) e segue com uma nova a partir de agora.
        repo.update(s.copy(arrivalConfirmed = false, destinationPlaceId = placeId ?: s.destinationPlaceId, endedAt = s.endedAt ?: now, state = MobilityState.ARRIVED))
        val continuing = repo.insert(reopened.copy(id = 0, startedAt = now, originPlaceId = s.originPlaceId, arrivalConfirmed = null, questionsAsked = s.questionsAsked, movingSince = now))
        repo.insertSegment(MobilitySegmentEntity(sessionId = continuing.id, mode = continuing.currentMode, startedAt = now, confidence = continuing.confidence, source = MobilitySource.CONFIRMATION))
        val rejected = placeId ?: s.destinationPlaceId
        if (rejected != null) contextEngine.rejectArrival(rejected, now)
        log.log(DebugEventLogger.Category.CONTEXT, "Chegada rejeitada: segue deslocamento")
    }

    private suspend fun maybeAskTripPattern(s: MobilitySessionEntity, trips: List<TripRecord>, learn: Boolean) {
        if (!learn || s.questionsAsked > 0 || s.destinationPlaceId == null || s.originPlaceId == null) return
        val key = MobilityLearningEngine.patternKey(s.originPlaceId, s.destinationPlaceId)
        val m = settings.current().mobility
        if (key in m.approvedPatterns || key in m.declinedPatterns) return
        if (!MobilityLearningEngine.repeatedPattern(trips, s.originPlaceId, s.destinationPlaceId, s.startedAt, clock.zone())) return
        ask(QuestionKind.CONFIRM_TRIP_PATTERN, s, places.byId(s.destinationPlaceId)?.type?.toContext(), s.destinationPlaceId, vehicleMoving = false)
    }

    /** Encerra o deslocamento (trecho aberto fecha junto). */
    private suspend fun finish(s: MobilitySessionEntity, destination: Long?, at: Long, reason: String) {
        pendingVehicleSwitches.remove(s.id)
        repo.openSegment(s.id)?.let { repo.updateSegment(it.copy(endedAt = maxOf(at, it.startedAt))) }
        val ended = s.copy(endedAt = maxOf(at, s.startedAt), destinationPlaceId = destination, state = MobilityState.ARRIVED, stillSince = null,
            confirmed = s.confirmed || destination != null,
            confidence = if (destination != null) maxOf(s.confidence, .85f) else s.confidence)
        repo.update(ended)
        scheduler.cancelMobilityCheck()
        bus.emit(MobilityEvent.MobilityEnded(s.id, ended.endedAt!!, destination, ended.endedAt - s.startedAt))
        log.log(DebugEventLogger.Category.CONTEXT, "Deslocamento encerrado: $reason")
    }

    /** Candidato que não era deslocamento: some sem deixar rastro (nem no Diário). */
    private suspend fun discard(s: MobilitySessionEntity, reason: String) {
        // Pergunta ainda aberta sobre um deslocamento que não existiu: some junto.
        val now = clock.nowMillis()
        questions.since(now - DAY_MS).filter { it.mobilitySessionId == s.id && it.answeredAt == null }.forEach {
            questions.update(it.copy(answeredAt = now, answer = "DISCARDED"))
            notifier.cancelQuestion(it.id)
        }
        repo.delete(s.id)
        scheduler.cancelMobilityCheck()
        log.log(DebugEventLogger.Category.CONTEXT, "Mobilidade descartada: $reason")
    }

    // ───────────────────────── Perguntas ─────────────────────────

    private suspend fun askTransport(s: MobilitySessionEntity, vehicleMoving: Boolean) {
        val segments = repo.segmentsOf(s.id)
        val segment = segments.lastOrNull { it.mode.isVehicle }
        if (segment == null || segment.mode != MovementMode.VEHICLE_UNKNOWN) {
            if (s.pendingModeQuestion) repo.update(s.copy(pendingModeQuestion = false))
            return
        }
        var realModeChange = false
        if (HoodieConfig.TRANSPORT_CLASSIFIER_V2 && intelligence != null && transportFeatures != null) {
            val result = vehicleClassification(s.originPlaceId, s.startedAt)
            val history = questions.since(clock.nowMillis() - DAY_MS).filter { it.kind == QuestionKind.SELECT_TRANSPORT_MODE }
            val previousQuestion = history.lastOrNull { it.mobilitySessionId == s.id }
            realModeChange = previousQuestion != null && segment.startedAt > previousQuestion.askedAt && segments.any {
                it.mode.isActive && it.startedAt > previousQuestion.askedAt && (it.endedAt ?: Long.MAX_VALUE) <= segment.startedAt
            }
            if (!com.hoodie.app.engine.detection.QuestionPolicy.canAsk(result.detection, "transport:${result.mode.name}", clock.nowMillis(),
                    history.map { com.hoodie.app.engine.detection.DetectionQuestion("transport:${it.answer ?: result.mode.name}", it.askedAt, it.mobilitySessionId, it.answer) },
                    transportSessionId = s.id, mode = result.mode.name, realModeChange = realModeChange)) return
        }
        ask(QuestionKind.SELECT_TRANSPORT_MODE, s, null, null, vehicleMoving, realModeChange)
    }

    /**
     * Pergunta pelo [MobilityConfirmationPolicy]. DEFER deixa marcada para depois (veículo
     * andando); retorna o id quando perguntou.
     */
    private suspend fun ask(kind: QuestionKind, s: MobilitySessionEntity, candidate: UserContextType?, placeId: Long?, vehicleMoving: Boolean, realModeChange: Boolean = false): Long? {
        val now = clock.nowMillis()
        val recent = questions.since(now - DAY_MS).map { AskedQuestion(it.kind, it.candidate, it.askedAt) }
        // Mesma pergunta já feita para este deslocamento: nunca repete.
        if (!realModeChange && questions.since(now - DAY_MS).any { it.mobilitySessionId == s.id && it.kind == kind }) return null
        val verdict = MobilityConfirmationPolicy.evaluate(kind, if (realModeChange) 0 else s.questionsAsked, vehicleMoving, appInForeground, now, clock.zone(), candidate, recent)
        val latest = repo.session(s.id) ?: s
        when (verdict) {
            MobilityConfirmationPolicy.Verdict.SKIP -> {
                if (latest.pendingModeQuestion && kind == QuestionKind.SELECT_TRANSPORT_MODE) repo.update(latest.copy(pendingModeQuestion = false))
                return null
            }
            MobilityConfirmationPolicy.Verdict.DEFER -> {
                if (kind == QuestionKind.SELECT_TRANSPORT_MODE && !latest.pendingModeQuestion) repo.update(latest.copy(pendingModeQuestion = true))
                return null
            }
            MobilityConfirmationPolicy.Verdict.ASK -> Unit
        }
        val entity = ContextQuestionEntity(
            kind = kind, candidate = candidate, placeId = placeId, encryptedCoordinates = null,
            contextEventId = contextDao.current()?.id, askedAt = now, mobilitySessionId = s.id,
        )
        val id = questions.insert(entity)
        val countsForMovement = kind == QuestionKind.CONFIRM_MOVEMENT || kind == QuestionKind.SELECT_TRANSPORT_MODE
        repo.update(
            latest.copy(
                questionsAsked = latest.questionsAsked + if (countsForMovement) 1 else 0,
                // Só a própria pergunta do transporte resolve a pendência dele.
                pendingModeQuestion = latest.pendingModeQuestion && kind != QuestionKind.SELECT_TRANSPORT_MODE,
            ),
        )
        val prompt = ContextQuestion(id, kind, candidate, placeId, null, entity.contextEventId, now, null, null).prompt
        if (kind.isYesNo) notifier.askYesNo(id, prompt) else notifier.askInApp(id, prompt)
        log.log(DebugEventLogger.Category.CONTEXT, "Pergunta de mobilidade: $kind")
        return id
    }

    private suspend fun markAnswered(questionId: Long, answer: String): ContextQuestionEntity? {
        val q = questions.getById(questionId) ?: return null
        if (q.answeredAt != null || !q.kind.isMobility) return null
        questions.update(q.copy(answeredAt = clock.nowMillis(), answer = answer))
        notifier.cancelQuestion(questionId)
        return q
    }

    /** O ContextEngine já perguntou "você está no Trabalho?" para esta chegada: não pergunta de novo. */
    private suspend fun contextAlreadyAsked(placeId: Long, at: Long): Boolean =
        questions.since(at - 2 * MINUTE_MS).any { it.kind == QuestionKind.CONFIRM_CONTEXT && it.placeId == placeId }

    private suspend fun originContext(s: MobilitySessionEntity): UserContextType? =
        s.originPlaceId?.let { places.byId(it) }?.type?.toContext()

    /** Saída perto do horário de entrada ou saída do trabalho, em dia de trabalho. */
    private suspend fun timeMatchesRoutine(at: Long): Boolean {
        val routine = routines.get()
        if (!routine.hasWork) return false
        val z = at.atZone(clock.zone())
        if (!RoutineEngine.isWorkDay(z.toLocalDate(), routine, routines.isDayOff(z.toLocalDate()))) return false
        val minute = z.minuteOfDay()
        return abs(minute - routine.startMinute) <= HoodieConfig.TRIP_PATTERN_WINDOW_MIN + 30 ||
            abs(minute - routine.endMinute) <= HoodieConfig.TRIP_PATTERN_WINDOW_MIN + 30
    }
}
