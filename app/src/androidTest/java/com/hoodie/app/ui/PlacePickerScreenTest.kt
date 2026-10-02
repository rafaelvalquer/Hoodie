package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.screens.places.PlacePickerState
import com.hoodie.app.presentation.screens.places.picker.PlacePickerActions
import com.hoodie.app.presentation.screens.places.picker.PlacePickerDimensions
import com.hoodie.app.presentation.screens.places.picker.PlacePickerLayout
import com.hoodie.app.presentation.screens.places.picker.PlacePickerTags
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layout do seletor "Mudar local" em tamanhos e fontes diferentes. O mapa real
 * (osmdroid) é trocado por uma caixa: o que se testa aqui é a distribuição do espaço.
 */
@RunWith(AndroidJUnit4::class)
class PlacePickerScreenTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val longAddress = "1600 Amphitheatre Parkway, Mountain View, California, United States of America, Planet Earth, Milky Way"

    private fun editing(address: String? = "Charleston Rd, Mountain View, CA") = PlacePickerState(
        loadState = com.hoodie.app.presentation.screens.places.PlaceLoadState.Ready,
        type = PlaceType.HOME, editingId = 1L, name = "Casa", hasPoint = true, address = address, radius = 150f,
        latitude = 37.42, longitude = -122.08,
    )

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

    /** Mapa com altura própria, nada sobreposto, detalhes entre mapa e Salvar, Salvar dentro da tela. */
    private fun assertLayout(h: Dp, font: Float) {
        val root = bounds(PlacePickerTags.ROOT)
        val map = bounds(PlacePickerTags.MAP)
        val details = bounds(PlacePickerTags.DETAILS)
        val save = bounds(PlacePickerTags.SAVE)
        val header = bounds(PlacePickerTags.HEADER)
        val tag = "${h}/$font"
        val expectedMap = PlacePickerDimensions.forHeight(h, font).mapHeight.value
        assertEquals("$tag altura do mapa", expectedMap, map.height, 1f)
        assertTrue("$tag mapa nunca espremido", map.height >= PlacePickerDimensions.MAP_MIN_HEIGHT.value - 1)
        assertTrue("$tag header acima do mapa", header.bottom <= map.top + 0.5f)
        assertTrue("$tag detalhes abaixo do mapa", details.top >= map.bottom - 0.5f)
        assertTrue("$tag detalhes têm espaço", details.height >= 48f)
        assertTrue("$tag salvar abaixo dos detalhes", save.top >= details.bottom - 0.5f)
        assertTrue("$tag salvar dentro da tela", save.bottom <= root.bottom + 0.5f)
        assertTrue("$tag salvar com 52 dp", save.height >= 51.5f)
        rule.onNodeWithTag(PlacePickerTags.SAVE).assertIsDisplayed()
        rule.onNodeWithTag(PlacePickerTags.MAP).assertIsDisplayed()
    }

    @Test fun pequeno_360x640() { show(360.dp, 640.dp); assertLayout(640.dp, 1f) }
    @Test fun pequeno_360x640_fonte130() { show(360.dp, 640.dp, 1.3f); assertLayout(640.dp, 1.3f) }
    @Test fun medio_360x800() { show(360.dp, 800.dp); assertLayout(800.dp, 1f) }
    @Test fun medio_360x800_fonte130() { show(360.dp, 800.dp, 1.3f); assertLayout(800.dp, 1.3f) }
    @Test fun grande_411x891() { show(411.dp, 891.dp); assertLayout(891.dp, 1f) }
    @Test fun grande_411x891_fonte130() { show(411.dp, 891.dp, 1.3f); assertLayout(891.dp, 1.3f) }

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
        assertEquals("150 m", rule.onNodeWithTag(PlacePickerTags.RADIUS_VALUE).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString())
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
    fun novo_lugar_mostra_os_nove_tipos_sem_sobreposicao() {
        show(360.dp, 800.dp, 1.3f, state = PlacePickerState(type = PlaceType.HOME, name = "Casa"))
        rule.onNodeWithText("🏠 NOVO LOCAL").assertIsDisplayed()
        rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.TYPE_GRID))
        val cells = PlaceType.entries.map { t ->
            rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.typeCell(t)))
            t to bounds(PlacePickerTags.typeCell(t))
        }
        cells.forEach { (t, r) -> assertTrue("$t com altura de toque", r.height >= 47f) }
        // Mesma linha da grade: células lado a lado, sem interseção.
        cells.chunked(3).forEach { row ->
            row.zipWithNext().forEach { (a, b) -> assertTrue("${a.first} x ${b.first}", a.second.right <= b.second.left + 0.5f) }
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
     * ou some; Salvar e o campo em edição continuam na tela. Não depende do teclado virtual.
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
            rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.NAME))
            rule.onNodeWithTag(PlacePickerTags.NAME).assertIsDisplayed()
            assertEquals("$h mapa do tamanho calculado", PlacePickerDimensions.forHeight(h).mapHeight.value, bounds(PlacePickerTags.MAP).height, 1f)
        }
    }

    @Test
    fun teclado_nao_cobre_o_salvar() {
        rule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) }
        rule.setContent { Picker(editing(), modifier = Modifier.imePadding()) }
        rule.onNodeWithTag(PlacePickerTags.NAME).performClick()
        val view = rule.activity.window.decorView
        fun imeBottom() = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        val deadline = System.currentTimeMillis() + 5_000
        while (imeBottom() == 0 && System.currentTimeMillis() < deadline) { Thread.sleep(100); rule.waitForIdle() }
        assumeTrue("teclado virtual não apareceu (teclado físico?)", imeBottom() > 0)
        rule.waitForIdle()
        val save = rule.onNodeWithTag(PlacePickerTags.SAVE).fetchSemanticsNode().boundsInWindow
        val visibleBottom = view.height - imeBottom()
        assertTrue("Salvar (${save.bottom}) acima do teclado ($visibleBottom)", save.bottom <= visibleBottom + 1)
        rule.onNodeWithTag(PlacePickerTags.MAP).assertIsDisplayed()
    }
}
