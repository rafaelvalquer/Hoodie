package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.ContextEvent
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.home.HomeNowAssembler
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.presentation.components.LocalPixelRenderFrame
import com.hoodie.app.presentation.components.PixelRenderFrame
import com.hoodie.app.presentation.screens.home.HomeActions
import com.hoodie.app.presentation.screens.home.HomeContent
import com.hoodie.app.presentation.screens.home.HomeUiState
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/** HOME-01, HOME-02, HOME-03 e HOME-06 da Home 2.0. Rodar só no Pixel_8 (am instrument), nunca no emulator-5556. */
@RunWith(AndroidJUnit4::class)
class HomeHeroUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun visual() = VisualDirector.resolve(HoodieActivity.IDLE, UserContextType.WORK, false, commute = CommuteStyle.WALK, variant = 1)

    private fun snapshot(source: ContextSource = ContextSource.GEOFENCE, confidence: Float = .94f) = HomeNowAssembler.assemble(
        null, ContextEvent(42, UserContextType.WORK, 1_000, null, confidence, 8, source), "Escritório", null, null, 3_000,
    )

    private fun show(homeNow: com.hoodie.app.domain.home.HomeNowSnapshot?, actions: HomeActions = HomeActions(), fontScale: Float = 1f, dialogue: String? = null) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalPixelRenderFrame provides PixelRenderFrame()) {
                HoodieTheme {
                    HomeContent(
                        state = HomeUiState(loading = false, now = 4_000, visual = visual(), dialogue = dialogue),
                        busy = false, zone = ZoneId.of("UTC"), onOpen = {}, actions = actions, homeNow = homeNow,
                    )
                }
            }
        }
    }

    @Test fun contextoValidoMostraCenaAtividadeELocal() {
        show(snapshot())
        rule.onNodeWithTag("home_live_scene").assertIsDisplayed()
        rule.onNodeWithTag("home_user_context").assertIsDisplayed()
        rule.onNodeWithTag("home_quick_confirm").assertIsDisplayed()
    }

    @Test fun semContextoDizQueAindaEstaEntendendoSemInventarLocal() {
        show(null)
        rule.onNodeWithTag("home_user_context").assertIsDisplayed()
        rule.onAllNodesWithTag("home_quick_confirm").assertCountEquals(0)
        rule.onAllNodesWithTag("home_confidence").assertCountEquals(0)
    }

    @Test fun simCorretoConfirmaEmUmToque() {
        var confirmed: Long? = null
        show(snapshot(), HomeActions(confirmContext = { confirmed = it }))
        rule.onNodeWithTag("home_quick_confirm").performClick()
        assertEquals(42L, confirmed)
    }

    @Test fun tocarNaConfiancaMostraDeOndeVeioAIdentificacao() {
        show(snapshot())
        rule.onAllNodesWithTag("home_confidence_explanation").assertCountEquals(0)
        rule.onNodeWithTag("home_confidence").performClick()
        rule.onNodeWithTag("home_confidence_explanation").assertIsDisplayed()
    }

    @Test fun fonteAmpliadaMantemAcoesAcessiveisPelaRolagem() {
        show(snapshot(), fontScale = 1.3f, dialogue = "Já tomei um café.")
        rule.onNodeWithTag("home_live_scene").assertIsDisplayed()
        rule.onNodeWithTag("home_quick_confirm").assertExists()
        assertTrue(rule.onNodeWithTag("home_dialogue").fetchSemanticsNode().layoutInfo.width > 0)
    }
}