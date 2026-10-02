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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import com.hoodie.app.presentation.components.PixelTextField
import com.hoodie.app.presentation.components.SectionLabel
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
 *     🔎 BUSCAR ENDEREÇO
 *     [ 🔎 Rua, número, cidade          BUSCAR ]
 *     ⚠ Endereço não encontrado
 *     ┌ resultados (opacos, abaixo do campo) ┐
 *
 * Sempre visível no topo da tela. A busca roda pelo IME Search ou pelo BUSCAR,
 * fecha o teclado e o mapa volta ao tamanho normal.
 */
@Composable
fun PlaceSearchSection(
    query: String,
    searching: Boolean,
    results: List<AddressResult>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onChoose: (AddressResult) -> Unit,
    error: String? = null,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
) {
    Column(modifier.testTag(PlacePickerTags.SEARCH_SECTION)) {
        SectionLabel("🔎 Buscar endereço", Modifier.padding(bottom = 4.dp))
        PlaceSearchBar(query, searching, onQueryChange, onSearch, error, onFocusChange = onFocusChange)
        PlaceSearchResults(results, onChoose, Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

@Composable
fun PlaceSearchBar(
    query: String,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    error: String? = null,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val focus = LocalFocusManager.current
    val canSearch = query.isNotBlank() && !searching
    val search = { if (canSearch) { focus.clearFocus(); onSearch() } }
    Column(modifier) {
        PixelTextField(
            query, onQueryChange,
            modifier = Modifier.onFocusChanged { onFocusChange(it.isFocused) }.testTag(PlacePickerTags.SEARCH),
            placeholder = "Rua, número, cidade",
            leadingIcon = { Text("🔎") },
            trailingIcon = {
                if (searching) {
                    CircularProgressIndicator(Modifier.size(18.dp).testTag(PlacePickerTags.SEARCH_LOADING), strokeWidth = 2.dp, color = HoodieColors.Gold)
                } else {
                    // Sempre à vista: deixa claro que é aqui que se busca.
                    Text(
                        "BUSCAR",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canSearch) HoodieColors.Gold else HoodieColors.Muted,
                        modifier = Modifier
                            .clickable(enabled = canSearch, onClick = search)
                            .semantics { role = Role.Button; contentDescription = "Buscar endereço" }
                            .padding(horizontal = 10.dp, vertical = 12.dp)
                            .testTag(PlacePickerTags.SEARCH_ACTION),
                    )
                }
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

/**
 * Lista curta (máx. 168 dp), 100% opaca, logo abaixo do campo de busca — fora
 * da área do mapa. Escolher um resultado fecha a lista e recentraliza o mapa.
 */
@Composable
fun PlaceSearchResults(results: List<AddressResult>, onChoose: (AddressResult) -> Unit, modifier: Modifier = Modifier) {
    if (results.isEmpty()) return
    LazyColumn(
        modifier
            .heightIn(max = 168.dp)
            .border(2.dp, HoodieColors.Outline)
            .background(HoodieColors.Panel)
            .testTag(PlacePickerTags.RESULTS),
    ) {
        items(results) { r ->
            val (line1, line2) = addressLines(r.label)
            Column(
                Modifier.fillMaxWidth().background(HoodieColors.Panel).clickable { onChoose(r) }
                    .semantics { role = Role.Button }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("📍 $line1", style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                line2?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 22.dp)) }
            }
            HorizontalDivider(color = HoodieColors.PanelLight)
        }
    }
}
