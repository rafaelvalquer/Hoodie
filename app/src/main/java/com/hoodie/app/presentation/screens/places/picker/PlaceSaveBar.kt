package com.hoodie.app.presentation.screens.places.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.theme.HoodieColors

fun saveButtonLabel(type: PlaceType, saving: Boolean): String =
    if (saving) "SALVANDO..." else "SALVAR COMO ${type.label.uppercase()}"

/**
 * Barra fixa fora da área de rolagem: Salvar sempre acessível.
 *
 *     ⚠ Não foi possível salvar
 *     [        SALVAR COMO CASA        ]   (mín. 52 dp)
 */
@Composable
fun PlaceSaveBar(
    type: PlaceType,
    enabled: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    error: String? = null,
    horizontalPadding: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(HoodieColors.Night)) {
        HorizontalDivider(color = HoodieColors.Outline, thickness = 2.dp)
        Column(Modifier.padding(horizontal = horizontalPadding, vertical = 10.dp)) {
            if (error != null) {
                Text("⚠ $error", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Coral, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp).testTag(PlacePickerTags.SAVE_ERROR))
            }
            PixelButton(
                saveButtonLabel(type, saving),
                onSave,
                Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(PlacePickerTags.SAVE),
                color = HoodieColors.Gold,
                enabled = enabled && !saving,
            )
        }
    }
}
