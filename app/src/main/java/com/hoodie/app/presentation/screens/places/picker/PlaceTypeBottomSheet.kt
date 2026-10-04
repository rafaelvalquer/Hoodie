package com.hoodie.app.presentation.screens.places.picker

import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors

/** Lista de tipos fora da tela principal: tira o ChipRow de nove itens do caminho. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceTypeBottomSheet(selected: PlaceType, onSelect: (PlaceType) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = HoodieColors.Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp).testTag(PlacePickerTags.TYPE_SHEET)) {
            SectionLabel(stringResource(R.string.ui_place_type_bottom_sheet_1), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            PlaceType.physicalPlaceOptions.forEach { t ->
                val isSelected = t == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(if (isSelected) HoodieColors.PanelLight else HoodieColors.Panel)
                        .clickable { onSelect(t) }
                        .semantics { role = Role.RadioButton; this.selected = isSelected }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag(PlacePickerTags.sheetType(t)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp))
                    Text(t.label, style = MaterialTheme.typography.bodyLarge, color = if (isSelected) HoodieColors.Hood else HoodieColors.Ink, modifier = Modifier.weight(1f))
                    if (isSelected) Text(stringResource(R.string.ui_place_type_bottom_sheet_2), color = HoodieColors.Gold)
                }
            }
        }
    }
}
