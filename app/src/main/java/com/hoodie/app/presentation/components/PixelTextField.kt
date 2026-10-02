package com.hoodie.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.hoodie.app.presentation.theme.HoodieColors

/** Altura mínima de toque/leitura dos campos do Hoodie. */
val PIXEL_TEXT_FIELD_MIN_HEIGHT = 52.dp

/**
 * Cores dos campos: fundo **sempre opaco** (nada atrás — mapa, cena — aparece
 * através do campo), texto claro de alto contraste e borda pixel forte.
 */
@Composable
fun pixelTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = HoodieColors.PanelLight,
    unfocusedContainerColor = HoodieColors.PanelLight,
    disabledContainerColor = HoodieColors.Panel,
    errorContainerColor = HoodieColors.PanelLight,
    focusedTextColor = HoodieColors.Ink,
    unfocusedTextColor = HoodieColors.Ink,
    disabledTextColor = HoodieColors.Muted,
    errorTextColor = HoodieColors.Ink,
    focusedBorderColor = HoodieColors.Gold,
    unfocusedBorderColor = HoodieColors.Outline,
    errorBorderColor = HoodieColors.Coral,
    cursorColor = HoodieColors.Hood,
    focusedPlaceholderColor = HoodieColors.Muted,
    unfocusedPlaceholderColor = HoodieColors.Muted,
)

/**
 *     ┌──────────────────────────────┐
 *     │ texto                        │   fundo sólido, borda de 2 dp, ≥ 52 dp
 *     └──────────────────────────────┘
 *
 * Campo de texto padrão do Hoodie (busca de endereço, nome do lugar…).
 */
@Composable
fun PixelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // O background extra garante opacidade mesmo antes do container do Material desenhar.
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PIXEL_TEXT_FIELD_MIN_HEIGHT)
            .background(HoodieColors.PanelLight, RectangleShape),
        placeholder = placeholder?.let { { Text(it, maxLines = 1) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        singleLine = singleLine,
        shape = RectangleShape,
        colors = pixelTextFieldColors(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}
