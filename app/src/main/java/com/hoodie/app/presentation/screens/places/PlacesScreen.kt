package com.hoodie.app.presentation.screens.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.geofence.GeofenceManager
import com.hoodie.app.core.location.LocationProvider
import com.hoodie.app.core.location.LocationStatus
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.util.Geo
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.engine.context.ContextEngine
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.navigation.Routes
import com.hoodie.app.pixel.icons.IconLabel
import com.hoodie.app.pixel.icons.PixelIcons
import com.hoodie.app.presentation.theme.HoodieColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val places: PlaceRepository,
    private val geofences: GeofenceManager,
    private val location: LocationProvider,
    private val contextEngine: ContextEngine,
    private val clock: ClockProvider,
) : ViewModel() {
    val list = places.places.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    fun status(): LocationStatus = location.status()

    fun addHere(type: PlaceType, name: String) = viewModelScope.launch {
        busy.value = true
        val pos = location.current()
        if (pos == null) message.value = "Não consegui sua localização agora."
        else contextEngine.savePlaceHere(type, name.ifBlank { type.label }, pos.first, pos.second)
        busy.value = false
    }

    /** Alternativa offline sem mapa: colar "lat, lng" copiado de qualquer app de mapas. */
    fun addManual(type: PlaceType, name: String, coords: String) = viewModelScope.launch {
        val parsed = Geo.parse(coords)
        if (parsed == null) { message.value = "Coordenadas inválidas. Use o formato: -23.5505, -46.6333"; return@launch }
        places.add(name.ifBlank { type.label }, type, parsed.first, parsed.second, GeofenceManager.DEFAULT_RADIUS, clock.nowMillis())
        geofences.registerAll()
    }

    fun update(place: Place) = viewModelScope.launch { places.update(place); geofences.registerAll() }

    fun delete(id: Long) = viewModelScope.launch { places.delete(id); geofences.registerAll() }
}

@Composable
fun PlacesScreen(onOpen: (String) -> Unit, vm: PlacesViewModel = hiltViewModel()) {
    val list by vm.list.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Place?>(null) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("LUGARES", style = MaterialTheme.typography.headlineSmall)
        PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
            Text("🔒 Coordenadas cifradas com chave do Android Keystore e guardadas só neste aparelho. Nenhum trajeto é registrado — apenas entradas e saídas destes lugares.", color = HoodieColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (vm.status() != LocationStatus.OK) {
            Text("⚠️ Localização ${if (vm.status() == LocationStatus.NO_BACKGROUND) "só com o app aberto" else "indisponível"}: os geofences podem não disparar.", color = HoodieColors.Coral)
        }
        message?.let { Text(it, color = HoodieColors.Coral) }
        if (list.isEmpty()) PixelPanel(Modifier.fillMaxWidth()) { Text("Nenhum lugar ainda. Comece pela Casa.", color = HoodieColors.Muted) }
        list.forEach { p ->
            PixelPanel(Modifier.fillMaxWidth(), onClick = { editing = p }) {
                Row {
                    IconLabel(PixelIcons.of(p.type), p.name, style = MaterialTheme.typography.titleMedium, iconSize = 20.dp, modifier = Modifier.weight(1f))
                    Text("${p.radiusMeters.toInt()} m", color = HoodieColors.Muted)
                }
                Text("${p.type.label} · ${p.confirmationCount} visitas", color = HoodieColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        PixelButton(if (busy) "Localizando..." else "Adicionar lugar", { adding = true }, Modifier.fillMaxWidth(), enabled = !busy)
    }

    if (adding) AddPlaceDialog(
        onDismiss = { adding = false },
        onHere = { t, n -> vm.addHere(t, n); adding = false },
        onManual = { t, n, c -> vm.addManual(t, n, c); adding = false },
        onMap = { t -> adding = false; onOpen(Routes.placePicker(t)) },
    )
    editing?.let { p ->
        EditPlaceDialog(
            p,
            onDismiss = { editing = null },
            onSave = { vm.update(it); editing = null },
            onDelete = { vm.delete(p.id); editing = null },
            onMap = { editing = null; onOpen(Routes.placePicker(p.type, p.id)) },
        )
    }
}

private val placeTypes = PlaceType.physicalPlaceOptions

@Composable
private fun PlaceTypeChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        placeTypes.forEachIndexed { index, placeType ->
            androidx.compose.material3.FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { IconLabel(PixelIcons.of(placeType), placeType.label, iconSize = 18.dp) },
            )
        }
    }
}

@Composable
internal fun AddPlaceDialog(onDismiss: () -> Unit, onHere: (PlaceType, String) -> Unit, onManual: (PlaceType, String, String) -> Unit, onMap: (PlaceType) -> Unit) {
    var type by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var coords by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo lugar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaceTypeChips(type, { type = it })
                PixelButton("Buscar endereço no mapa", { onMap(placeTypes[type]) }, Modifier.fillMaxWidth(), leadingIcon = PixelIcons.MAP)
                OutlinedTextField(name, { name = it }, label = { Text("Nome (opcional)") }, singleLine = true)
                if (manual) OutlinedTextField(coords, { coords = it }, label = { Text("lat, lng") }, singleLine = true)
                Text(
                    if (manual) "Usar minha localização atual" else "Ou digitar coordenadas",
                    color = HoodieColors.Blue, modifier = Modifier.clickable { manual = !manual }.padding(4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (manual) onManual(placeTypes[type], name, coords) else onHere(placeTypes[type], name) }) {
                Text(if (manual) "Salvar" else "Estou aqui agora")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun AddPlaceDialogContent() {
    var type by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var coords by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Novo lugar", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PlaceTypeChips(type, { type = it })
            PixelButton("Buscar endereço no mapa", {}, Modifier.fillMaxWidth(), leadingIcon = PixelIcons.MAP)
            OutlinedTextField(name, { name = it }, label = { Text("Nome (opcional)") }, singleLine = true)
            if (manual) OutlinedTextField(coords, { coords = it }, label = { Text("lat, lng") }, singleLine = true)
            Text(
                if (manual) "Usar minha localização atual" else "Ou digitar coordenadas",
                color = HoodieColors.Blue, modifier = Modifier.clickable { manual = !manual }.padding(4.dp),
            )
        }
    }
}

@Composable
private fun EditPlaceDialog(place: Place, onDismiss: () -> Unit, onSave: (Place) -> Unit, onDelete: () -> Unit, onMap: () -> Unit) {
    var name by remember { mutableStateOf(place.name) }
    var radius by remember { mutableFloatStateOf(place.radiusMeters) }
    var type by remember { mutableIntStateOf(placeTypes.indexOf(place.type)) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { IconLabel(PixelIcons.of(place.type), place.name, iconSize = 22.dp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                PlaceTypeChips(type, { type = it })
                SectionLabel("Raio: ${radius.toInt()} m")
                Slider(radius, { radius = it }, valueRange = 75f..400f)
                PixelButton("Mudar local no mapa", onMap, Modifier.fillMaxWidth(), color = HoodieColors.Hood, leadingIcon = PixelIcons.MAP)
                Spacer(Modifier.padding(2.dp))
                Text(if (confirmDelete) "Toque de novo para apagar" else "Apagar lugar", color = HoodieColors.Coral,
                    modifier = Modifier.clickable { if (confirmDelete) onDelete() else confirmDelete = true }.padding(4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(place.copy(name = name.ifBlank { place.name }, radiusMeters = radius, type = placeTypes[type])) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
