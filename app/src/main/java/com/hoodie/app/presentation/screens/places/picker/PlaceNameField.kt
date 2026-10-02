package com.hoodie.app.presentation.screens.places.picker

import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hoodie.app.presentation.components.SectionLabel

/**
 *     NOME
 *     [ Casa                         ]
 *
 * Título externo pequeno, sem label flutuante.
 */
@Composable
fun PlaceNameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.ui_place_name_field_1), Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value, onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(PlacePickerTags.NAME),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            placeholder = { Text(stringResource(R.string.ui_place_name_field_2)) },
        )
    }
}
