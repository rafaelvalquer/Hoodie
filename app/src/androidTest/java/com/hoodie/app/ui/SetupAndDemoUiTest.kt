package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.domain.setup.SetupItem
import com.hoodie.app.domain.setup.SetupProgress
import com.hoodie.app.presentation.screens.demo.DemoDayScreen
import com.hoodie.app.presentation.screens.onboarding.SetupProgressCard
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** ONB-02/ONB-06 e o cartão de configuração gradual. Rodar só no Pixel_8 (am instrument), nunca no emulator-5556. */
@RunWith(AndroidJUnit4::class)
class SetupAndDemoUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun progress(home: Boolean = false, bg: Boolean = false, work: Boolean = false, notif: Boolean = false) =
        SetupProgress.of(onboardingDone = true, home = home, work = work, background = bg, notifications = notif, demo = false)

    private fun show(p: SetupProgress, onAction: (SetupItem) -> Unit = {}, onNoWork: () -> Unit = {}, onDemo: () -> Unit = {}, onLater: () -> Unit = {}) {
        rule.setContent { HoodieTheme { SetupProgressCard(p, onAction = onAction, onNoWork = onNoWork, onDemo = onDemo, onLater = onLater) } }
    }

    @Test fun cartaoMostraOProximoPassoEPedeSoUmaCoisaPorVez() {
        var asked: SetupItem? = null
        show(progress(), onAction = { asked = it })
        rule.onNodeWithTag("setup_progress_card").assertIsDisplayed()
        rule.onNodeWithText("Cadastrar minha Casa", ignoreCase = true).assertIsDisplayed()
        rule.onAllNodesWithText("Não trabalho", ignoreCase = true).assertCountEquals(0) // só aparece quando o trabalho é o próximo
        rule.onNodeWithTag("setup_next").performClick()
        assertEquals(SetupItem.HOME, asked)
    }

    @Test fun trabalhoPendenteOferecePoderDizerQueNaoTrabalha() {
        var noWork = false
        show(progress(home = true, bg = true), onNoWork = { noWork = true })
        rule.onNodeWithText("Configurar trabalho", ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText("Não trabalho", ignoreCase = true).performClick()
        assertTrue(noWork)
    }

    @Test fun demonstracaoEstaSempreDisponivelSemPermissoes() {
        var demo = false
        show(progress(), onDemo = { demo = true })
        rule.onNodeWithTag("setup_demo").performClick()
        assertTrue(demo)
    }

    @Test fun tudoConfiguradoNaoMostraCartao() {
        show(progress(home = true, bg = true, work = true, notif = true))
        rule.onAllNodesWithTag("setup_progress_card").assertCountEquals(0)
    }

    @Test fun diaDeDemonstracaoMostraAvisoFixoEPermiteSair() {
        var exited = false
        rule.setContent { HoodieTheme { DemoDayScreen(onExit = { exited = true }) } }
        rule.onNodeWithTag("demo_banner").assertIsDisplayed()
        rule.onNodeWithText("Dados fictícios · Dia de Demonstração").assertIsDisplayed()
        rule.onNodeWithTag("demo_steps").assertIsDisplayed()
        rule.onNodeWithText("07:00 — Hoodie acorda em Casa").assertIsDisplayed()
        rule.onNodeWithTag("demo_exit").performClick()
        assertTrue(exited)
    }
}