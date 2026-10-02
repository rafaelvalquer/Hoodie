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
import com.hoodie.app.core.error.*
import com.hoodie.app.presentation.common.runUiAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject
import kotlin.random.Random

data class HomeUiState(
    val loading: Boolean = true,
    val catName: String = "Hoodie",
    val now: Long = 0,
    val period: DayPeriod = DayPeriod.DAY,
    val context: ContextEvent? = null,
    val snapshot: HoodieSnapshot? = null,
    val visual: VisualState? = null,
    val next: UpcomingEvent? = null,
    val dialogue: String? = null,
    val probableMode: Boolean = false,
    val locationStatus: LocationStatus = LocationStatus.OK,
    val question: ContextQuestion? = null,
    val isWorkDay: Boolean = false,
    val isDayOff: Boolean = false,
    val suggestSavePlace: PlaceType? = null,
    val canSaveHere: Boolean = false,
)

private data class Inputs(
    val settings: AppSettings,
    val routine: Routine,
    val context: ContextEvent?,
    val places: List<Place>,
    val dayOff: Boolean,
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
    contextDao: ContextEventDao,
    questionDao: QuestionDao,
) : ViewModel() {

    /** Modo real do deslocamento em andamento (null = sem sessão confirmada → preferência). */
    private val activeMobilityMode = mobilityRepo.activeMode

    private val _events = Channel<HomeUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // App aberto: a escolha do transporte adiada (veículo andando) pode aparecer agora.
        action { mobility.onAppOpened() }
    }

    override fun onCleared() {
        mobility.onAppClosed()
        super.onCleared()
    }


    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** Relógio da tela: só corre enquanto alguém observa (pausa em background). */
    private val ticker = flow { while (true) { emit(clock.nowMillis()); delay(TICK_MS) } }

    private val inputs = combine(
        settingsRepo.settings,
        routines.routine,
        contextDao.observeCurrent().map { it?.toDomain() },
        places.places,
        // Vira o dia junto com o relógio (meia-noite, fuso, TIME_SET): nunca observa o dia antigo.
        currentDateFlow(clock).flatMapLatest { routines.observeDayOff(it) },
    ) { s, r, c, p, off -> Inputs(s, r, c, p, off) }

    private val pendingQuestion = questionDao.observePending(clock.nowMillis() - 12 * HOUR_MS).map { list ->
        list.firstOrNull()?.let { ContextQuestion(it.id, it.kind, it.candidate, it.placeId, it.chosenPlaceType, it.contextEventId, it.askedAt, it.answeredAt, it.answer) }
    }

    private var dialogueText: String? = null
    private var dialogueUntil = 0L

    val state: StateFlow<HomeUiState> = combine(inputs, ticker, activeMobilityMode) { i, now, mode -> Triple(i, now, mode) }
        .mapLatest { (i, now, mode) -> build(i, now, hoodie.resolve(), mode) }
        .combineWithQuestion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun kotlinx.coroutines.flow.Flow<HomeUiState>.combineWithQuestion() =
        combine(this, pendingQuestion) { s, q -> s.copy(question = q) }

    private fun build(i: Inputs, now: Long, snap: HoodieSnapshot, mobilityMode: com.hoodie.app.core.mobility.MovementMode? = null): HomeUiState {
        val zoned = clock.now()
        val minute = zoned.minuteOfDay()
        val ctx = i.context
        val workDay = RoutineEngine.isWorkDay(zoned.toLocalDate(), i.routine, i.dayOff)
        val status = location.status()
        val probable = ctx == null || ctx.source == ContextSource.ROUTINE
        val variant = (zoned.toLocalDate().toEpochDay() + (ctx?.id ?: 0)).toInt()
        val visual = VisualDirector.resolve(
            activity = snap.state.activity,
            context = snap.state.userContext,
            homeOffice = i.routine.workMode == WorkMode.HOME_OFFICE,
            commute = i.settings.commuteStyle,
            mobilityMode = mobilityMode,
            variant = variant,
            energy = snap.liveNeeds.energy,
            mood = snap.liveNeeds.mood,
        )
        val userType = ctx?.type ?: UserContextType.HOME
        val next = RoutineEngine.nextEvent(zoned, i.routine, i.settings.sleep, i.dayOff, userType)

        // Fala ocasional, sem IA: regras do dialogues.json. Fica alguns segundos e some.
        if (now >= dialogueUntil) {
            dialogueText = null
            if (Random.nextInt(100) < 40) {
                val ctxMinutes = ctx?.let { (now - it.startedAt) / MINUTE_MS } ?: 0
                val arrivedEarly = ctx?.type == UserContextType.WORK &&
                    ctx.startedAt.let { java.time.Instant.ofEpochMilli(it).atZone(clock.zone()).minuteOfDay() } < i.routine.startMinute - 10
                dialogueText = dialogues.pick(
                    DialogueInput(
                        context = snap.state.userContext, activity = snap.state.activity, contextMinutes = ctxMinutes,
                        hour = zoned.hour, weekend = zoned.dayOfWeek == DayOfWeek.SATURDAY || zoned.dayOfWeek == DayOfWeek.SUNDAY,
                        overtime = ctx?.type == UserContextType.WORK && workDay && RoutineEngine.isOvertime(zoned, i.routine),
                        energy = snap.liveNeeds.energy, arrivedEarly = arrivedEarly,
                    ),
                    Random,
                )
            }
            dialogueUntil = now + if (dialogueText != null) 9_000 else 20_000
        }

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
            context = ctx, snapshot = snap, visual = visual, next = next, dialogue = dialogueText,
            probableMode = probable, locationStatus = status, isWorkDay = i.routine.hasWork && zoned.dayOfWeek in i.routine.days,
            isDayOff = i.dayOff, suggestSavePlace = suggest, canSaveHere = canSaveHere,
        )
    }

    fun setManual(type: UserContextType) = action {
        contextEngine.setManual(type)
        // "Não estou no trabalho" logo após uma chegada automática: a mobilidade aprende a correção.
        runCatching { mobility.onManualContext(type, clock.nowMillis()) }
        _events.send(HomeUiEvent.React(AnimationId.HAPPY))
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
        _events.send(HomeUiEvent.React(AnimationId.SURPRISED))
    }

    fun answerSavePlace(id: Long, save: Boolean) = action {
        contextEngine.answerSavePlace(id, save)
        _events.send(HomeUiEvent.React(AnimationId.HAPPY))
    }

    fun dismissQuestion(id: Long) = action { contextEngine.dismissQuestion(id) }

    fun toggleDayOff() = action {
        val today = clock.today()
        routines.setDayOff(today, !routines.isDayOff(today), clock.nowMillis())
        _events.send(HomeUiEvent.React(AnimationId.HAPPY))
    }

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

    companion object {
        const val TICK_MS = 20_000L
        private const val TAG = "HomeViewModel"
    }
}
