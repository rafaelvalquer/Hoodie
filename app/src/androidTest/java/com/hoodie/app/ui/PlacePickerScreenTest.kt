package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.location.AddressResult
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.screens.places.PlaceLoadState
import com.hoodie.app.presentation.screens.places.PlacePickerState
import com.hoodie.app.presentation.screens.places.picker.PlacePickerActions
import com.hoodie.app.presentation.screens.places.picker.PlacePickerDimensions
import com.hoodie.app.presentation.screens.places.picker.PlacePickerLayout
import com.hoodie.app.presentation.screens.places.picker.PlacePickerTags
import com.hoodie.app.presentation.theme.HoodieColors
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Layout da tela "Novo Local" / "Mudar local" em tamanhos e fontes diferentes. O mapa
 * real (osmdroid) é trocado por uma caixa: aqui se testa a distribuição do espaço e os
 * fluxos. O MapView real é coberto por [PlacePickerRealMapTest].
 */
@RunWith(AndroidJUnit4::class)
class PlacePickerScreenTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val longAddress = "1600 Amphitheatre Parkway, Mountain View, California, United States of America, Planet Earth, Milky Way"
    private val paulista = AddressResult("Av. Paulista, 1000, Bela Vista, São Paulo - SP", -23.5614, -46.6559)
    private val paulista2 = AddressResult("Av. Paulista, 1000, Paraíso, São Paulo - SP", -23.5700, -46.6450)

    private fun editing(address: String? = "Charleston Rd, Mountain View, CA") = PlacePickerState(
        loadState = PlaceLoadState.Ready,
        type = PlaceType.HOME, editingId = 1L, name = "Casa", hasPoint = true, address = address, radius = 150f,
        latitude = 37.42, longitude = -122.08,
    )

    private fun newPlace() = PlacePickerState(type = PlaceType.WORK, name = "Trabalho")

    @Composable
    private fun Picker(state: PlacePickerState, actions: PlacePickerActions = PlacePickerActions(), allowTypeChange: Boolean = true, modifier: Modifier = Modifier) {
        HoodieTheme {
            PlacePickerLayout(state, actions, modifier, allowTypeChange, map = { m -> Box(m.background(Color(0xFF5E9E6B))) })
        }
    }

    /** Coloca a tela numa "janela" de [w]×[h] dp com escala de fonte [font]. */
    private fun show(w: Dp, h: Dp, font: Float = 1f, allowTypeChange: Boolean = true, state: PlacePickerState = editing()) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, font)) {
                Box(Modifier.requiredSize(w, h)) { Picker(state, allowTypeChange = allowTypeChange) }
            }
        }
        rule.waitForIdle()
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).getBoundsInRoot().let { Rect(it.left.value, it.top.value, it.right.value, it.bottom.value) }

    /** Busca acima do mapa, mapa com altura própria, detalhes entre mapa e Salvar, Salvar dentro da tela. */
    private fun assertLayout(h: Dp, font: Float) {
        val root = bounds(PlacePickerTags.ROOT)
        val search = bounds(PlacePickerTags.SEARCH_SECTION)
        val map = bounds(PlacePickerTags.MAP)
        val details = bounds(PlacePickerTags.DETAILS)
        val save = bounds(PlacePickerTags.SAVE)
        val header = bounds(PlacePickerTags.HEADER)
        val tag = "${h}/$font"
        val expectedMap = PlacePickerDimensions.forHeight(h, font).mapHeight.value
        assertEquals("$tag altura do mapa", expectedMap, map.height, 1f)
        assertTrue("$tag mapa nunca espremido", map.height >= PlacePickerDimensions.MAP_MIN_HEIGHT.value - 1)
        assertTrue("$tag mapa nunca domina", map.height <= PlacePickerDimensions.MAP_MAX_HEIGHT.value + 1)
        assertTrue("$tag header acima da busca", header.bottom <= search.top + 0.5f)
        assertTrue("$tag busca acima do mapa", search.bottom <= map.top + 0.5f)
        assertTrue("$tag detalhes abaixo do mapa", details.top >= map.bottom - 0.5f)
        assertTrue("$tag detalhes têm espaço", details.height >= 48f)
        assertTrue("$tag salvar abaixo dos detalhes", save.top >= details.bottom - 0.5f)
        assertTrue("$tag salvar dentro da tela", save.bottom <= root.bottom + 0.5f)
        assertTrue("$tag salvar com 52 dp", save.height >= 51.5f)
        rule.onNodeWithTag(PlacePickerTags.SEARCH).assertIsDisplayed()
        rule.onNodeWithTag(PlacePickerTags.SAVE).assertIsDisplayed()
        rule.onNodeWithTag(PlacePickerTags.MAP).assertIsDisplayed()
    }

    @Test fun pequeno_360x640() { show(360.dp, 640.dp); assertLayout(640.dp, 1f) }
    @Test fun pequeno_360x640_fonte130() { show(360.dp, 640.dp, 1.3f); assertLayout(640.dp, 1.3f) }
    @Test fun medio_360x800() { show(360.dp, 800.dp); assertLayout(800.dp, 1f) }
    @Test fun medio_360x800_fonte130() { show(360.dp, 800.dp, 1.3f); assertLayout(800.dp, 1.3f) }
    @Test fun tela_da_imagem_393x873() { show(393.dp, 873.dp, state = newPlace()); assertLayout(873.dp, 1f) }
    @Test fun tela_da_imagem_393x873_fonte130() { show(393.dp, 873.dp, 1.3f, state = newPlace()); assertLayout(873.dp, 1.3f) }
    @Test fun grande_411x891() { show(411.dp, 891.dp); assertLayout(891.dp, 1f) }
    @Test fun grande_411x891_fonte130() { show(411.dp, 891.dp, 1.3f); assertLayout(891.dp, 1.3f) }

    @Test
    fun busca_de_endereco_sempre_visivel_ao_abrir_novo_local() {
        show(393.dp, 873.dp, state = newPlace())
        rule.onNodeWithText("🏢 NOVO LOCAL").assertIsDisplayed()
        rule.onNodeWithText("🔎 BUSCAR ENDEREÇO").assertIsDisplayed()
        rule.onNodeWithTag(PlacePickerTags.SEARCH).assertIsDisplayed()
        rule.onNodeWithTag(PlacePickerTags.SEARCH_ACTION).assertIsDisplayed()
        rule.onNodeWithText("Busque um endereço, use sua localização ou ajuste o pino no mapa.").assertIsDisplayed()
        // Sem ponto escolhido o Salvar fica desabilitado.
        rule.onNodeWithTag(PlacePickerTags.SAVE).assertIsNotEnabled()
    }

    /** Fluxo do plano: digitar → buscar → resultados fora do mapa → escolher → local selecionado → Salvar habilita. */
    @Test
    fun fluxo_de_busca_escolhe_endereco_e_habilita_salvar() {
        var recenters = 0
        rule.setContent {
            var state by remember { mutableStateOf(newPlace()) }
            Box(Modifier.requiredSize(393.dp, 873.dp)) {
                Picker(
                    state,
                    PlacePickerActions(
                        onQueryChange = { state = state.copy(query = it) },
                        onSearch = { state = state.copy(results = listOf(paulista, paulista2)) },
                        onChooseResult = { r ->
                            recenters++
                            state = state.copy(latitude = r.latitude, longitude = r.longitude, address = r.label, hasPoint = true,
                                results = emptyList(), recenterKey = state.recenterKey + 1)
                        },
                    ),
                )
            }
        }
        rule.onNodeWithTag(PlacePickerTags.SEARCH).performTextInput("Avenida Paulista 1000")
        rule.onNodeWithTag(PlacePickerTags.SEARCH_ACTION).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(PlacePickerTags.RESULTS).assertIsDisplayed()
        // Resultados opacos, dentro da seção de busca, acima do mapa — nunca sobre ele.
        assertTrue("resultados acima do mapa", bounds(PlacePickerTags.RESULTS).bottom <= bounds(PlacePickerTags.MAP).top + 0.5f)

        rule.onNodeWithText("Bela Vista", substring = true).performClick()
        rule.waitForIdle()
        assertEquals(1, recenters)
        assertEquals(0, rule.onAllNodes(hasTestTag(PlacePickerTags.RESULTS)).fetchSemanticsNodes().size)
        val address = rule.onNodeWithTag(PlacePickerTags.ADDRESS_TEXT).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        assertEquals(paulista.label, address)
        rule.onNodeWithTag(PlacePickerTags.SAVE).assertIsEnabled()
    }

    /** Digitando na busca numa tela pequena o mapa some; ao buscar (IME) o teclado fecha e o mapa volta. */
    @Test
    fun foco_na_busca_reduz_o_mapa_e_buscar_reexpande() {
        rule.setContent {
            var state by remember { mutableStateOf(newPlace()) }
            Box(Modifier.requiredSize(360.dp, 640.dp)) {
                Picker(state, PlacePickerActions(onQueryChange = { state = state.copy(query = it) }))
            }
        }
        val normal = PlacePickerDimensions.forHeight(640.dp).mapHeight.value
        assertEquals(normal, bounds(PlacePickerTags.MAP).height, 1f)
        rule.onNodeWithTag(PlacePickerTags.SEARCH).performClick()
        rule.onNodeWithTag(PlacePickerTags.SEARCH).performTextInput("Rua Augusta 500")
        rule.waitForIdle()
        assertEquals("mapa some enquanto se digita", 0f, bounds(PlacePickerTags.MAP).height, 1f)
        rule.onNodeWithTag(PlacePickerTags.SEARCH).assertIsDisplayed()
        rule.onNodeWithTag(PlacePickerTags.SAVE).assertIsDisplayed()

        rule.onNodeWithTag(PlacePickerTags.SEARCH).performImeAction()
        rule.waitForIdle()
        assertEquals("mapa volta depois de buscar", normal, bounds(PlacePickerTags.MAP).height, 1f)
    }

    /** Campo Nome: fundo 100% sólido, alto o bastante, fora do mapa, e digitar atualiza o nome. */
    @Test
    fun campo_nome_e_solido_alto_e_fora_do_mapa() {
        var typed = ""
        rule.setContent {
            var state by remember { mutableStateOf(editing().copy(name = "")) }
            Box(Modifier.requiredSize(393.dp, 873.dp)) {
                Picker(state, PlacePickerActions(onNameChange = { typed = it; state = state.copy(name = it) }))
            }
        }
        rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.NAME))
        val name = bounds(PlacePickerTags.NAME)
        val map = bounds(PlacePickerTags.MAP)
        assertTrue("altura mínima de 48 dp", name.height >= 48f)
        assertTrue("nome não intersecta o mapa", name.top >= map.bottom - 0.5f)

        // Fundo opaco: o miolo do campo (longe do texto e da borda) tem exatamente a cor do painel.
        val image = rule.onNodeWithTag(PlacePickerTags.NAME).captureToImage().toPixelMap()
        val sample = image[(image.width * 0.85f).toInt(), image.height / 2]
        assertEquals("alpha", 1f, sample.alpha, 0.01f)
        assertTrue("fundo sólido do campo (era $sample)", sample.close(HoodieColors.PanelLight))

        rule.onNodeWithTag(PlacePickerTags.NAME).performTextInput("Escritório Centro")
        assertEquals("Escritório Centro", typed)
    }

    @Test
    fun raio_fica_inteiro_fora_do_mapa() {
        show(393.dp, 873.dp, state = newPlace())
        rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.RADIUS))
        assertTrue(bounds(PlacePickerTags.RADIUS).top >= bounds(PlacePickerTags.MAP).bottom - 0.5f)
        rule.onNodeWithTag(PlacePickerTags.MAP).assertIsDisplayed()
    }

    @Test
    fun detalhes_rolam_ate_a_privacidade_em_tela_pequena() {
        show(360.dp, 640.dp, 1.3f)
        rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.PRIVACY))
        rule.onNodeWithTag(PlacePickerTags.PRIVACY).assertIsDisplayed()
        // O mapa não rola junto: continua do mesmo tamanho e visível.
        rule.onNodeWithTag(PlacePickerTags.MAP).assertIsDisplayed()
        rule.onNodeWithTag(PlacePickerTags.SAVE).assertIsDisplayed()
    }

    @Test
    fun edicao_mostra_mudar_local_e_tipo_resumido() {
        show(360.dp, 800.dp)
        rule.onNodeWithText("📍 MUDAR LOCAL").assertIsDisplayed()
        rule.onNodeWithTag(PlacePickerTags.TYPE_SUMMARY).assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(hasTestTag(PlacePickerTags.TYPE_GRID)).fetchSemanticsNodes().size)
        rule.onNodeWithText("🏠 Casa").assertIsDisplayed()
        rule.onNodeWithTag(PlacePickerTags.RADIUS_VALUE).assertIsDisplayed()
        assertEquals("150 m", rule.onNodeWithTag(PlacePickerTags.RADIUS_VALUE).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString())
        // Tipo resumido ocupa uma linha (não três de chips).
        assertTrue(bounds(PlacePickerTags.TYPE_SUMMARY).height < 100f)
    }

    @Test
    fun alterar_tipo_abre_e_selecionar_fecha_o_sheet() {
        var chosen: PlaceType? = null
        rule.setContent {
            var state by remember { mutableStateOf(editing()) }
            Picker(state, PlacePickerActions(onTypeChange = { chosen = it; state = state.copy(type = it) }))
        }
        rule.onNodeWithTag(PlacePickerTags.TYPE_CHANGE).performClick()
        rule.onNodeWithTag(PlacePickerTags.TYPE_SHEET).assertIsDisplayed()
        rule.onNodeWithTag(PlacePickerTags.sheetType(PlaceType.WORK)).performClick()
        rule.waitForIdle()
        assertEquals(PlaceType.WORK, chosen)
        assertEquals(0, rule.onAllNodes(hasTestTag(PlacePickerTags.TYPE_SHEET)).fetchSemanticsNodes().size)
        rule.onNodeWithText("🏢 Trabalho").assertIsDisplayed()
        rule.onNodeWithText("SALVAR COMO TRABALHO").assertIsDisplayed()
    }

    @Test
    fun endereco_longo_fica_em_duas_linhas_sem_empurrar_o_mapa() {
        show(360.dp, 640.dp, state = editing(longAddress))
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(PlacePickerTags.ADDRESS_TEXT).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertTrue("no máximo 2 linhas", layouts.single().lineCount <= 2)
        assertTrue("texto cortado com reticências", layouts.single().isLineEllipsized(layouts.single().lineCount - 1))
        assertLayout(640.dp, 1f)
    }

    @Test
    fun novo_lugar_mostra_todos_os_tipos_sem_sobreposicao_e_fora_do_mapa() {
        show(360.dp, 800.dp, 1.3f, state = PlacePickerState(type = PlaceType.HOME, name = "Casa"))
        rule.onNodeWithText("🏠 NOVO LOCAL").assertIsDisplayed()
        val mapBottom = bounds(PlacePickerTags.MAP).bottom
        rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.TYPE_GRID))
        val cells = PlaceType.physicalPlaceOptions.map { t ->
            rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.typeCell(t)))
            rule.onNodeWithTag(PlacePickerTags.typeCell(t)).assertIsDisplayed()
            val r = bounds(PlacePickerTags.typeCell(t))
            assertTrue("$t com altura de toque", r.height >= 47f)
            assertTrue("$t nunca aparece sobre o mapa", r.top >= mapBottom - 0.5f)
            val scrollPixels = rule.onNodeWithTag(PlacePickerTags.DETAILS).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
            val scrollDp = scrollPixels / rule.activity.resources.displayMetrics.density
            // Compare in content coordinates: each item may require a different
            // scroll offset before it can be measured in the viewport.
            t to Rect(r.left, r.top + scrollDp, r.right, r.bottom + scrollDp)
        }
        cells.forEachIndexed { index, (type, cell) ->
            cells.drop(index + 1).forEach { (otherType, otherCell) ->
                val overlapX = maxOf(cell.left, otherCell.left) < minOf(cell.right, otherCell.right) - 0.5f
                val overlapY = maxOf(cell.top, otherCell.top) < minOf(cell.bottom, otherCell.bottom) - 0.5f
                assertFalse("$type x $otherType", overlapX && overlapY)
            }
        }
        // Salvar desabilitado sem ponto, mas sempre presente.
        rule.onNodeWithTag(PlacePickerTags.SAVE).assertIsDisplayed()
    }

    @Test
    fun onboarding_esconde_o_tipo() {
        show(360.dp, 640.dp, allowTypeChange = false, state = PlacePickerState(type = PlaceType.WORK, name = "Trabalho"))
        assertEquals(0, rule.onAllNodes(hasTestTag(PlacePickerTags.TYPE_SUMMARY)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodes(hasTestTag(PlacePickerTags.TYPE_GRID)).fetchSemanticsNodes().size)
        rule.onNodeWithText("SALVAR COMO TRABALHO").assertIsDisplayed()
    }

    @Test
    fun privacidade_compacta_abre_detalhes() {
        show(411.dp, 891.dp)
        rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.PRIVACY))
        assertTrue("uma linha, não um parágrafo", bounds(PlacePickerTags.PRIVACY).height < 60f)
        rule.onNodeWithTag(PlacePickerTags.PRIVACY_MORE).performClick()
        rule.onNodeWithTag(PlacePickerTags.PRIVACY_SHEET).assertIsDisplayed()
    }

    /**
     * Teclado aberto num aparelho pequeno: sobra ~380 dp (640 − teclado). O mapa encolhe
     * ou some; Salvar, busca e o campo em edição continuam na tela. Não depende do teclado virtual.
     */
    @Test
    fun altura_de_teclado_aberto_mantem_salvar_e_nome() {
        val height = mutableStateOf(380.dp)
        rule.setContent { Box(Modifier.requiredSize(360.dp, height.value)) { Picker(editing()) } }
        for (h in listOf(380.dp, 440.dp, 520.dp)) {
            height.value = h
            rule.waitForIdle()
            val root = bounds(PlacePickerTags.ROOT)
            val save = bounds(PlacePickerTags.SAVE)
            val details = bounds(PlacePickerTags.DETAILS)
            assertTrue("$h salvar dentro da tela", save.bottom <= root.bottom + 0.5f)
            assertTrue("$h detalhes visíveis", details.height >= 48f)
            rule.onNodeWithTag(PlacePickerTags.SAVE).assertIsDisplayed()
            rule.onNodeWithTag(PlacePickerTags.SEARCH).assertIsDisplayed()
            rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.NAME))
            rule.onNodeWithTag(PlacePickerTags.NAME).assertIsDisplayed()
            assertEquals("$h mapa do tamanho calculado", PlacePickerDimensions.forHeight(h).mapHeight.value, bounds(PlacePickerTags.MAP).height, 1f)
        }
    }

    /** Teclado virtual real: o campo em edição e o Salvar ficam acima dele. */
    @Test
    fun teclado_nao_cobre_o_salvar_nem_o_campo() {
        rule.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false)
            rule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        var keyboardBottom = 0
        rule.setContent {
            val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { keyboardBottom = bottom }
            Picker(editing(), modifier = Modifier.imePadding())
        }
        val view = rule.activity.window.decorView
        rule.waitUntil(timeoutMillis = 10_000) { view.hasWindowFocus() }
        rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.NAME))
        rule.onNodeWithTag(PlacePickerTags.NAME)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
        rule.runOnUiThread {
            val inputMethod = rule.activity.getSystemService(InputMethodManager::class.java)
            inputMethod.showSoftInput(rule.activity.currentFocus ?: view, InputMethodManager.SHOW_IMPLICIT)
            WindowCompat.getInsetsController(rule.activity.window, view).show(WindowInsetsCompat.Type.ime())
        }
        // Read the same real window insets used by imePadding. Compose consumes
        // them on its host view, so a later read from DecorView can return zero.
        fun imeBottom() = keyboardBottom
        try {
            rule.waitUntil(timeoutMillis = 15_000) { imeBottom() > 0 }
        } catch (timeout: ComposeTimeoutException) {
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val output = java.io.File(rule.activity.getExternalFilesDir(null), "stabilization-ime-timeout.png")
            output.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            throw AssertionError(
                "IME sem insets positivos: compose=$keyboardBottom, window=${view.rootWindowInsets}, " +
                    "focus=${rule.activity.currentFocus}; captura=$output",
                timeout,
            )
        }
        assertTrue("teclado virtual real deve estar visível", imeBottom() > 0)
        rule.waitForIdle()
        rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.NAME))
        val visibleBottom = view.height - imeBottom()
        val save = rule.onNodeWithTag(PlacePickerTags.SAVE).fetchSemanticsNode().boundsInWindow
        val name = rule.onNodeWithTag(PlacePickerTags.NAME).fetchSemanticsNode().boundsInWindow
        assertTrue("Salvar (${save.bottom}) acima do teclado ($visibleBottom)", save.bottom <= visibleBottom + 1)
        assertTrue("Nome (${name.bottom}) acima do teclado ($visibleBottom)", name.bottom <= visibleBottom + 1)
        rule.onNodeWithTag(PlacePickerTags.NAME).assertIsDisplayed()
    }

    private fun Color.close(other: Color, tol: Float = 0.03f) =
        abs(red - other.red) < tol && abs(green - other.green) < tol && abs(blue - other.blue) < tol
}
