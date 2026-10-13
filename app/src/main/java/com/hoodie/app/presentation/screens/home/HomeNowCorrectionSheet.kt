package com.hoodie.app.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.home.HomeNowSnapshot
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.theme.HoodieColors
import kotlinx.coroutines.launch

/** Tipos mais comuns; os demais ficam em "Ver todos". */
internal val COMMON_CORRECTION_TYPES = listOf(PlaceType.HOME, PlaceType.WORK, PlaceType.GYM, PlaceType.RESTAURANT, PlaceType.OTHER)

/** Lugares cadastrados do tipo escolhido: com mais de um, o usuário precisa indicar qual. */
internal fun placesOfType(places: List<Place>, type: PlaceType): List<Place> = places.filter { it.type == type }

@Composable
@androidx.compose.material3.ExperimentalMaterial3Api
internal fun HomeNowCorrectionSheet(
    snapshot: HomeNowSnapshot?, onDismiss: () -> Unit, onSave: suspend (PlaceType, Boolean, Long?) -> Boolean, saving: Boolean,
    savedPlaces: List<Place> = emptyList(),
) {
    var selected by remember { mutableStateOf<PlaceType?>(null) }
    var selectedPlaceId by remember { mutableStateOf<Long?>(null) }
    var historical by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(!HoodieConfig.QUICK_CORRECTION_V2) }
    var localSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val options = if (showAll) PlaceType.physicalPlaceOptions else COMMON_CORRECTION_TYPES
    val candidates = selected?.let { placesOfType(savedPlaces, it) }.orEmpty()
    val needsPlace = HoodieConfig.QUICK_CORRECTION_V2 && candidates.size > 1
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HoodieColors.Panel,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.home_now_correct_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
                Text(stringResource(R.string.home_now_current_context, snapshot?.context?.label ?: stringResource(R.string.home_now_unknown)))
                if (!HoodieConfig.QUICK_CORRECTION_V2) {
                    snapshot?.contextEventId?.let { Text(stringResource(R.string.home_now_event_id, it)) }
                        ?: Text(stringResource(R.string.home_now_no_event))
                }
            }
            PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
                Text(stringResource(R.string.home_now_correct_scope))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !historical, onClick = { historical = false }, label = { Text(stringResource(R.string.home_now_from_now)) })
                    FilterChip(
                        selected = historical,
                        enabled = snapshot?.contextEventId != null,
                        onClick = { historical = true },
                        label = { Text(stringResource(R.string.home_now_since_start)) },
                    )
                }
            }
            options.forEach { type ->
                FilterChip(modifier = Modifier.fillMaxWidth(), selected = selected == type, onClick = { selected = type; selectedPlaceId = null }, label = { Text(type.label) })
            }
            if (!showAll) PixelButton(
                stringResource(R.string.home_correct_see_all), { showAll = true },
                Modifier.fillMaxWidth().testTag("home_correct_see_all"), color = HoodieColors.Panel, textColor = HoodieColors.Ink,
            )
            if (needsPlace) {
                Text(stringResource(R.string.home_correct_pick_place))
                candidates.forEach { place ->
                    FilterChip(modifier = Modifier.fillMaxWidth().testTag("home_correct_place_${place.id}"), selected = selectedPlaceId == place.id, onClick = { selectedPlaceId = place.id }, label = { Text(place.name) })
                }
            }
            PixelButton(stringResource(R.string.home_now_save_correction), {
                selected?.let { type -> scope.launch {
                    localSaving = true
                    // Um único lugar do tipo é inequívoco; vários exigem a escolha acima.
                    val placeId = selectedPlaceId ?: candidates.singleOrNull()?.id
                    try { if (onSave(type, historical, placeId)) onDismiss() } finally { localSaving = false }
                } }
            }, Modifier.fillMaxWidth(), enabled = !saving && !localSaving && selected != null && (!needsPlace || selectedPlaceId != null), color = HoodieColors.Gold)
        }
    }
}