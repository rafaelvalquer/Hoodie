package com.hoodie.app.presentation.screens.home

import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.domain.home.HomeNowStatus

/**
 * Medidas puras da Home 2.0. A cena (canvas lógico 240×320, escala inteira) ganha o que sobra depois de
 * reservar espaço para cabeçalho, atividade, contexto e ação. Com fonte ampliada os textos pedem mais
 * espaço e a cena cede; a rolagem continua permitida e nada é comprimido para caber numa tela só.
 */
object HomeHeroLayout {
    const val HEADER_DP = 64
    const val ACTIVITY_DP = 76
    const val CONTEXT_DP = 150
    const val ACTION_DP = 56
    /** Abaixo disso a escala inteira de pixel art degrada; prefere-se rolar a cortar sprites. */
    const val MIN_SCENE_DP = 200

    /** Canvas lógico da cena (px). O pixel art só escala por fator inteiro. */
    const val SCENE_W = 240
    const val SCENE_H = 320
    /** Quanto a cena pode passar do espaço calculado para encaixar num fator inteiro maior (a rolagem cobre o resto). */
    const val SCALE_TOLERANCE = 1.15f

    /**
     * Fator inteiro da cena: o maior que cabe na largura e, com [SCALE_TOLERANCE], na altura disponível.
     * Sem a tolerância, 360×640 cairia de 2× para 1× por poucos dp e a cena ficaria minúscula.
     */
    fun sceneScale(widthPx: Int, maxHeightDp: Int, pxPerDp: Float): Int {
        var scale = (widthPx / SCENE_W).coerceAtLeast(1)
        val limitDp = maxHeightDp * SCALE_TOLERANCE
        while (scale > 1 && SCENE_H * scale / pxPerDp > limitDp) scale--
        return scale
    }

    /** Altura exata (dp) da cena nesse fator, com meio pixel de folga contra arredondamento. */
    fun sceneHeightDp(scale: Int, pxPerDp: Float): Float = (SCENE_H * scale + 0.5f) / pxPerDp

    fun sceneMaxHeightDp(screenHeightDp: Int, fontScale: Float): Int {
        val text = ((ACTIVITY_DP + CONTEXT_DP) * fontScale.coerceIn(1f, 2f)).toInt()
        return (screenHeightDp - HEADER_DP - text - ACTION_DP).coerceAtLeast(MIN_SCENE_DP)
    }
}

/** De onde vem a identificação mostrada. Só descreve origens que o app realmente conhece. */
enum class ConfidenceOrigin { PLACE_ARRIVAL, ROUTINE, USER_CONFIRMED, USER_CORRECTED, MOVEMENT, LOCATION_CHECK, UNKNOWN }

object ConfidenceExplainer {
    fun origin(source: ContextSource?, status: HomeNowStatus): ConfidenceOrigin = when {
        status == HomeNowStatus.CONFIRMED && source == ContextSource.USER_CORRECTION -> ConfidenceOrigin.USER_CORRECTED
        status == HomeNowStatus.CONFIRMED -> ConfidenceOrigin.USER_CONFIRMED
        else -> when (source) {
            ContextSource.GEOFENCE -> ConfidenceOrigin.PLACE_ARRIVAL
            ContextSource.ROUTINE -> ConfidenceOrigin.ROUTINE
            ContextSource.MOBILITY -> ConfidenceOrigin.MOVEMENT
            ContextSource.LOCATION_CHECK -> ConfidenceOrigin.LOCATION_CHECK
            ContextSource.MANUAL, ContextSource.USER_CORRECTION -> ConfidenceOrigin.USER_CORRECTED
            ContextSource.CONFIRMATION, ContextSource.ONBOARDING -> ConfidenceOrigin.USER_CONFIRMED
            null -> ConfidenceOrigin.UNKNOWN
        }
    }
}