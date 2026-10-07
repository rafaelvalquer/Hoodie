package com.hoodie.app.presentation.screens.places.picker

import androidx.compose.foundation.layout.sizeIn
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.pixel.icons.IconLabel
import com.hoodie.app.pixel.icons.PixelIconView
import com.hoodie.app.pixel.icons.PixelIcons
import com.hoodie.app.presentation.theme.HoodieColors

/** Como escolher o tipo: escondido (onboarding), resumo + "Alterar" (edição) ou grade (novo lugar). */
enum class PlaceTypeSelectorMode { HIDDEN, SUMMARY, GRID }

fun placeTypeSelectorMode(allowTypeChange: Boolean, editing: Boolean) = when {
    !allowTypeChange -> PlaceTypeSelectorMode.HIDDEN
    editing -> PlaceTypeSelectorMode.SUMMARY
    else -> PlaceTypeSelectorMode.GRID
}

/**
 * Edição:  Tipo / [ícone] Casa ........... ALTERAR  (abre [PlaceTypeBottomSheet])
 * Novo:    grade 2 colunas que acomoda todos os tipos físicos com rótulos legíveis.
 */
@Composable
fun PlaceTypeSelector(type: PlaceType, mode: PlaceTypeSelectorMode, onChange: (PlaceType) -> Unit, modifier: Modifier = Modifier) {
    when (mode) {
        PlaceTypeSelectorMode.HIDDEN -> Unit
        PlaceTypeSelectorMode.SUMMARY -> {
            var open by remember { mutableStateOf(false) }
            PixelPanel(modifier.fillMaxWidth().testTag(PlacePickerTags.TYPE_SUMMARY), color = HoodieColors.PanelLight) {
                SectionLabel(stringResource(R.string.ui_place_type_selector_1))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    IconLabel(PixelIcons.of(type), type.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.ui_place_type_selector_2),
                        style = MaterialTheme.typography.labelLarge,
                        color = HoodieColors.Blue,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable { open = true }.semantics { role = Role.Button }.padding(8.dp).testTag(PlacePickerTags.TYPE_CHANGE),
                    )
                }
            }
            if (open) PlaceTypeBottomSheet(type, onSelect = { onChange(it); open = false }, onDismiss = { open = false })
        }
        PlaceTypeSelectorMode.GRID -> Column(modifier.fillMaxWidth().testTag(PlacePickerTags.TYPE_GRID), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(stringResource(R.string.ui_place_type_selector_3))
            PlaceType.physicalPlaceOptions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { t -> PlaceTypeCell(t, t == type, { onChange(t) }, Modifier.weight(1f)) }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PlaceTypeCell(t: PlaceType, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .heightIn(min = 48.dp)
            .border(2.dp, if (selected) HoodieColors.Gold else HoodieColors.Outline)
            .background(if (selected) HoodieColors.PanelLight else HoodieColors.Panel)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { role = Role.RadioButton; this.selected = selected; contentDescription = t.label }
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .testTag(PlacePickerTags.typeCell(t)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelIconView(PixelIcons.of(t), size = 22.dp, tint = if (selected) HoodieColors.Hood else HoodieColors.Ink)
        Text(t.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            color = if (selected) HoodieColors.Hood else HoodieColors.Ink)
    }
}
