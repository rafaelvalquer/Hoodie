package com.hoodie.app.presentation.screens.places.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.hoodie.app.presentation.components.MapPicker
import com.hoodie.app.presentation.screens.places.PlacePickerState
import com.hoodie.app.presentation.theme.HoodieColors

/**
 *     ┌──── área do mapa (altura fixa) ────┐
 *     │ [◎ Minha localização]              │
 *     │              MapView               │
 *     │                📍                  │
 *     │                         © OSM      │
 *     └────────────────────────────────────┘
 *     ↓ fim do mapa — o formulário começa aqui
 *
 * O container define a altura e corta tudo o que o MapView (AndroidView) tentar
 * desenhar fora dela ([clipToBounds]). Nenhum controle do formulário é filho desta Box.
 */
@Composable
fun PlaceMapSection(
    state: PlacePickerState,
    height: Dp,
    onCenterChanged: (Double, Double) -> Unit,
    onMyLocation: () -> Unit,
    onMapInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    map: @Composable (Modifier) -> Unit = { m ->
        MapPicker(
            state.latitude, state.longitude, state.radius, state.recenterKey, onCenterChanged, m,
            onMyLocation = onMyLocation, loadingLocation = state.locating, onMapInteraction = onMapInteraction,
        )
    },
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .background(HoodieColors.Panel)
            .testTag(PlacePickerTags.MAP),
    ) {
        map(Modifier.matchParentSize())
    }
}
