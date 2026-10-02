package com.hoodie.app.presentation.screens.places.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.location.AddressResult
import com.hoodie.app.presentation.theme.HoodieColors

/** Resultado em duas linhas: "Rua X, 123" / "São Paulo - SP" (o resto do endereço). */
fun addressLines(label: String): Pair<String, String?> {
    val parts = label.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size <= 1) return label.trim() to null
    // Número costuma vir como segundo pedaço ("Rua X, 123 - Bairro"): fica na primeira linha.
    val firstCount = if (parts[1].firstOrNull()?.isDigit() == true) 2 else 1
    return parts.take(firstCount).joinToString(", ") to parts.drop(firstCount).joinToString(", ").ifBlank { null }
}

/**
 *     [ 🔎 Rua, número, cidade        ◌ ]
 *     ⚠ Endereço não encontrado
 *
 * A busca roda pelo IME Search ou pelo toque em 🔎. Loading e erro ficam no próprio campo.
 */
@Composable
fun PlaceSearchBar(
    query: String,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    error: String? = null,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val search = { if (query.isNotBlank() && !searching) { focus.clearFocus(); onSearch() } }
    Column(modifier) {
        OutlinedTextField(
            query, onQueryChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(PlacePickerTags.SEARCH),
            placeholder = { Text("Rua, número, cidade", maxLines = 1) },
            leadingIcon = { Text("🔎") },
            trailingIcon = {
                if (searching) CircularProgressIndicator(Modifier.size(18.dp).testTag(PlacePickerTags.SEARCH_LOADING), strokeWidth = 2.dp)
                else if (query.isNotBlank()) Text(
                    "BUSCAR",
                    style = MaterialTheme.typography.labelSmall,
                    color = HoodieColors.Blue,
                    modifier = Modifier.clickable(onClick = search).semantics { role = Role.Button; contentDescription = "Buscar endereço" }.padding(10.dp),
                )
            },
            isError = error != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
        )
        if (error != null) {
            Text("⚠ $error", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Coral, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp).testTag(PlacePickerTags.SEARCH_ERROR))
        }
    }
}

/** Lista curta (máx. 140 dp) por cima do mapa: o mapa não é empurrado. */
@Composable
fun PlaceSearchResults(results: List<AddressResult>, onChoose: (AddressResult) -> Unit, modifier: Modifier = Modifier) {
    if (results.isEmpty()) return
    LazyColumn(
        modifier
            .heightIn(max = 140.dp)
            .border(2.dp, HoodieColors.Outline)
            .background(HoodieColors.Panel)
            .testTag(PlacePickerTags.RESULTS),
    ) {
        items(results) { r ->
            val (line1, line2) = addressLines(r.label)
            Column(Modifier.fillMaxWidth().clickable { onChoose(r) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("📍 $line1", style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                line2?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 22.dp)) }
            }
            HorizontalDivider(color = HoodieColors.PanelLight)
        }
    }
}
