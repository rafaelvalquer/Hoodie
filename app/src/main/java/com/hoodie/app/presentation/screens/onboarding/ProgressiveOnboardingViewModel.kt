package com.hoodie.app.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.memory.MemoryEngine
import com.hoodie.app.engine.memory.Milestone
import com.hoodie.app.engine.setup.ProgressiveOnboardingCoordinator
import com.hoodie.app.engine.setup.ProgressiveStep
import com.hoodie.app.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressiveOnboardingState(
    val step: ProgressiveStep = ProgressiveStep.WELCOME,
    val catName: String = "Hoodie",
    val restored: Boolean = false,
    val busy: Boolean = false,
    val showDemo: Boolean = false,
)

/** Boas-vindas → nome → acompanhamento (agora ou depois). Entra na Home sem permissões nem rotina. */
@HiltViewModel
class ProgressiveOnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val geofences: GeofenceRegistrar,
    private val memory: MemoryEngine,
    private val hoodie: HoodieEngine,
    private val scheduler: WorkScheduler,
    private val clock: ClockProvider,
) : ViewModel() {
    private val flow = ProgressiveOnboardingCoordinator(settings, clock)
    private val _state = MutableStateFlow(ProgressiveOnboardingState())
    val state: StateFlow<ProgressiveOnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val draft = flow.restore()
            _state.update { it.copy(step = draft.step, catName = draft.name, restored = true) }
        }
    }

    fun go(step: ProgressiveStep) {
        _state.update { it.copy(step = step) }
        viewModelScope.launch { flow.saveDraft(step, _state.value.catName) }
    }

    fun setName(name: String) {
        _state.update { it.copy(catName = name.take(16)) }
        viewModelScope.launch { flow.saveDraft(_state.value.step, _state.value.catName) }
    }

    fun showDemo(show: Boolean) = _state.update { it.copy(showDemo = show) }

    /** Entra na Home. Etapas opcionais falham em silêncio; a conclusão só é publicada no fim. */
    fun finish() = viewModelScope.launch {
        if (_state.value.busy) return@launch
        _state.update { it.copy(busy = true) }
        try {
            val name = _state.value.catName
            flow.complete(name, listOf(
                { memory.unlock(Milestone.FIRST_DAY, clock.nowMillis()) },
                { geofences.registerAll() },
                { hoodie.resolve() },
                { scheduler.schedulePeriodic() },
            ))
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }
}