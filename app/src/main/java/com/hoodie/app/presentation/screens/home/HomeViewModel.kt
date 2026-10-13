package com.hoodie.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.QuestionDao
import com.hoodie.app.core.datastore.AppSettings
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.location.LocationProvider
import com.hoodie.app.core.location.LocationStatus
import com.hoodie.app.core.model.ContextEvent
import com.hoodie.app.core.model.ContextQuestion
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.model.WorkMode
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.currentDateFlow
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.core.time.HOUR_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.core.time.minuteOfDay
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.data.repository.RoutineRepository
import com.hoodie.app.data.repository.toDomain
import com.hoodie.app.engine.context.ContextEngine
import com.hoodie.app.engine.dialogue.DialogueEngine
import com.hoodie.app.engine.dialogue.DialogueInput
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.hoodie.HoodieSnapshot
import com.hoodie.app.engine.routine.RoutineEngine
import com.hoodie.app.engine.routine.UpcomingEvent
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.pixel.scene.VisualState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import com.hoodie.app.core.error.*
import com.hoodie.app.presentation.common.runUiAction
import com.hoodie.app.presentation.common.retryableUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import javax.inject.Inject
import kotlin.random.Random

data class HomeUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val catName: String = "Hoodie",
    val now: Long = 0,
    val period: DayPeriod = DayPeriod.DAY,
    val context: ContextEvent? = null,
    val contextPlaceName: String? = null,
    val snapshot: HoodieSnapshot? = null,
    val visual: VisualState? = null,
    val next: UpcomingEvent? = null,
    val routineOrigin: String? = null,
    val dialogue: String? = null,
    val probableMode: Boolean = false,
    val locationStatus: LocationStatus = LocationStatus.OK,
    val question: ContextQuestion? = null,
    val newPlaceSaveOptions: com.hoodie.app.engine.context.NewPlaceSaveOptions? = null,
    val vehicleMoving: Boolean = false,
    val suggestSavePlace: PlaceType? = null,
    val canSaveHere: Boolean = false,
)

private data class HomeTick(
    val inputs: Inputs,
    val now: Long,
    val mobility: com.hoodie.app.core.mobility.MobilityVisualSnapshot,
    val day: com.hoodie.app.domain.daystate.DayStateSnapshot?,
)

private data class Inputs(
    val settings: AppSettings,
    val routine: Routine,
    val context: ContextEvent?,
    val places: List<Place>,
    val dayOff: Boolean,
    val effective: com.hoodie.app.domain.routine.EffectiveRoutineSnapshot? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val hoodie: HoodieEngine,
    private val contextEngine: ContextEngine,
    private val settingsRepo: SettingsRepository,
    private val routines: RoutineRepository,
    private val places: PlaceRepository,
    private val location: LocationProvider,
    private val dialogues: DialogueEngine,
    private val clock: ClockProvider,
    private val log: DebugEventLogger,
    private val router: com.hoodie.app.engine.mobility.QuestionRouter,
    private val mobility: com.hoodie.app.engine.mobility.MobilityEngine,
    mobilityRepo: com.hoodie.app.data.repository.MobilityRepository,
    private val visualDiagnostics: com.hoodie.app.core.debug.HomeVisualDiagnostics,
    private val contextDao: ContextEventDao,
    questionDao: QuestionDao,
    dayStates: dagger.Lazy<com.hoodie.app.engine.daystate.DayStateCoordinator>? = null,
    private val effectiveRoutine: com.hoodie.app.data.repository.EffectiveRoutineRepository? = null,
    personalityRuntime: com.hoodie.app.engine.personality.PersonalityRuntime? = null,
) : ViewModel() {
    private val personality = personalityRuntime?.takeIf { com.hoodie.app.core.config.HoodieConfig.HOODIE_PERSONALITY_V2 }
        ?.let(::PersonalityDialogueScheduler)
    private val ambientDialogue = AmbientDialogueScheduler(dialogues)
    private var lastScene: com.hoodie.app.pixel.scene.SceneId? = null
    private val dayStateCoordinator = if (com.hoodie.app.core.config.HoodieConfig.DAY_STATE_ENGINE) dayStates?.get() else null
    val dayState = dayStateCoordinator?.snapshots ?: kotlinx.coroutines.flow.flowOf(null)
    private val wakeEvidence = dayStateCoordinator?.wakeEvidence ?: kotlinx.coroutines.flow.flowOf(null)
    val zone get() = clock.zone()


    /** Provisório e confirmado são ambos visíveis à Home; activeMode mantém seu contrato para os demais consumidores. */
    private val mobilityVisualObservation = mobilityRepo.visualObservation

    private val _events = Channel<HomeUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    override fun onCleared() {
        mobility.onAppClosed()
        super.onCleared()
    }


    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** Relógio da tela: só corre enquanto alguém observa (pausa em background). */
    private val ticker = flow { while (true) { emit(clock.nowMillis()); delay(TICK_MS) } }

    private val manualInputs = combine(
        settingsRepo.settings,
        routines.routine,
        contextDao.observeCurrent().map { it?.toDomain() },
        places.places,
        // Vira o dia junto com o relógio (meia-noite, fuso, TIME_SET): nunca observa o dia antigo.
        currentDateFlow(clock).flatMapLatest { routines.observeDayOff(it) },
    ) { s, r, c, p, off -> Inputs(s, r, c, p, off) }

    private val inputs = combine(manualInputs, effectiveRoutine?.current ?: kotlinx.coroutines.flow.flowOf(null)) { i, effective ->
        if (effective == null) i else i.copy(routine = effective.routine, settings = i.settings.copy(sleep = effective.sleep), effective = effective)
    }

    private val pendingQuestion = questionDao.observePending(clock.nowMillis() - 12 * HOUR_MS).map { list ->
        list.firstOrNull { !(com.hoodie.app.core.config.HoodieConfig.PASSIVE_CONTEXT_CONFIRMATION && it.kind == com.hoodie.app.core.model.QuestionKind.CONFIRM_CONTEXT) }
            ?.let { ContextQuestion(it.id, it.kind, it.candidate, it.placeId, it.chosenPlaceType, it.contextEventId, it.askedAt, it.answeredAt, it.answer) }
    }

    private val reloadHome = MutableStateFlow(0L)
    private val homeNowReconciled = MutableStateFlow(false)
    private var homeResumeJob: Job? = null
    private val manualPlaceOptions = MutableStateFlow<com.hoodie.app.engine.context.NewPlaceSaveOptions?>(null)
    private var placeOptionsJob: Job? = null
    val state: StateFlow<HomeUiState> = retryableUiState(
        retries = reloadHome,
        loading = HomeUiState(),
        onFailure = { cause ->
            Log.e(TAG, "Failed to load home", cause)
            HomeUiState(loading = false, error = cause.appErrorOr(DatabaseError.ReadFailed))
        },
        source = {
            combine(inputs, ticker, mobilityVisualObservation, dayState) { i, now, mobility, day -> HomeTick(i, now, mobility, day) }
                .mapLatest { t -> build(t.inputs, t.now, hoodie.resolve(), t.mobility, t.day) }
                .combineWithQuestion()
        },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val homeNow: StateFlow<com.hoodie.app.domain.home.HomeNowSnapshot?> = combine(state, dayState, wakeEvidence, homeNowReconciled) { home, day, wake, reconciled ->
        if (!reconciled || home.loading || home.error != null) null else {
            val todayWake = wake?.takeIf { java.time.Instant.ofEpochMilli(it.first).atZone(clock.zone()).toLocalDate() == clock.today() }
            com.hoodie.app.engine.home.HomeNowAssembler.assemble(day, home.context, home.contextPlaceName,
                todayWake?.first, todayWake?.second, home.now, home.locationStatus)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Hide the previous snapshot until canonical sources have been reconciled on every resume. */
    fun onHomeStarted() {
        visualDiagnostics.visibility(true)
        personality?.onForeground(clock.nowMillis())
        homeResumeJob?.cancel()
        homeNowReconciled.value = false
        homeResumeJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { mobility.onAppOpened() }
                if (com.hoodie.app.core.config.HoodieConfig.PASSIVE_CONTEXT_CONFIRMATION) {
                    runCatching { withContext(Dispatchers.IO) { contextEngine.dismissPendingContextConfirmations() } }
                }
                val reconciledDayState = withContext(Dispatchers.IO) { dayStateCoordinator?.reconcile() }
                if (reconciledDayState != null) {
                    withTimeout(10_000) { dayState.first { it == reconciledDayState } }
                }
                reloadHome.value += 1
                val currentEventId = withContext(Dispatchers.IO) { contextDao.current()?.id }
                withTimeout(10_000) {
                    state.first { !it.loading && it.error == null && it.context?.id == currentEventId }
                }
                homeNowReconciled.value = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Failed to reconcile HomeNow on resume", error)
            }
        }
    }

    fun onHomeStopped() {
        visualDiagnostics.visibility(false)
        personality?.onBackground(clock.nowMillis())
        mobility.onAppClosed()
        homeResumeJob?.cancel()
        homeNowReconciled.value = false
    }

    fun retryLoad() { reloadHome.value += 1 }

    fun onScenePresented(scene: com.hoodie.app.pixel.scene.SceneId) {
        visualDiagnostics.presented(scene, clock.nowMillis())
    }

    private fun kotlinx.coroutines.flow.Flow<HomeUiState>.combineWithQuestion() =
        combine(this, pendingQuestion, manualPlaceOptions) { s, q, manual ->
            val visible = q?.takeUnless { s.vehicleMoving && it.kind in setOf(
                com.hoodie.app.core.model.QuestionKind.NEW_PLACE, com.hoodie.app.core.model.QuestionKind.SAVE_PLACE) }
            s.copy(question = visible, newPlaceSaveOptions = visible?.takeIf {
                it.kind == com.hoodie.app.core.model.QuestionKind.SAVE_PLACE
            }?.let { manual?.takeIf { options -> options.questionId == it.id } ?: contextEngine.newPlaceSaveOptions(it.id) })
        }

    fun updateNewPlacePosition(id: Long, position: Pair<Double, Double>?) {
        placeOptionsJob?.cancel()
        manualPlaceOptions.value = null
        if (position == null) return
        placeOptionsJob = viewModelScope.launch {
            manualPlaceOptions.value = withContext(Dispatchers.IO) { contextEngine.newPlaceSaveOptions(id, position) }
        }
    }

    private suspend fun build(
        i: Inputs, now: Long, snap: HoodieSnapshot, mobility: com.hoodie.app.core.mobility.MobilityVisualSnapshot,
        day: com.hoodie.app.domain.daystate.DayStateSnapshot? = null,
    ): HomeUiState {
        val zoned = clock.now()
        val minute = zoned.minuteOfDay()
        val ctx = i.context
        val workDay = RoutineEngine.isWorkDay(zoned.toLocalDate(), i.routine, i.dayOff)
        val status = location.status()
        val probable = ctx == null || ctx.source == ContextSource.ROUTINE
        val variant = (zoned.toLocalDate().toEpochDay() + (ctx?.id ?: 0)).toInt()
        val visual = VisualDirector.resolve(
            activity = snap.state.activity,
            // A correção manual é persistida em context_events antes do snapshot do simulador.
            // A cena deve refletir imediatamente o evento canônico observado pela Home.
            context = ctx?.type ?: snap.state.userContext,
            homeOffice = i.routine.workMode == WorkMode.HOME_OFFICE,
            commute = i.settings.commuteStyle,
            mobilityMode = mobility.mode,
            variant = variant,
            energy = snap.liveNeeds.energy,
            mood = snap.liveNeeds.mood,
            social = snap.liveNeeds.social,
            hunger = snap.liveNeeds.hunger,
            focus = snap.liveNeeds.focus,
            shoppingVenue = ctx?.venueType,
            manualTransition = ctx?.source == ContextSource.MANUAL || ctx?.source == ContextSource.USER_CORRECTION,
            mobilitySnapshot = mobility,
            preferredMode = i.settings.mobility.preferredMode,
            now = now,
        // A história ambiental do dia vem da jornada (data), separada da aparência (variant).
        ).copy(daySeed = zoned.toLocalDate().toEpochDay().toInt(), placeId = ctx?.placeId)
        visualDiagnostics.selected(visual, now)
        val userType = ctx?.type ?: UserContextType.HOME
        val tomorrowDate = zoned.toLocalDate().plusDays(1)
        val tomorrow = effectiveRoutine?.resolve(tomorrowDate, tomorrowDate.atStartOfDay(clock.zone()).toInstant().toEpochMilli())
        val next = if (i.effective != null && tomorrow != null) RoutineEngine.nextEvent(zoned, i.effective, tomorrow)
            else RoutineEngine.nextEvent(zoned, i.routine, i.settings.sleep, i.dayOff, userType)
        val originSnapshot = if (next.tomorrow) tomorrow else i.effective
        val origin = next.routineType?.let { originSnapshot?.values?.get(it) }?.takeIf { it.origin != com.hoodie.app.domain.routine.RoutineValueOrigin.MANUAL }
        val originText = origin?.let { if (it.origin == com.hoodie.app.domain.routine.RoutineValueOrigin.LEARNED)
            "Aprendido${if (it.shiftLimited) " · ajuste limitado a 90 min" else ""} · ${(100 * (it.confidence ?: 0f)).toInt()}% · ${it.sampleCount} observações"
            else if (it.fallbackReason == "NO_ELIGIBLE_PATTERN") "Automático · referência manual (sem padrão elegível)"
            else "Automático · referência manual (horários incompatíveis)" }

        // Personalidade: um momento do dia (chegou, pausa, lugar novo...) pode virar gesto e fala.
        val daySeed = zoned.toLocalDate().toEpochDay().toInt()
        val sceneChanged = lastScene != null && lastScene != visual.scene
        lastScene = visual.scene
        personality?.update(
            com.hoodie.app.engine.personality.PersonalityObservation(
                now = now, daySeed = daySeed, hour = zoned.hour, contextId = ctx?.id, contextType = ctx?.type,
                contextStartedAt = ctx?.startedAt, placeId = ctx?.placeId, activity = snap.state.activity,
                dayState = day?.state, dayStateStartedAt = day?.startedAt,
            ),
            com.hoodie.app.engine.personality.PersonalityGate(
                walking = snap.state.activity == com.hoodie.app.core.model.HoodieActivity.WALKING ||
                    (ctx?.type ?: snap.state.userContext) == UserContextType.COMMUTING,
                workAction = snap.state.activity == com.hoodie.app.core.model.HoodieActivity.WORKING,
                sceneTransition = sceneChanged,
            ),
        ) { detected ->
            com.hoodie.app.engine.personality.PersonalityContext(
                trigger = detected.trigger, scene = visual.scene, activity = snap.state.activity, needs = snap.liveNeeds,
                placeId = ctx?.placeId, occurredAt = now, daySeed = daySeed, visitSeed = detected.visitSeed,
                userContext = ctx?.type ?: snap.state.userContext, hour = zoned.hour,
            )
        }?.animation?.let { _events.send(HomeUiEvent.React(it)) }
        val personalLine = personality?.current(now)
        if (personalLine != null) ambientDialogue.holdUntil(personality!!.speakingUntil)
        // Fala ocasional, sem IA: regras do dialogues.json. Fica alguns segundos e some.
        val ambientLine = ambientDialogue.next(now) {
            val ctxMinutes = ctx?.let { (now - it.startedAt) / MINUTE_MS } ?: 0
            val arrivedEarly = ctx?.type == UserContextType.WORK &&
                ctx.startedAt.let { java.time.Instant.ofEpochMilli(it).atZone(clock.zone()).minuteOfDay() } < i.routine.startMinute - 10
            DialogueInput(
                context = snap.state.userContext, activity = snap.state.activity, contextMinutes = ctxMinutes,
                hour = zoned.hour, weekend = zoned.dayOfWeek == DayOfWeek.SATURDAY || zoned.dayOfWeek == DayOfWeek.SUNDAY,
                overtime = ctx?.type == UserContextType.WORK && workDay && RoutineEngine.isOvertime(zoned, i.routine),
                energy = snap.liveNeeds.energy, arrivedEarly = arrivedEarly,
            )
        }
        val dialogueText = DialogueArbiter.choose(personalLine, ambientLine)?.text

        val hasHome = i.places.any { it.type == PlaceType.HOME }
        val hasWork = i.places.any { it.type == PlaceType.WORK }
        // Lugar faltando: sempre dá para buscar pelo endereço; "estou aqui agora" só faz
        // sentido com localização e (no caso do trabalho) em horário de expediente.
        val atWorkHours = workDay && RoutineEngine.isWorkHours(minute, i.routine)
        val suggest = when {
            i.routine.workMode == WorkMode.OFFICE && !hasWork && (atWorkHours || hasHome) -> PlaceType.WORK
            !hasHome -> PlaceType.HOME
            else -> null
        }
        val locationReady = status != LocationStatus.NO_PERMISSION && status != LocationStatus.DISABLED
        val canSaveHere = locationReady && (suggest == PlaceType.HOME || atWorkHours)
        return HomeUiState(
            loading = false, catName = i.settings.catName, now = now, period = DayPeriod.of(zoned.hour),
            context = ctx, contextPlaceName = ctx?.placeId?.let { id -> i.places.firstOrNull { it.id == id }?.name },
            snapshot = snap, visual = visual, next = next, routineOrigin = originText, dialogue = dialogueText,
            probableMode = probable, locationStatus = status,
            suggestSavePlace = suggest, canSaveHere = canSaveHere,
            vehicleMoving = mobility.mode?.isVehicle == true &&
                mobility.phase != com.hoodie.app.core.mobility.MobilityPhase.IDLE &&
                mobility.observedMovement != com.hoodie.app.core.mobility.DetectedMovement.STILL,
        )
    }

    fun setManual(type: PlaceType) = action {
        val context = type.toContext()
        contextEngine.setManualPlace(type)
        // "Não estou no trabalho" logo após uma chegada automática: a mobilidade aprende a correção.
        mobility.onManualContext(context, clock.nowMillis())
        _events.send(HomeUiEvent.React(AnimationId.HAPPY))
    }

    /** Lugares cadastrados, para escolher o local exato quando há mais de um do mesmo tipo. */
    val savedPlaces: StateFlow<List<Place>> = places.places.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Correção rápida com lugar exato; oferece Desfazer quando é seguro. */
    suspend fun quickCorrect(correction: com.hoodie.app.domain.correction.QuickContextCorrection): Boolean {
        if (_busy.value) return false
        _busy.value = true
        return try {
            when (val result = withContext(Dispatchers.IO) { contextEngine.correctWithUndo(correction) }) {
                com.hoodie.app.domain.correction.QuickCorrectionResult.Stale -> {
                    _events.send(HomeUiEvent.ShowMessage("O Hoodie identificou uma mudança. Confira sua atividade atual antes de salvar."))
                    false
                }
                is com.hoodie.app.domain.correction.QuickCorrectionResult.Applied -> {
                    if (correction.scope == com.hoodie.app.domain.correction.CorrectionScope.FROM_NOW) {
                        withContext(Dispatchers.IO) { mobility.onManualContext(correction.placeType.toContext(), clock.nowMillis()) }
                    }
                    _events.send(HomeUiEvent.React(AnimationId.HAPPY))
                    result.operationId?.let { _events.send(HomeUiEvent.OfferUndo(it, "Correção salva.")) }
                    true
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Failed to save quick correction", error)
            _events.send(HomeUiEvent.ShowMessage("Não foi possível salvar a correção. Tente novamente."))
            false
        } finally {
            _busy.value = false
        }
    }

    fun undoCorrection(operationId: String) = action {
        val message = when (val r = contextEngine.undoCorrection(operationId)) {
            com.hoodie.app.domain.correction.UndoResult.Restored -> "Correção desfeita."
            com.hoodie.app.domain.correction.UndoResult.NotFound -> "Não encontrei essa correção."
            is com.hoodie.app.domain.correction.UndoResult.Blocked -> when (r.reason) {
                com.hoodie.app.domain.correction.UndoBlockReason.EXPIRED -> "Já não é seguro desfazer. Edite manualmente no Diário."
                com.hoodie.app.domain.correction.UndoBlockReason.ALREADY_UNDONE -> "Essa correção já foi desfeita."
                com.hoodie.app.domain.correction.UndoBlockReason.CONFLICT -> "Algo mudou depois da correção, então não desfiz para não perder dados. Edite manualmente no Diário."
            }
        }
        _events.send(HomeUiEvent.ShowMessage(message))
    }

    fun confirmContext(expectedEventId: Long) = action {
        val confirmed = contextEngine.confirmCurrentContext(expectedEventId, clock.nowMillis())
        if (confirmed) _events.send(HomeUiEvent.React(AnimationId.HAPPY))
        else _events.send(HomeUiEvent.ShowMessage("O contexto mudou. Confira a informação atualizada."))
    }

    suspend fun correctCurrentContext(type: PlaceType, historical: Boolean, expectedEventId: Long?): Boolean {
        if (_busy.value) return false
        _busy.value = true
        return try {
            val corrected = withContext(Dispatchers.IO) { contextEngine.correctCurrentContext(expectedEventId, type, historical) }
            if (!corrected) {
                _events.send(HomeUiEvent.ShowMessage("O Hoodie identificou uma mudança. Confira sua atividade atual antes de salvar."))
                false
            } else {
                if (!historical) withContext(Dispatchers.IO) { mobility.onManualContext(type.toContext(), clock.nowMillis()) }
                _events.send(HomeUiEvent.React(AnimationId.HAPPY))
                true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Failed to save context correction", error)
            _events.send(HomeUiEvent.ShowMessage("Não foi possível salvar a correção. Tente novamente."))
            false
        } finally {
            _busy.value = false
        }
    }

    fun answerYesNo(id: Long, yes: Boolean) = action {
        // Contexto ou mobilidade: o roteador sabe de quem é a pergunta.
        router.answerYesNo(id, yes)
        _events.send(HomeUiEvent.React(if (yes) AnimationId.HAPPY else AnimationId.SHRUG))
    }

    fun answerTransportMode(id: Long, mode: com.hoodie.app.core.mobility.MovementMode) = action {
        router.answerTransportMode(id, mode)
        _events.send(HomeUiEvent.React(AnimationId.HAPPY))
    }

    fun answerNewPlace(id: Long, type: PlaceType) = action {
        contextEngine.answerNewPlace(id, type)
        personality?.onNewPlaceConfirmed(id)
        _events.send(HomeUiEvent.React(AnimationId.SURPRISED))
    }

    fun answerSavePlace(id: Long, save: Boolean) = placeAction {
        contextEngine.answerSavePlace(id, save)
        _events.send(HomeUiEvent.React(AnimationId.HAPPY))
    }

    fun saveDiscoveredPlace(id: Long, name: String, reuseId: Long?, manualPosition: Pair<Double, Double>?) = placeAction {
        contextEngine.answerSavePlace(id, true, name, reuseId, manualPosition)
        personality?.onNewPlaceConfirmed(id)
        _events.send(HomeUiEvent.React(AnimationId.HAPPY))
    }

    fun dismissQuestion(id: Long) = action { contextEngine.dismissQuestion(id) }

    /** "Chegou ao trabalho? Salvar este local." */
    fun savePlaceHere(type: PlaceType) = viewModelScope.launch {
        if (_busy.value) return@launch
        _busy.value = true
        try {
            log.log(DebugEventLogger.Category.PLACE, "SAVE_STARTED type=$type")
            val pos = location.current() ?: run {
                log.log(DebugEventLogger.Category.PLACE, "SAVE_FAILED type=$type location_unavailable")
                _events.send(HomeUiEvent.ShowError(LocationError.Unavailable))
                return@launch
            }
            contextEngine.savePlaceHere(type, type.label, pos.first, pos.second)
            log.log(DebugEventLogger.Category.PLACE, "SAVE_SUCCESS type=$type")
            _events.send(HomeUiEvent.React(AnimationId.HAPPY))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao salvar lugar $type", e)
            log.log(DebugEventLogger.Category.PLACE, "SAVE_FAILED type=$type ${e.javaClass.simpleName}")
            _events.send(HomeUiEvent.ShowError(e.appErrorOr(PlaceError.SaveFailed)))
        } finally {
            _busy.value = false
        }
    }

    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        runUiAction(DatabaseError.WriteFailed, { error, cause ->
            Log.e(TAG, "Home action failed", cause)
            _events.send(HomeUiEvent.ShowError(error))
        }, block)
    }

    private fun placeAction(block: suspend () -> Unit) = viewModelScope.launch {
        if (_busy.value) return@launch
        _busy.value = true
        try {
            runUiAction(PlaceError.SaveFailed, { error, cause ->
                Log.e(TAG, "Discovered place save failed", cause)
                _events.send(HomeUiEvent.ShowError(error))
            }) { withContext(Dispatchers.IO) { block() } }
        } finally {
            _busy.value = false
        }
    }

    companion object {
        const val TICK_MS = 20_000L
        private const val TAG = "HomeViewModel"
    }
}
