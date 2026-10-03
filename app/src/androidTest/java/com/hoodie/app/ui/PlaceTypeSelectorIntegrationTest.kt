package com.hoodie.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.screens.home.ManualPlaceOptions
import com.hoodie.app.presentation.screens.places.AddPlaceDialog
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Confirma que as opções reais de seleção manual e cadastro vêm do mesmo catálogo físico. */
@RunWith(AndroidJUnit4::class)
class PlaceTypeSelectorIntegrationTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun selecao_manual_da_home_exibe_todos_os_tipos_fisicos() {
        rule.setContent {
            HoodieTheme {
                ManualPlaceOptions(onSelect = {})
            }
        }
        PlaceType.physicalPlaceOptions.forEach { type ->
            val label = "${type.emoji} ${type.label}".uppercase()
            assertTrue("Opção ausente na Home: $label", rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test fun dialogo_novo_lugar_exibe_o_mesmo_catalogo_fisico() {
        rule.setContent {
            HoodieTheme {
                AddPlaceDialog(onDismiss = {}, onHere = { _, _ -> }, onManual = { _, _, _ -> }, onMap = {})
            }
        }
        PlaceType.physicalPlaceOptions.forEach { type ->
            val label = "${type.emoji} ${type.label}"
            assertTrue("Opção ausente em Novo lugar: $label", rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
    }
}
