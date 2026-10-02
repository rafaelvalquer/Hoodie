package com.hoodie.app.presentation.screens.places.picker

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.R
import com.hoodie.app.core.location.AddressResult
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.screens.places.PlaceLoadState
import com.hoodie.app.presentation.screens.places.PlacePickerState
import com.hoodie.app.presentation.screens.places.PlacePickerUiEvent
import com.hoodie.app.presentation.screens.places.PlacePickerViewModel
import com.hoodie.app.presentation.theme.HoodieColors

/** Tags para testes de UI (Compose). */
object PlacePickerTags {
    const val ROOT = "picker_root"
    const val HEADER = "picker_header"
    const val CLOSE = "picker_close"
    const val SEARCH_SECTION = "picker_search_section"
    const val SEARCH = "picker_search"
    const val SEARCH_ACTION = "picker_search_action"
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
    const val RADIUS = "picker_radius"
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

/** Qual campo de texto está em edição (estado só visual: não vai para o ViewModel). */
enum class PlacePickerFocus { NONE, SEARCH, NAME }

/** Novo foco quando o campo [field] ganha/perde o foco. Perder o foco de outro campo não apaga o atual. */
fun focusAfter(current: PlacePickerFocus, field: PlacePickerFocus, focused: Boolean): PlacePickerFocus = when {
    focused -> field
    current == field -> PlacePickerFocus.NONE
    else -> current
}

/** Medidas que dependem da altura disponível (não da tela física: funciona dentro do Scaffold). */
data class PlacePickerDimensions(val mapHeight: Dp, val horizontalPadding: Dp, val sectionSpacing: Dp) {
    companion object {
        /** O formulário é mais importante que o mapa: ele é uma ferramenta, não o fundo da tela. */
        val MAP_MIN_HEIGHT = 180.dp
        val MAP_MAX_HEIGHT = 260.dp
        /** Altura máxima do mapa enquanto se digita (busca ou nome) / com teclado aberto. */
        val MAP_TYPING_MAX = 160.dp

        /** Abaixo disso a tela está "encolhida" (teclado aberto num aparelho pequeno). */
        val SHORT_HEIGHT = 560.dp
        /** Abaixo disso, digitando, o mapa some para o campo e o Salvar caberem. */
        val TYPING_HIDE_BELOW = 700.dp
        /** Header + busca + barra de salvar + o mínimo de detalhes que precisa caber. */
        private val FIXED_WITHOUT_MAP = 320.dp
        private val MAP_HIDE_BELOW = 96.dp

        /**
         *     < 650 dp → 190 · 650–800 → 220 · 800–900 → 240 · > 900 → 260
         *
         * Com fonte grande o mapa cede 20 dp para os detalhes (nunca abaixo de [MAP_MIN_HEIGHT]).
         * Teclado aberto (altura < [SHORT_HEIGHT]): o mapa encolhe para o que sobrar, até
         * [MAP_TYPING_MAX], e some se não couber. Digitando ([focus] ≠ NONE): no máximo
         * [MAP_TYPING_MAX]; em telas pequenas, 0 — o Salvar nunca sai da tela.
         */
        fun forHeight(availableHeight: Dp, fontScale: Float = 1f, focus: PlacePickerFocus = PlacePickerFocus.NONE): PlacePickerDimensions {
            if (availableHeight < SHORT_HEIGHT) {
                val room = (availableHeight - FIXED_WITHOUT_MAP).coerceIn(0.dp, MAP_TYPING_MAX)
                return PlacePickerDimensions(if (room < MAP_HIDE_BELOW) 0.dp else room, horizontalPadding = 16.dp, sectionSpacing = 8.dp)
            }
            val base = when {
                availableHeight < 650.dp -> 190.dp
                availableHeight < 800.dp -> 220.dp
                availableHeight <= 900.dp -> 240.dp
                else -> 260.dp
            }
            val normal = (if (fontScale >= 1.3f) base - 20.dp else base).coerceIn(MAP_MIN_HEIGHT, MAP_MAX_HEIGHT)
            val map = when {
                focus == PlacePickerFocus.NONE -> normal
                availableHeight < TYPING_HIDE_BELOW -> 0.dp
                else -> minOf(normal, MAP_TYPING_MAX)
            }
            val compact = availableHeight < 700.dp
            return PlacePickerDimensions(map, horizontalPadding = 16.dp, sectionSpacing = if (compact) 10.dp else 14.dp)
        }
    }
}

/** Ações da tela (o ViewModel no app, lambdas nos testes de UI). */
data class PlacePickerActions(
    val onRetryLoad: () -> Unit = {},
    val onMapInteraction: () -> Unit = {},
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
 *     HEADER    NOVO LOCAL / ✕
 *     SEARCH    🔎 BUSCAR ENDEREÇO + campo + resultados (opacos, empurram o mapa)
 *     MAP       altura própria e clipToBounds — nunca é fundo de nada
 *     DETAILS   LazyColumn com weight(1f), fundo sólido: local, tipo, nome, raio, privacidade
 *     SAVE BAR  fixa
 *
 * Nada de DETAILS é filho da Box do mapa. O weight fica nos detalhes, não no mapa.
 * [map] substitui o MapView real nos testes de distribuição de espaço; null = MapView real.
 */
@Composable
fun PlacePickerLayout(
    state: PlacePickerState,
    actions: PlacePickerActions,
    modifier: Modifier = Modifier,
    allowTypeChange: Boolean = true,
    map: (@Composable (Modifier) -> Unit)? = null,
) {
    var focus by remember { mutableStateOf(PlacePickerFocus.NONE) }
    BoxWithConstraints(modifier.fillMaxSize().background(HoodieColors.Night).testTag(PlacePickerTags.ROOT)) {
        val dims = PlacePickerDimensions.forHeight(maxHeight, LocalDensity.current.fontScale, focus)
        val pad = dims.horizontalPadding
        Column(Modifier.fillMaxSize()) {
            PlacePickerHeader(state.editing, state.type, actions.onClose, pad)
            if (state.editing && state.loadState != PlaceLoadState.Ready) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(pad),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (state.loadState == PlaceLoadState.Loading || state.loadState == PlaceLoadState.Idle) {
                        CircularProgressIndicator()
                    } else {
                        Text(stringResource(R.string.place_load_failed), color = HoodieColors.Coral)
                        PixelButton(stringResource(R.string.place_retry_load), actions.onRetryLoad, Modifier.fillMaxWidth())
                        PixelButton(stringResource(R.string.place_back), actions.onClose, Modifier.fillMaxWidth())
                    }
                }
                return@Column
            }
            PlaceSearchSection(
                state.query, state.searching, state.results,
                actions.onQueryChange, actions.onSearch, actions.onChooseResult, state.searchError,
                Modifier.fillMaxWidth().background(HoodieColors.Night).padding(horizontal = pad).padding(bottom = 8.dp),
                onFocusChange = { focused -> focus = focusAfter(focus, PlacePickerFocus.SEARCH, focused) },
            )
            if (map == null) {
                PlaceMapSection(state, dims.mapHeight, actions.onCenterChanged, actions.onMyLocation, actions.onMapInteraction)
            } else {
                PlaceMapSection(state, dims.mapHeight, actions.onCenterChanged, actions.onMyLocation, actions.onMapInteraction, map = map)
            }
            state.locationError?.let {
                Text(
                    "⚠ $it", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Coral, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().background(HoodieColors.Night).padding(horizontal = pad, vertical = 4.dp).testTag(PlacePickerTags.LOCATION_ERROR),
                )
            }
            LazyColumn(
                // Fundo sólido: mesmo com algum comportamento estranho de composição, nada do mapa aparece por trás.
                modifier = Modifier.weight(1f).fillMaxWidth().background(HoodieColors.Night).testTag(PlacePickerTags.DETAILS),
                contentPadding = PaddingValues(start = pad, end = pad, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing),
            ) {
                item("address") { SelectedAddressCard(state.address, state.latitude, state.longitude, state.hasPoint) }
                val mode = placeTypeSelectorMode(allowTypeChange, state.editing)
                if (mode != PlaceTypeSelectorMode.HIDDEN) item("type") { PlaceTypeSelector(state.type, mode, actions.onTypeChange) }
                item("name") {
                    PlaceNameField(state.name, actions.onNameChange, onFocusChange = { focused -> focus = focusAfter(focus, PlacePickerFocus.NAME, focused) })
                }
                item("radius") { PlaceRadiusControl(state.radius, actions.onRadiusChange, Modifier.testTag(PlacePickerTags.RADIUS)) }
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
            onRetryLoad = { vm.reloadPlace() },
            onMapInteraction = vm::dismissResults,
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
