package com.hoodie.app.presentation.screens.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.geofence.GeofenceManager
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.location.AddressResult
import com.hoodie.app.core.location.AddressSearch
import com.hoodie.app.core.location.CurrentPosition
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.MapPicker
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlacePickerState(
    val type: PlaceType = PlaceType.HOME,
    val editingId: Long? = null,
    val name: String = "",
    val query: String = "",
    val results: List<AddressResult> = emptyList(),
    val searching: Boolean = false,
    val latitude: Double = DEFAULT_LAT,
    val longitude: Double = DEFAULT_LNG,
    /** Incrementa quando o ponto vem de fora do mapa (busca/GPS) para o mapa recentralizar. */
    val recenterKey: Int = 0,
    val hasPoint: Boolean = false,
    val address: String? = null,
    val radius: Float = GeofenceManager.DEFAULT_RADIUS,
    val message: String? = null,
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    companion object {
        // Centro inicial neutro (São Paulo) até haver busca, GPS ou um lugar em edição.
        const val DEFAULT_LAT = -23.5505
        const val DEFAULT_LNG = -46.6333
    }
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
    private var initialized = false
    private var reverseJob: Job? = null

    fun init(type: PlaceType, placeId: Long?) {
        if (initialized) return
        initialized = true
        _state.update { it.copy(type = type, editingId = placeId, name = type.label) }
        viewModelScope.launch {
            val existing = placeId?.let { places.byId(it) }
            if (existing != null) {
                _state.update {
                    it.copy(type = existing.type, name = existing.name, radius = existing.radiusMeters,
                        latitude = existing.latitude, longitude = existing.longitude, hasPoint = true, recenterKey = it.recenterKey + 1)
                }
                lookupAddress(existing.latitude, existing.longitude)
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q, message = null) }
    fun setName(n: String) = _state.update { it.copy(name = n.take(30)) }
    fun setRadius(r: Float) = _state.update { it.copy(radius = r) }
    fun setType(t: PlaceType) = _state.update { s ->
        // Se o nome ainda era o padrão do tipo antigo, acompanha o novo tipo.
        s.copy(type = t, name = if (s.name.isBlank() || s.name == s.type.label) t.label else s.name)
    }

    fun search() = viewModelScope.launch {
        val q = _state.value.query
        if (q.isBlank()) return@launch
        _state.update { it.copy(searching = true, results = emptyList(), message = null) }
        val results = search.search(q)
        _state.update {
            it.copy(
                searching = false, results = results,
                message = if (results.isEmpty()) "Não encontrei esse endereço. Confira a internet ou tente com rua, número e cidade." else null,
            )
        }
        if (results.size == 1) choose(results.first())
    }

    fun choose(r: AddressResult) = _state.update {
        it.copy(latitude = r.latitude, longitude = r.longitude, address = r.label, results = emptyList(), hasPoint = true, recenterKey = it.recenterKey + 1)
    }

    /** O usuário arrastou o mapa: o centro é o novo ponto. */
    fun onCenterChanged(lat: Double, lng: Double) {
        val s = _state.value
        if (Math.abs(lat - s.latitude) < 1e-6 && Math.abs(lng - s.longitude) < 1e-6) return
        _state.update { it.copy(latitude = lat, longitude = lng, hasPoint = true) }
        lookupAddress(lat, lng)
    }

    fun useMyLocation() = viewModelScope.launch {
        _state.update { it.copy(searching = true, message = null) }
        val pos = position.current()
        if (pos == null) {
            _state.update { it.copy(searching = false, message = "Não consegui sua localização agora.") }
            return@launch
        }
        _state.update { it.copy(searching = false, latitude = pos.first, longitude = pos.second, hasPoint = true, recenterKey = it.recenterKey + 1) }
        lookupAddress(pos.first, pos.second)
    }

    private fun lookupAddress(lat: Double, lng: Double) {
        reverseJob?.cancel()
        reverseJob = viewModelScope.launch {
            val label = search.reverse(lat, lng)
            _state.update { it.copy(address = label) }
        }
    }

    fun save() = viewModelScope.launch {
        val s = _state.value
        if (!s.hasPoint || s.saving) return@launch
        _state.update { it.copy(saving = true) }
        val name = s.name.ifBlank { s.type.label }
        val existing = s.editingId?.let { places.byId(it) }
        if (existing != null) {
            places.update(existing.copy(name = name, type = s.type, latitude = s.latitude, longitude = s.longitude, radiusMeters = s.radius))
        } else {
            places.add(name, s.type, s.latitude, s.longitude, s.radius, clock.nowMillis())
        }
        geofences.registerAll()
        _state.update { it.copy(saving = false, done = true) }
    }
}

/**
 * Conteúdo reutilizável (onboarding, tela Lugares, Home). Ocupa a tela toda:
 * o mapa não fica dentro de scroll para não brigar com o gesto de arrastar.
 */
@Composable
fun PlacePickerContent(
    type: PlaceType,
    placeId: Long?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    allowTypeChange: Boolean = true,
    vm: PlacePickerViewModel = hiltViewModel(key = "picker_${type.name}_${placeId ?: "new"}"),
) {
    LaunchedEffect(type, placeId) { vm.init(type, placeId) }
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.done) { if (s.done) onDone() }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (s.editingId != null) "📍 Mudar local" else "${s.type.emoji} Onde fica: ${s.type.label}?",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            )
            Text("✕", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(4.dp).clickable(onClick = onCancel))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                s.query, vm::setQuery, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text("Rua, número, cidade") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            )
            PixelButton("Buscar", { vm.search() }, enabled = !s.searching && s.query.isNotBlank())
        }
        if (s.searching) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        s.message?.let { Text(it, color = HoodieColors.Coral, style = MaterialTheme.typography.bodySmall) }
        if (s.results.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.results) { r ->
                    PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, onClick = { vm.choose(r) }) {
                        Text("📍 ${r.label}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        MapPicker(s.latitude, s.longitude, s.radius, s.recenterKey, vm::onCenterChanged, Modifier.fillMaxWidth().weight(1f))
        Text(
            s.address?.let { "📍 $it" } ?: if (s.hasPoint) "📍 %.5f, %.5f".format(s.latitude, s.longitude) else "Busque o endereço ou arraste o mapa até o pino ficar no lugar certo.",
            style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted,
        )
        Text("Usar minha localização", color = HoodieColors.Blue, modifier = Modifier.clickable { vm.useMyLocation() }.padding(vertical = 2.dp))

        if (allowTypeChange) {
            val types = PlaceType.entries
            ChipRow(types.map { "${it.emoji} ${it.label}" }, types.indexOf(s.type), { vm.setType(types[it]) })
        }
        OutlinedTextField(s.name, vm::setName, singleLine = true, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
        SectionLabel("Raio: ${s.radius.toInt()} m")
        Slider(s.radius, vm::setRadius, valueRange = 75f..400f)
        Text(
            "🔒 O endereço é enviado ao serviço de mapas do Android só para esta busca; o mapa vem do OpenStreetMap. O local é salvo cifrado, só neste aparelho.",
            style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted,
        )
        PixelButton(
            if (s.saving) "Salvando..." else "Salvar como ${s.type.label}",
            { vm.save() }, Modifier.fillMaxWidth(), color = HoodieColors.Gold, enabled = s.hasPoint && !s.saving,
        )
    }
}

/** Tela de navegação (rota place_picker). */
@Composable
fun PlacePickerScreen(type: PlaceType, placeId: Long?, onBack: () -> Unit) {
    PlacePickerContent(type, placeId, onDone = onBack, onCancel = onBack, modifier = Modifier.statusBarsPadding())
}
