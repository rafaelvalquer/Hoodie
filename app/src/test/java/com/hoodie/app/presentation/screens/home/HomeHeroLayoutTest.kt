package com.hoodie.app.presentation.screens.home

import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.domain.home.HomeNowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeroLayoutTest {
    @Test fun `a cena ocupa o que sobra depois de reservar os textos`() {
        // 640 - 64 (cabecalho) - 226 (atividade+contexto) - 56 (acao)
        assertEquals(294, HomeHeroLayout.sceneMaxHeightDp(640, 1f))
        assertEquals(560, HomeHeroLayout.sceneMaxHeightDp(906, 1f)) // 906 - 64 - 226 - 56
    }

    @Test fun `fonte ampliada reserva mais espaco e a cena cede`() {
        val normal = HomeHeroLayout.sceneMaxHeightDp(640, 1f)
        val large = HomeHeroLayout.sceneMaxHeightDp(640, 1.3f)
        assertTrue(large < normal)
        assertEquals(227, large) // 640 - 64 - 293 (226 x 1,3) - 56
    }

    @Test fun `celular pequeno com fonte grande nunca encolhe a cena abaixo do minimo`() {
        assertEquals(HomeHeroLayout.MIN_SCENE_DP, HomeHeroLayout.sceneMaxHeightDp(480, 2f))
    }

    @Test fun `telas de referencia do projeto`() {
        listOf(640, 800, 891).forEach { h ->
            listOf(1f, 1.3f).forEach { f -> assertTrue("$h@$f", HomeHeroLayout.sceneMaxHeightDp(h, f) >= HomeHeroLayout.MIN_SCENE_DP) }
        }
    }

    @Test fun `escala inteira encaixa com tolerancia em vez de cair para 1x`() {
        // 360x640 a 100%: 2x (320 dp) cabe dentro da tolerancia dos 294 dp calculados.
        assertEquals(2, HomeHeroLayout.sceneScale(720, HomeHeroLayout.sceneMaxHeightDp(640, 1f), 2f))
        // 360x640 a 130%: sobra pouco; 1x.
        assertEquals(1, HomeHeroLayout.sceneScale(720, HomeHeroLayout.sceneMaxHeightDp(640, 1.3f), 2f))
        // 360x800 a 100%: 3x.
        assertEquals(3, HomeHeroLayout.sceneScale(720, HomeHeroLayout.sceneMaxHeightDp(800, 1f), 2f))
        // Pixel 8 (1080 px, 2,625 px/dp): 4x.
        assertEquals(4, HomeHeroLayout.sceneScale(1080, HomeHeroLayout.sceneMaxHeightDp(914, 1f), 2.625f))
    }

    @Test fun `escala nunca passa da largura nem fica abaixo de 1x`() {
        assertEquals(1, HomeHeroLayout.sceneScale(200, 600, 2f))
        assertEquals(1, HomeHeroLayout.sceneScale(1080, 10, 2.625f))
        assertTrue(HomeHeroLayout.sceneScale(720, 10_000, 2f) <= 3)
    }

    @Test fun `altura em dp tem meio pixel de folga para o arredondamento`() {
        val dp = HomeHeroLayout.sceneHeightDp(2, 2.625f)
        assertTrue(dp * 2.625f >= 640f)
    }

    @Test fun `origem da confianca so descreve o que o app sabe`() {
        assertEquals(ConfidenceOrigin.PLACE_ARRIVAL, ConfidenceExplainer.origin(ContextSource.GEOFENCE, HomeNowStatus.IDENTIFIED))
        assertEquals(ConfidenceOrigin.ROUTINE, ConfidenceExplainer.origin(ContextSource.ROUTINE, HomeNowStatus.PROBABLE))
        assertEquals(ConfidenceOrigin.USER_CONFIRMED, ConfidenceExplainer.origin(ContextSource.CONFIRMATION, HomeNowStatus.CONFIRMED))
        assertEquals(ConfidenceOrigin.USER_CORRECTED, ConfidenceExplainer.origin(ContextSource.USER_CORRECTION, HomeNowStatus.CONFIRMED))
        assertEquals(ConfidenceOrigin.UNKNOWN, ConfidenceExplainer.origin(null, HomeNowStatus.IDENTIFIED))
    }
}