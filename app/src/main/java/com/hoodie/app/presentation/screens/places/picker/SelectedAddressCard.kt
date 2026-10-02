package com.hoodie.app.presentation.screens.places.picker

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.util.Locale

/** O que mostrar como local: endereço, coordenadas (sem endereço) ou a dica inicial. */
fun selectedAddressText(address: String?, latitude: Double, longitude: Double, hasPoint: Boolean): String = when {
    !address.isNullOrBlank() -> address
    hasPoint -> String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
    else -> "Busque um endereço, use sua localização ou ajuste o pino no mapa."
}

/**
 *     📍 LOCAL SELECIONADO
 *     Charleston Rd, Mountain View...      (no máximo 2 linhas)
 */
@Composable
fun SelectedAddressCard(address: String?, latitude: Double, longitude: Double, hasPoint: Boolean, modifier: Modifier = Modifier) {
    PixelPanel(modifier.fillMaxWidth().testTag(PlacePickerTags.ADDRESS), color = HoodieColors.PanelLight) {
        SectionLabel("📍 LOCAL SELECIONADO")
        Text(
            selectedAddressText(address, latitude, longitude, hasPoint),
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasPoint) HoodieColors.Ink else HoodieColors.Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).testTag(PlacePickerTags.ADDRESS_TEXT),
        )
    }
}
