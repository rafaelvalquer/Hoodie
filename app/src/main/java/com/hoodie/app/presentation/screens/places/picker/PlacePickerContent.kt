package com.hoodie.app.presentation.screens.places.picker

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.core.location.AddressResult
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.components.MapPicker
import com.hoodie.app.presentation.screens.places.PlacePickerState
import com.hoodie.app.presentation.screens.places.PlacePickerUiEvent
import com.hoodie.app.presentation.screens.places.PlacePickerViewModel
import com.hoodie.app.presentation.theme.HoodieColors

/** Tags para testes de UI (Compose). */
object PlacePickerTags {
    const val ROOT = "picker_root"
    const val HEADER = "picker_header"
    const val CLOSE = "picker_close"
    const val SEARCH = "picker_search"
    const val SEARCH_LOADING = "picker_search_loading"
    const val SEARCH_ERROR = "picker_search_error"
    const val RESULTS = "picker_results"
    const val MAP = "picker_map"
    const val LOCATION_ERROR = "picker_location_error"
    const val DETAILS = "picker_details"
    const val ADDRESS = "picker_address"
    const val ADDRESS_TEXT = "picker_address_text"
    const val TYPE_SUMMARY = "picker_type_summary"
    const val TYPE_CHANGE = "picker_type_change"
    const val TYPE_GRID = "picker_type_grid"
    const val TYPE_SHEET = "picker_type_sheet"
    const val NAME = "picker_name"
    const val RADIUS_VALUE = "picker_radius_value"
    const val RADIUS_SLIDER = "picker_radius_slider"
    const val PRIVACY = "picker_privacy"
    const val PRIVACY_MORE = "picker_privacy_more"
    const val PRIVACY_SHEET = "picker_privacy_sheet"
    const val SAVE = "picker_save"
    const val SAVE_ERROR = "picker_save_error"
    fun typeCell(t: PlaceType) = "picker_type_${t.name}"
    fun sheetType(t: PlaceType) = "picker_sheet_type_${t.name}"
    fun quickRadius(m: Int) = "picker_radius_$m"
}

/** Medidas que dependem da altura disponível (não da tela física: funciona dentro do Scaffold). */
data class PlacePickerDimensions(val mapHeight: Dp, val horizontalPadding: Dp, val sectionSpacing: Dp) {
    companion object {
        val MAP_MIN_HEIGHT = 200.dp
        val MAP_MAX_HEIGHT = 300.dp

        /** Abaixo disso a tela está "encolhida" (teclado aberto num aparelho pequeno). */
        val SHORT_HEIGHT = 560.dp
        /** Header + busca + barra de salvar + o mínimo de detalhes que precisa caber. */
        private val FIXED_WITHOUT_MAP = 300.dp
        private val MAP_HIDE_BELOW = 96.dp

        /**
         * < 700 dp → mapa 210 · 700–850 dp → 250 · > 850 dp → 280. Com fonte grande o
         * mapa cede um pouco para os detalhes; nunca abaixo de [MAP_MIN_HEIGHT].
         *
         * Com o teclado aberto (altura < [SHORT_HEIGHT]) quem cede é o mapa: ele encolhe
         * para o que sobrar e some enquanto se digita se não couber — o Salvar nunca sai da tela.
         */
        fun forHeight(availableHeight: Dp, fontScale: Float = 1f): PlacePickerDimensions {
            if (availableHeight < SHORT_HEIGHT) {
                val room = (availableHeight - FIXED_WITHOUT_MAP).coerceIn(0.dp, 210.dp)
                return PlacePickerDimensions(if (room < MAP_HIDE_BELOW) 0.dp else room, horizontalPadding = 16.dp, sectionSpacing = 8.dp)
            }
            val base = when {
                availableHeight < 700.dp -> 210.dp
                availableHeight <= 850.dp -> 250.dp
                else -> 280.dp
            }
            val map = (if (fontScale >= 1.3f) base - 20.dp else base).coerceIn(MAP_MIN_HEIGHT, MAP_MAX_HEIGHT)
            val compact = availableHeight < 700.dp
            return PlacePickerDimensions(map, horizontalPadding = 16.dp, sectionSpacing = if (compact) 10.dp else 14.dp)
        }
    }
}

/** Ações da tela (o ViewModel no app, lambdas nos testes de UI). */
data class PlacePickerActions(
    val onClose: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onChooseResult: (AddressResult) -> Unit = {},
    val onCenterChanged: (Double, Double) -> Unit = { _, _ -> },
    val onMyLocation: () -> Unit = {},
    val onTypeChange: (PlaceType) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onRadiusChange: (Float) -> Unit = {},
    val onSave: () -> Unit = {},
)

/**
 *     HEADER    (52 dp)
 *     SEARCH    (campo; resultados por cima do mapa)
 *     MAP       (altura própria — nunca espremido)
 *     DETAILS   (LazyColumn com weight(1f): rola sozinha)
 *     SAVE BAR  (fixa)
 *
 * O weight fica nos detalhes, não no mapa. [map] é substituível nos testes de UI.
 */
@Composable
fun PlacePickerLayout(
    state: PlacePickerState,
    actions: PlacePickerActions,
    modifier: Modifier = Modifier,
    allowTypeChange: Boolean = true,
    map: @Composable (Modifier) -> Unit = { m ->
        MapPicker(state.latitude, state.longitude, state.radius, state.recenterKey, actions.onCenterChanged, m,
            onMyLocation = actions.onMyLocation, loadingLocation = state.locating)
    },
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag(PlacePickerTags.ROOT)) {
        val dims = PlacePickerDimensions.forHeight(maxHeight, LocalDensity.current.fontScale)
        val pad = dims.horizontalPadding
        Column(Modifier.fillMaxSize()) {
            PlacePickerHeader(state.editing, state.type, actions.onClose, pad)
            PlaceSearchBar(
                state.query, state.searching, actions.onQueryChange, actions.onSearch, state.searchError,
                Modifier.padding(horizontal = pad).padding(bottom = 8.dp),
            )
            Box(Modifier.fillMaxWidth().height(dims.mapHeight).testTag(PlacePickerTags.MAP)) {
                map(Modifier.fillMaxSize())
                PlaceSearchResults(state.results, actions.onChooseResult, Modifier.padding(horizontal = pad).fillMaxWidth().align(Alignment.TopCenter))
            }
            state.locationError?.let {
                Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Coral, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = pad, vertical = 4.dp).testTag(PlacePickerTags.LOCATION_ERROR))
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag(PlacePickerTags.DETAILS),
                contentPadding = PaddingValues(start = pad, end = pad, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing),
            ) {
                item("address") { SelectedAddressCard(state.address, state.latitude, state.longitude, state.hasPoint) }
                val mode = placeTypeSelectorMode(allowTypeChange, state.editing)
                if (mode != PlaceTypeSelectorMode.HIDDEN) item("type") { PlaceTypeSelector(state.type, mode, actions.onTypeChange) }
                item("name") { PlaceNameField(state.name, actions.onNameChange) }
                item("radius") { PlaceRadiusControl(state.radius, actions.onRadiusChange) }
                item("privacy") { PlacePrivacyInfo() }
            }
            PlaceSaveBar(state.type, state.canSave, state.saving, actions.onSave, state.saveError, pad)
        }
    }
}

/**
 * Seletor de lugar ligado ao ViewModel. Reutilizado na rota place_picker (Lugares, Home)
 * e no onboarding (HOME_ADDRESS / WORK_ADDRESS, com [allowTypeChange] = false).
 * Quem chama cuida dos insets (Scaffold ou safeDrawingPadding); aqui só o teclado.
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
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                PlacePickerUiEvent.Saved -> done()
                // Aviso que precisa sobreviver ao fechamento da tela (ex.: geofence não ativado).
                is PlacePickerUiEvent.ShowMessage -> Toast.makeText(context, e.text, Toast.LENGTH_LONG).show()
            }
        }
    }
    PlacePickerLayout(
        s,
        PlacePickerActions(
            onClose = onCancel,
            onQueryChange = vm::setQuery,
            onSearch = { vm.search() },
            onChooseResult = vm::choose,
            onCenterChanged = vm::onCenterChanged,
            onMyLocation = { vm.useMyLocation() },
            onTypeChange = vm::setType,
            onNameChange = vm::setName,
            onRadiusChange = vm::setRadius,
            onSave = { vm.save() },
        ),
        modifier,
        allowTypeChange,
    )
}
