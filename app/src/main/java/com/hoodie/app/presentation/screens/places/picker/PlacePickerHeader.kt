package com.hoodie.app.presentation.screens.places.picker

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.pixel.icons.IconLabel
import com.hoodie.app.pixel.icons.PixelIcons
import com.hoodie.app.presentation.theme.HoodieColors

/** Título da tela: editar um lugar existente ou criar um novo. */
@Composable
fun placePickerTitle(editing: Boolean, type: PlaceType): String =
    if (editing) stringResource(R.string.place_edit_title) else stringResource(R.string.place_new_title)

/**
 *     [pino] MUDAR LOCAL                    ✕
 *
 * Altura fixa (~52 dp), sem padding excessivo.
 */
@Composable
fun PlacePickerHeader(editing: Boolean, type: PlaceType, onClose: () -> Unit, horizontalPadding: Dp = 16.dp) {
    val uiTextContext = LocalContext.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(start = horizontalPadding, end = 4.dp).testTag(PlacePickerTags.HEADER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconLabel(
            if (editing) PixelIcons.PIN else PixelIcons.of(type),
            placePickerTitle(editing, type),
            style = MaterialTheme.typography.titleMedium,
            color = HoodieColors.Hood,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.ui_place_picker_header_1),
            style = MaterialTheme.typography.titleLarge,
            // Cor explícita: sem Surface por cima, o padrão do Material sai escuro sobre o fundo escuro.
            color = HoodieColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = uiTextContext.getString(R.string.ui_extra_place_picker_header_1) }
                .padding(top = 10.dp)
                .testTag(PlacePickerTags.CLOSE),
        )
    }
}
