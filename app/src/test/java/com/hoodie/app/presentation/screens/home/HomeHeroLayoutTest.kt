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

    @Test fun `origem da confianca so descreve o que o app sabe`() {
        assertEquals(ConfidenceOrigin.PLACE_ARRIVAL, ConfidenceExplainer.origin(ContextSource.GEOFENCE, HomeNowStatus.IDENTIFIED))
        assertEquals(ConfidenceOrigin.ROUTINE, ConfidenceExplainer.origin(ContextSource.ROUTINE, HomeNowStatus.PROBABLE))
        assertEquals(ConfidenceOrigin.USER_CONFIRMED, ConfidenceExplainer.origin(ContextSource.CONFIRMATION, HomeNowStatus.CONFIRMED))
        assertEquals(ConfidenceOrigin.USER_CORRECTED, ConfidenceExplainer.origin(ContextSource.USER_CORRECTION, HomeNowStatus.CONFIRMED))
        assertEquals(ConfidenceOrigin.UNKNOWN, ConfidenceExplainer.origin(null, HomeNowStatus.IDENTIFIED))
    }
}