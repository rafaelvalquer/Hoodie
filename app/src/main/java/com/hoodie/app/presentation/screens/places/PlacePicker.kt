package com.hoodie.app.presentation.screens.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.geofence.GeofenceManager
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.location.AddressResult
import com.hoodie.app.core.location.AddressSearch
import com.hoodie.app.core.location.CurrentPosition
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.error.*
import android.util.Log
import com.hoodie.app.data.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlaceLoadState {
    data object Idle : PlaceLoadState
    data object Loading : PlaceLoadState
    data object Ready : PlaceLoadState
    data object NotFound : PlaceLoadState
    data class Error(val cause: Throwable, val error: AppError = DatabaseError.ReadFailed) : PlaceLoadState
}

data class PlacePickerState(
    val loadState: PlaceLoadState = PlaceLoadState.Idle,
    val type: PlaceType = PlaceType.HOME,
    val editingId: Long? = null,
    val name: String = "",
    val query: String = "",
    val results: List<AddressResult> = emptyList(),
    val searching: Boolean = false,
    /** "Minha localização" em andamento (separado da busca: o loading aparece no botão do mapa). */
    val locating: Boolean = false,
    val latitude: Double = DEFAULT_LAT,
    val longitude: Double = DEFAULT_LNG,
    /** Incrementa quando o ponto vem de fora do mapa (busca/GPS) para o mapa recentralizar. */
    val recenterKey: Int = 0,
    val hasPoint: Boolean = false,
    val address: String? = null,
    val radius: Float = GeofenceManager.DEFAULT_RADIUS,
    /** Erros perto de onde nasceram: busca, mapa/localização e salvar. */
    val searchError: AppError? = null,
    val locationError: AppError? = null,
    val saveError: AppError? = null,
    val saving: Boolean = false,
) {
    val editing: Boolean get() = editingId != null
    val canSave: Boolean get() = hasPoint && !saving && (!editing || loadState == PlaceLoadState.Ready)

    companion object {
        // Centro inicial neutro (São Paulo) até haver busca, GPS ou um lugar em edição.
        const val DEFAULT_LAT = -23.5505
        const val DEFAULT_LNG = -46.6333
        const val MIN_RADIUS = 75f
        const val MAX_RADIUS = 400f
        val QUICK_RADII = listOf(100f, 150f, 200f, 300f)
        val SEARCH_NOT_FOUND = PlaceError.AddressNotFound
        val LOCATION_FAILED = LocationError.Unavailable
        val SAVE_FAILED = PlaceError.SaveFailed
        val GEOFENCE_FAILED = PlaceError.GeofenceRegistrationFailed
    }
}

/** Ações pontuais (não ficam no estado): fechar a tela, avisar algo que sobrevive à navegação. */
sealed interface PlacePickerUiEvent {
    data object Saved : PlacePickerUiEvent
    data object RetryGeofence : PlacePickerUiEvent
}

/**
 * Escolher um lugar por endereço + mapa. Diferente de "estou aqui agora", não
 * muda o contexto atual: a pessoa pode estar longe. Se estiver dentro do lugar,
 * o próprio geofence (INITIAL_TRIGGER_ENTER) avisa.
 */
@HiltViewModel
class PlacePickerViewModel @Inject constructor(
    private val search: AddressSearch,
    private val places: PlaceRepository,
    private val geofences: GeofenceRegistrar,
    private val position: CurrentPosition,
    private val clock: ClockProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(PlacePickerState())
    val state: StateFlow<PlacePickerState> = _state.asStateFlow()
    private val _events = Channel<PlacePickerUiEvent>(Channel.BUFFERED)
    val events: Flow<PlacePickerUiEvent> = _events.receiveAsFlow()
    private var initialized = false
    private var reverseJob: Job? = null

    fun init(type: PlaceType, placeId: Long?) {
        if (initialized) return
        initialized = true
        _state.update { it.copy(type = type, editingId = placeId, name = type.label) }
        if (placeId != null) reloadPlace()
    }

    fun reloadPlace() = viewModelScope.launch {
        val placeId = _state.value.editingId ?: return@launch
        _state.update { it.copy(loadState = PlaceLoadState.Loading, hasPoint = false, saveError = null) }
        try {
            val existing = places.byId(placeId)
            if (existing == null) {
                _state.update { it.copy(loadState = PlaceLoadState.NotFound) }
            } else {
                _state.update {
                    it.copy(loadState = PlaceLoadState.Ready, type = existing.type, name = existing.name, radius = existing.radiusMeters,
                        latitude = existing.latitude, longitude = existing.longitude, hasPoint = true, recenterKey = it.recenterKey + 1)
                }
                lookupAddress(existing.latitude, existing.longitude)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("PlacePickerViewModel", "Failed to load place", e)
            _state.update { it.copy(loadState = PlaceLoadState.Error(e, e.appErrorOr(DatabaseError.ReadFailed))) }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q, searchError = null) }
    fun setName(n: String) = _state.update { it.copy(name = n.take(30), saveError = null) }
    fun setRadius(r: Float) = _state.update { it.copy(radius = r.coerceIn(PlacePickerState.MIN_RADIUS, PlacePickerState.MAX_RADIUS)) }
    fun setType(t: PlaceType) = _state.update { s ->
        // Se o nome ainda era o padrão do tipo antigo, acompanha o novo tipo.
        s.copy(type = t, name = if (s.name.isBlank() || s.name == s.type.label) t.label else s.name)
    }

    fun search() = viewModelScope.launch {
        val q = _state.value.query
        if (q.isBlank() || _state.value.searching) return@launch
        _state.update { it.copy(searching = true, results = emptyList(), searchError = null) }
        val results = try { search.search(q) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        _state.update {
            it.copy(searching = false, results = results, searchError = if (results.isEmpty()) PlacePickerState.SEARCH_NOT_FOUND else null)
        }
        if (results.size == 1) choose(results.first())
    }

    fun choose(r: AddressResult) = _state.update {
        it.copy(latitude = r.latitude, longitude = r.longitude, address = r.label, results = emptyList(), hasPoint = true,
            recenterKey = it.recenterKey + 1, searchError = null, locationError = null)
    }

    suspend fun retryGeofences(): Boolean = geofences.registerAll().ok

    fun dismissResults() = _state.update { it.copy(results = emptyList()) }

    /** O usuário arrastou o mapa: o centro é o novo ponto. */
    fun onCenterChanged(lat: Double, lng: Double) {
        val s = _state.value
        if (Math.abs(lat - s.latitude) < 1e-6 && Math.abs(lng - s.longitude) < 1e-6) return
        _state.update { it.copy(latitude = lat, longitude = lng, hasPoint = true, locationError = null) }
        lookupAddress(lat, lng)
    }

    fun useMyLocation() = viewModelScope.launch {
        if (_state.value.locating) return@launch
        _state.update { it.copy(locating = true, locationError = null) }
        val pos = try { position.current() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (pos == null) {
            _state.update { it.copy(locating = false, locationError = PlacePickerState.LOCATION_FAILED) }
            return@launch
        }
        _state.update { it.copy(locating = false, latitude = pos.first, longitude = pos.second, hasPoint = true, recenterKey = it.recenterKey + 1) }
        lookupAddress(pos.first, pos.second)
    }

    private fun lookupAddress(lat: Double, lng: Double) {
        reverseJob?.cancel()
        reverseJob = viewModelScope.launch {
            val label = try { search.reverse(lat, lng) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            _state.update { it.copy(address = label) }
        }
    }

    /**
     * Salvar o lugar e registrar o geofence são passos separados: se o banco falhar, a tela
     * fica aberta com o erro; se só o geofence falhar, o lugar continua salvo e vira um aviso.
     */
    fun save() = viewModelScope.launch {
        val s = _state.value
        if (!s.canSave) return@launch
        _state.update { it.copy(saving = true, saveError = null) }
        try {
            val name = s.name.trim().ifBlank { s.type.label }
            if (s.editingId != null) {
                val existing = places.byId(s.editingId) ?: throw PlaceException.NotFound(s.editingId)
                places.update(existing.copy(name = name, type = s.type, latitude = s.latitude, longitude = s.longitude, radiusMeters = s.radius))
            } else {
                places.add(name, s.type, s.latitude, s.longitude, s.radius, clock.nowMillis())
            }
        } catch (e: CancellationException) {
            _state.update { it.copy(saving = false) }
            throw e
        } catch (e: PlaceException.NotFound) {
            _state.update { it.copy(saving = false, hasPoint = false, loadState = PlaceLoadState.NotFound) }
            return@launch
        } catch (error: Exception) {
            Log.e("PlacePickerViewModel", "Failed to save place", error)
            _state.update { it.copy(saving = false, saveError = error.appErrorOr(PlaceError.SaveFailed)) }
            return@launch
        }
        val geofenceOk = try { geofences.registerAll().ok } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
        _state.update { it.copy(saving = false) }
        if (!geofenceOk) _events.send(PlacePickerUiEvent.RetryGeofence)
        _events.send(PlacePickerUiEvent.Saved)
    }
}
