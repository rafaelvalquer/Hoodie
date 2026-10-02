package com.hoodie.app.presentation.screens.places.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors

/** Detalhes de privacidade do seletor (mostrados só em "Saiba mais"). */
val PLACE_PRIVACY_DETAILS = listOf(
    "O endereço usado na busca é enviado ao serviço de geocodificação do Android.",
    "Os tiles do mapa são fornecidos pelo OpenStreetMap.",
    "As coordenadas do local salvo são armazenadas cifradas, somente neste aparelho.",
)

/** 🔒 Local salvo somente neste aparelho. SAIBA MAIS → bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePrivacyInfo(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().testTag(PlacePickerTags.PRIVACY), verticalAlignment = Alignment.CenterVertically) {
        Text("🔒 Local salvo somente neste aparelho.", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.weight(1f))
        Text(
            "SAIBA MAIS",
            style = MaterialTheme.typography.labelSmall,
            color = HoodieColors.Blue,
            modifier = Modifier.clickable { open = true }.semantics { role = Role.Button }.padding(8.dp).testTag(PlacePickerTags.PRIVACY_MORE),
        )
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(), containerColor = HoodieColors.Panel) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).testTag(PlacePickerTags.PRIVACY_SHEET), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("🔒 Privacidade")
                PLACE_PRIVACY_DETAILS.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Ink) }
            }
        }
    }
}
