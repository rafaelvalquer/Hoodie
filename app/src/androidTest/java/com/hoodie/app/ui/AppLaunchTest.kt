package com.hoodie.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.MainActivity
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test de UI: o app abre e chega a uma tela real (onboarding ou Home)
 * depois do splash. Não toca em nada — não altera os dados do aparelho.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun abreEChegaAUmaTelaPrincipal() {
        // O Hoodie anima sem parar: sem isso o Compose nunca fica "ocioso" para o teste.
        compose.mainClock.autoAdvance = false
        val labels = listOf("Começar", "Hoje", "Histórico", "Ajustes")
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(FRAME_MS)
            val found = labels.any {
                compose.onAllNodesWithText(it, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            if (found) return
            Thread.sleep(FRAME_MS)
        }
        fail("Nenhuma tela principal apareceu em ${TIMEOUT_MS / 1000}s")
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val FRAME_MS = 250L
    }
}
