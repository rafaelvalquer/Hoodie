package com.hoodie.app.presentation.screens.onboarding

import com.hoodie.app.core.error.*
import com.hoodie.app.presentation.common.runUiAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.location.LocationPermissionManager
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.location.CurrentPosition
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.WorkMode
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.data.repository.RoutineRepository
import com.hoodie.app.engine.context.ContextEngine
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.memory.MemoryEngine
import com.hoodie.app.engine.memory.Milestone
import com.hoodie.app.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

/**
 * Fluxo de permissão separado (exigência do Android 10+):
 * PERMISSION (precisa, durante o uso) → HOME (casa) → BACKGROUND ("Permitir o tempo todo" nas configurações) → WORK…
 */
enum class OnboardingStep { WELCOME, NAME, PERMISSION, HOME, HOME_ADDRESS, BACKGROUND, WORK, WORK_ADDRESS, SCHEDULE, DAYS, DONE }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val catName: String = "Hoodie",
    val homeSaved: Boolean = false,
    val workSaved: Boolean = false,
    val busy: Boolean = false,
    val error: AppError? = null,
    val routine: Routine = Routine(workMode = WorkMode.OFFICE),
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val routines: RoutineRepository,
    private val contextEngine: ContextEngine,
    private val location: CurrentPosition,
    private val geofences: GeofenceRegistrar,
    private val permissions: LocationPermissionManager,
    private val memory: MemoryEngine,
    private val hoodie: HoodieEngine,
    private val scheduler: WorkScheduler,
    private val clock: ClockProvider,
    private val log: DebugEventLogger,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    private val _events = Channel<OnboardingUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun openAppSettings() { _events.trySend(OnboardingUiEvent.OpenAppSettings) }
    fun appSettingsFailed() { _state.update { it.copy(error = LocationError.Unavailable) } }

    fun go(step: OnboardingStep) = _state.update { it.copy(step = step, error = null) }

    fun setName(name: String) = _state.update { it.copy(catName = name.take(16)) }

    val permission: StateFlow<LocationPermissionState> = permissions.state

    fun hasLocation() = permissions.hasForeground()

    fun needsBackground() = permissions.current().needsBackgroundStep

    /** Volta das configurações do sistema: revalida a permissão. */
    fun revalidatePermission(): LocationPermissionState = permissions.refresh()

    fun appSettingsIntent() = permissions.appSettingsIntent()

    /** Depois da casa: pede o "tempo todo" só se a pessoa deu localização e ainda falta o segundo plano. */
    fun afterHome() = go(if (needsBackground()) OnboardingStep.BACKGROUND else OnboardingStep.WORK)

    /** "📍 Você está em casa agora? [SIM]" — uma leitura pontual e o lugar vira um geofence. */
    fun markHomeHere() = viewModelScope.launch {
        if (_state.value.busy) return@launch
        _state.update { it.copy(busy = true, error = null) }
        try {
            log.log(DebugEventLogger.Category.ONBOARDING, "HOME_SAVE_STARTED")
            val pos = location.current()
            if (pos == null) {
                _state.update { it.copy(error = LocationError.Unavailable) }
                log.log(DebugEventLogger.Category.PLACE, "HOME_SAVE_FAILED location_unavailable")
                return@launch
            }
            contextEngine.savePlaceHere(PlaceType.HOME, "Casa", pos.first, pos.second)
            _state.update { it.copy(homeSaved = true, error = null) }
            log.log(DebugEventLogger.Category.PLACE, "HOME_SAVE_SUCCESS")
            afterHome()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao salvar Casa no onboarding", e)
            log.log(DebugEventLogger.Category.ONBOARDING, "HOME_SAVE_FAILED ${e.javaClass.simpleName}")
            _state.update { it.copy(error = e.appErrorOr(PlaceError.SaveFailed)) }
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    fun setWorkMode(mode: WorkMode) {
        _state.update { it.copy(routine = it.routine.copy(workMode = mode)) }
        go(if (mode == WorkMode.NONE) OnboardingStep.DONE else OnboardingStep.SCHEDULE)
    }

    /** Casa escolhida pelo endereço no mapa. */
    fun onHomeAddressSaved() {
        _state.update { it.copy(homeSaved = true) }
        afterHome()
    }

    /** "Sim, trabalho fora" + buscar o endereço do trabalho agora. */
    fun pickWorkAddress() {
        _state.update { it.copy(routine = it.routine.copy(workMode = WorkMode.OFFICE)) }
        go(OnboardingStep.WORK_ADDRESS)
    }

    fun onWorkAddressSaved() = _state.update { it.copy(workSaved = true, step = OnboardingStep.SCHEDULE) }

    fun updateRoutine(transform: (Routine) -> Routine) = _state.update { it.copy(routine = transform(it.routine)) }

    fun toggleDay(day: DayOfWeek) = updateRoutine { r -> r.copy(days = if (day in r.days) r.days - day else r.days + day) }

    fun finish() = viewModelScope.launch {
        if (_state.value.busy) return@launch
        _state.update { it.copy(busy = true, error = null) }
        try {
            runUiAction(DatabaseError.WriteFailed, { error, cause ->
                Log.e(TAG, "Onboarding completion failed", cause)
                _state.update { it.copy(error = error) }
            }) {
                val s = _state.value
                val now = clock.nowMillis()
                routines.save(s.routine, now)
                settings.setCatName(s.catName)
                memory.unlock(Milestone.FIRST_DAY, now)
                geofences.registerAll()
                contextEngine.applyRoutineFallbackIfNeeded()
                hoodie.resolve()
                scheduler.schedulePeriodic()
                // Root navigation observes this flag; publish it after setup succeeds.
                settings.completeOnboarding(s.catName, now)
            }
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    private companion object {
        const val TAG = "OnboardingViewModel"
    }
}
