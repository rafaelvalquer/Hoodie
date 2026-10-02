package com.hoodie.app.presentation.screens.places.picker

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
import com.hoodie.app.presentation.theme.HoodieColors

/** Título da tela: editar um lugar existente ou criar um novo. */
fun placePickerTitle(editing: Boolean, type: PlaceType): String =
    if (editing) "📍 MUDAR LOCAL" else "${type.emoji} NOVO LOCAL"

/**
 *     📍 MUDAR LOCAL                    ✕
 *
 * Altura fixa (~52 dp), sem padding excessivo.
 */
@Composable
fun PlacePickerHeader(editing: Boolean, type: PlaceType, onClose: () -> Unit, horizontalPadding: Dp = 16.dp) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(start = horizontalPadding, end = 4.dp).testTag(PlacePickerTags.HEADER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            placePickerTitle(editing, type),
            style = MaterialTheme.typography.titleMedium,
            color = HoodieColors.Hood,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "✕",
            style = MaterialTheme.typography.titleLarge,
            // Cor explícita: sem Surface por cima, o padrão do Material sai escuro sobre o fundo escuro.
            color = HoodieColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onClose)
                .semantics { contentDescription = "Fechar" }
                .padding(top = 10.dp)
                .testTag(PlacePickerTags.CLOSE),
        )
    }
}
