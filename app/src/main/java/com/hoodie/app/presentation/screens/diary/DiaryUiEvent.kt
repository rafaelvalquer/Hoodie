package com.hoodie.app.presentation.screens.diary

import java.time.LocalDate

sealed interface DiaryUiEvent {
    data class ShowPlaceDetails(val date: LocalDate, val nodeId: String) : DiaryUiEvent
    /** Correção agrupou permanências consecutivas em [placeName]: aviso discreto na tela (nunca notificação). */
    data class VisitsGrouped(val placeName: String) : DiaryUiEvent
}
