package com.hoodie.app.presentation.screens.places.picker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hoodie.app.presentation.components.PixelTextField
import com.hoodie.app.presentation.components.SectionLabel

/**
 *     NOME DO LOCAL
 *     [ Escritório                   ]   fundo sólido, borda forte
 */
@Composable
fun PlaceNameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, onFocusChange: (Boolean) -> Unit = {}) {
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxWidth()) {
        SectionLabel("Nome do local", Modifier.padding(bottom = 4.dp))
        PixelTextField(
            value, onValueChange,
            modifier = Modifier.onFocusChanged { onFocusChange(it.isFocused) }.testTag(PlacePickerTags.NAME),
            placeholder = "Ex.: Casa da praia",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )
    }
}
