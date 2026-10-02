package com.hoodie.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.location.AddressResult
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.screens.places.PlacePickerState
import com.hoodie.app.presentation.screens.places.picker.PlacePickerActions
import com.hoodie.app.presentation.screens.places.picker.PlacePickerLayout
import com.hoodie.app.presentation.screens.places.picker.PlacePickerTags
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Gera PNGs da tela "Novo Local" com o MapView real (393×873, a resolução da imagem do
 * problema) em getExternalFilesDir("screenshots"), para revisão visual. Não faz asserções
 * de layout — isso é do [PlacePickerRealMapTest].
 */
@RunWith(AndroidJUnit4::class)
class PlacePickerScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun save(name: String) {
        Thread.sleep(1_500) // tiles e animação de centralização
        rule.waitForIdle()
        val bmp = rule.onNodeWithTag(PlacePickerTags.ROOT).captureToImage().asAndroidBitmap()
        val dir = File(rule.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun novo_local_inicial_e_com_endereco() {
        val paulista = AddressResult("Av. Paulista, 1000, Bela Vista, São Paulo - SP", -23.5614, -46.6559)
        var choose: () -> Unit = {}
        rule.setContent {
            var state by remember { mutableStateOf(PlacePickerState(type = PlaceType.WORK, name = "")) }
            choose = {
                state = state.copy(latitude = paulista.latitude, longitude = paulista.longitude, address = paulista.label,
                    hasPoint = true, recenterKey = state.recenterKey + 1, name = "Escritório", query = "Av. Paulista, 1000")
            }
            Box(Modifier.requiredSize(393.dp, 873.dp)) {
                HoodieTheme {
                    PlacePickerLayout(
                        state,
                        PlacePickerActions(
                            onQueryChange = { state = state.copy(query = it) },
                            onSearch = { state = state.copy(results = listOf(paulista)) },
                        ),
                    )
                }
            }
        }
        save("01_inicial")
        rule.onNodeWithTag(PlacePickerTags.SEARCH).performTextInput("Av. Paulista, 1000")
        rule.runOnIdle { }
        rule.onNodeWithTag(PlacePickerTags.SEARCH_ACTION).performClick()
        save("02_resultados")
        rule.runOnIdle { choose() }
        save("03_selecionado")
    }
}
