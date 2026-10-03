package com.hoodie.app.presentation.screens.phoneinsights

import com.hoodie.app.core.error.*
import com.hoodie.app.presentation.common.runUiAction
import android.util.Log
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.deviceusage.UsageAccessManager
import com.hoodie.app.core.deviceusage.UsagePermissionState
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.domain.phoneinsights.model.AppUsageEntry
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.domain.phoneinsights.usecase.LoadPhoneInsightsUseCase
import com.hoodie.app.domain.phoneinsights.usecase.SetAppCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PhoneInsightsViewModel @Inject constructor(
    private val loadInsights: LoadPhoneInsightsUseCase,
    private val setCategory: SetAppCategoryUseCase,
    private val access: UsageAccessManager,
    private val settings: SettingsRepository,
    private val clock: ClockProvider,
) : ViewModel() {
    val zone get() = clock.zone()
    val today get() = clock.today()

    private val _state = MutableStateFlow(PhoneInsightsUiState(clock.today(), permission = access.state.value))
    val state: StateFlow<PhoneInsightsUiState> = _state.asStateFlow()
    private val _events = Channel<PhoneInsightsUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.collect { s -> _state.value = _state.value.copy(settings = s.digital, catName = s.catName) }
        }
        // Ligar/desligar a análise em Ajustes recarrega o dia.
        viewModelScope.launch {
            settings.settings.map { it.digital.analysisEnabled }.distinctUntilChanged().drop(1).collect { reload() }
        }
        reload()
    }

    fun selectDate(date: LocalDate) {
        if (date.isAfter(clock.today()) || date == _state.value.date) return
        _state.value = _state.value.copy(date = date, insights = null, selectedApp = null, error = null)
        reload()
    }

    /** Voltou da tela "Acesso ao uso" (ou o app voltou ao primeiro plano). */
    fun onResume() {
        val before = _state.value.permission
        val now = access.refresh()
        _state.value = _state.value.copy(permission = now)
        if (now == UsagePermissionState.GRANTED) action {
            val digital = settings.current().digital
            if (digital.analysisRequested) {
                settings.setDigital(digital.copy(analysisEnabled = true, analysisRequested = false))
            }
        }
        if (before != now || now == UsagePermissionState.GRANTED && _state.value.date == clock.today()) reload()
    }

    fun refresh() = reload()

    fun permissionIntent(): Intent = access.settingsIntent()
    fun appDetailsIntent(): Intent = access.appDetailsIntent()

    fun enableAnalysis() = action {
        val granted = access.isGranted()
        settings.setDigital(settings.current().digital.copy(analysisEnabled = granted, analysisRequested = !granted))
        if (!granted) _events.send(PhoneInsightsUiEvent.OpenUsageSettings)
    }

    fun openApp(entry: AppUsageEntry) { _state.value = _state.value.copy(selectedApp = entry) }

    fun closeApp() { _state.value = _state.value.copy(selectedApp = null) }

    /** Troca a categoria de um app (null = automática) e recalcula o dia. */
    fun changeCategory(packageName: String, category: HoodieAppCategory?) = action {
        setCategory(packageName, category)
        _state.value = _state.value.copy(selectedApp = _state.value.selectedApp?.let { if (it.packageName == packageName && category != null) it.copy(appCategory = category) else it })
        reload(keepSelection = true)
    }

    fun permissionLaunchFailed() { _events.trySend(PhoneInsightsUiEvent.ShowError(UsageAccessError.Unavailable)) }

    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        runUiAction(DatabaseError.WriteFailed, { error, cause ->
            Log.e("PhoneInsightsViewModel", "Failed to update digital settings", cause)
            _events.send(PhoneInsightsUiEvent.ShowError(error))
        }, block)
    }

    private fun reload(keepSelection: Boolean = false) {
        val date = _state.value.date
        loadJob?.cancel()
        _state.value = _state.value.copy(isLoading = true, error = null, permission = access.refresh())
        loadJob = viewModelScope.launch {
            runUiAction(UsageAccessError.ReadFailed, { error, cause ->
                Log.e("PhoneInsightsViewModel", "Failed to load usage insights", cause)
                if (_state.value.date == date) _state.value = _state.value.copy(isLoading = false, error = error)
            }) {
                val insights = loadInsights(date)
                if (_state.value.date == date) {
                    val selected = if (keepSelection) _state.value.selectedApp?.let { s -> insights?.topApps?.firstOrNull { it.packageName == s.packageName } ?: s } else null
                    _state.value = _state.value.copy(insights = insights, isLoading = false, selectedApp = selected)
                }
            }
        }
    }
}
