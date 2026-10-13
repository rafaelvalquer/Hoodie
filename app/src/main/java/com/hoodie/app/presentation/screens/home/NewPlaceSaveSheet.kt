package com.hoodie.app.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.util.Geo
import com.hoodie.app.engine.context.NewPlaceSaveOptions
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.theme.HoodieColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewPlaceSaveSheet(
    questionId: Long,
    type: PlaceType,
    options: NewPlaceSaveOptions?,
    busy: Boolean,
    onSave: (String, Long?, Pair<Double, Double>?) -> Unit,
    onToday: () -> Unit,
    onPositionChange: (Pair<Double, Double>?) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(questionId) { mutableStateOf("") }
    var coordinates by rememberSaveable(questionId) { mutableStateOf("") }
    var reuseId by rememberSaveable(questionId) { mutableStateOf<Long?>(null) }
    val manualPosition = Geo.parse(coordinates)
    val selected = options?.nearby?.firstOrNull { it.id == reuseId && it.type == type }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp).testTag("new_place_save_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quer guardar este lugar?", style = MaterialTheme.typography.titleLarge)
            Text(type.label, style = MaterialTheme.typography.titleMedium)
            if (options == null) Text("Carregando localização e lugares próximos…")
            else if (options.hasLocation) {
                Text("Localização aproximada registrada. Confira os lugares próximos; a posição pode ser imprecisa.",
                    style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted)
            } else {
                Text("Sem localização disponível. Você pode informar coordenadas ou escolher Somente hoje.")
                OutlinedTextField(coordinates, { coordinates = it; reuseId = null; onPositionChange(Geo.parse(it)) }, singleLine = true, enabled = !busy,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Coordenadas: latitude, longitude") },
                    isError = coordinates.isNotBlank() && manualPosition == null)
            }
            OutlinedTextField(name, { name = it }, singleLine = true, enabled = !busy && selected == null,
                modifier = Modifier.fillMaxWidth(), label = { Text("Nome (opcional)") })
            if (options?.nearby?.isNotEmpty() == true) {
                Text("Lugares salvos próximos", style = MaterialTheme.typography.titleSmall)
                options.nearby.forEach { place ->
                    TextButton(onClick = { reuseId = place.id }, enabled = !busy && place.type == type,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("${if (selected?.id == place.id) "✓ " else ""}${place.name} · ${place.type.label}")
                    }
                }
                if (selected != null) TextButton(onClick = { reuseId = null }, enabled = !busy) {
                    Text("É outro lugar — criar novo cadastro")
                }
            }
            PixelButton(selected?.let { "Reutilizar ${it.name}" } ?: "Salvar", {
                onSave(name, selected?.id, manualPosition)
            }, Modifier.fillMaxWidth(), enabled = !busy && options != null &&
                (options.hasLocation || manualPosition != null))
            PixelButton("Somente hoje", onToday, Modifier.fillMaxWidth(), enabled = !busy,
                color = HoodieColors.Panel, textColor = HoodieColors.Ink)
        }
    }
}
