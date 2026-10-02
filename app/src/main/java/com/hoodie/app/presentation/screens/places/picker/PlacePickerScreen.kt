package com.hoodie.app.presentation.screens.places.picker

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hoodie.app.core.model.PlaceType

/**
 * Rota place_picker, dentro do Scaffold principal. O NavHost já aplica o padding do
 * Scaffold (status bar em cima, barra de navegação embaixo): aqui ele é só marcado como
 * consumido, para que [imePadding] some apenas o que o teclado cobre além da barra
 * inferior — sem statusBarsPadding duplicado e sem alturas manuais.
 */
@Composable
fun PlacePickerScreen(type: PlaceType, placeId: Long?, onBack: () -> Unit, scaffoldPadding: PaddingValues = PaddingValues()) {
    PlacePickerContent(
        type, placeId, onDone = onBack, onCancel = onBack,
        modifier = Modifier.consumeWindowInsets(scaffoldPadding).imePadding(),
    )
}
